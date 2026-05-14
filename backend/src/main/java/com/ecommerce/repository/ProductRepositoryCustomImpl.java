package com.ecommerce.repository;

import com.ecommerce.model.Product;
import lombok.RequiredArgsConstructor;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ProductRepositoryCustomImpl implements ProductRepositoryCustom {

    private final R2dbcEntityTemplate template;
    private final DatabaseClient db;

    @Override
    public Flux<Product> searchByName(String search, int limit, long offset) {
        Criteria criteria = Criteria.where("is_active").is(true)
                .and("name").like("%" + search + "%").ignoreCase(true);

        Query query = Query.query(criteria)
                .limit(limit)
                .offset(offset);

        return template.select(Product.class)
                .matching(query)
                .all();
    }

    @Override
    public Mono<Long> countBySearch(String search) {
        Criteria criteria = Criteria.where("is_active").is(true)
                .and("name").like("%" + search + "%").ignoreCase(true);

        return template.select(Product.class)
                .matching(Query.query(criteria))
                .count();
    }

    @Override
    public Flux<Product> findByPriceRange(BigDecimal minPrice, BigDecimal maxPrice, int limit, long offset) {
        Criteria criteria = Criteria.where("is_active").is(true)
                .and("price").greaterThanOrEquals(minPrice)
                .and("price").lessThanOrEquals(maxPrice);

        Query query = Query.query(criteria)
                .limit(limit)
                .offset(offset);

        return template.select(Product.class)
                .matching(query)
                .all();
    }

    // ---------- Region-aware queries ----------

    /**
     * Visibility predicate (used by every region-filtered query):
     *   no rows in product_regions for this product (open/global)
     *   OR a row exists for this product + the customer's region.
     */
    private static final String REGION_VISIBLE_CLAUSE =
            "(NOT EXISTS (SELECT 1 FROM product_regions WHERE product_id = p.id) "
          + " OR EXISTS (SELECT 1 FROM product_regions WHERE product_id = p.id AND region_id = :regionId))";

    private static String orderByClause(String orderBy) {
        // Whitelist allowed sort expressions to keep this safe to interpolate.
        return switch (orderBy == null ? "" : orderBy) {
            case "price_asc"  -> "ORDER BY p.price ASC";
            case "price_desc" -> "ORDER BY p.price DESC";
            case "name_asc"   -> "ORDER BY p.name ASC";
            case "name_desc"  -> "ORDER BY p.name DESC";
            default           -> "ORDER BY p.created_at DESC";
        };
    }

    @Override
    public Flux<Product> findVisibleInRegion(UUID regionId, UUID categoryId,
                                              int limit, long offset, String orderBy) {
        StringBuilder sql = new StringBuilder("SELECT p.* FROM products p WHERE p.is_active = true AND ")
                .append(REGION_VISIBLE_CLAUSE);
        if (categoryId != null) sql.append(" AND p.category_id = :categoryId");
        sql.append(" ").append(orderByClause(orderBy)).append(" LIMIT :limit OFFSET :offset");

        var spec = db.sql(sql.toString())
                .bind("regionId", regionId)
                .bind("limit", limit)
                .bind("offset", offset);
        if (categoryId != null) spec = spec.bind("categoryId", categoryId);
        return spec.map((row, meta) -> mapProduct(row)).all();
    }

    @Override
    public Mono<Long> countVisibleInRegion(UUID regionId, UUID categoryId) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) AS c FROM products p WHERE p.is_active = true AND ")
                .append(REGION_VISIBLE_CLAUSE);
        if (categoryId != null) sql.append(" AND p.category_id = :categoryId");

        var spec = db.sql(sql.toString()).bind("regionId", regionId);
        if (categoryId != null) spec = spec.bind("categoryId", categoryId);
        return spec.map(row -> ((Number) row.get("c")).longValue()).one().defaultIfEmpty(0L);
    }

    @Override
    public Flux<Product> searchVisibleInRegion(String search, UUID regionId, int limit, long offset) {
        String sql = "SELECT p.* FROM products p WHERE p.is_active = true"
                + " AND LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%'))"
                + " AND " + REGION_VISIBLE_CLAUSE
                + " ORDER BY p.created_at DESC LIMIT :limit OFFSET :offset";
        return db.sql(sql)
                .bind("search", search)
                .bind("regionId", regionId)
                .bind("limit", limit)
                .bind("offset", offset)
                .map((row, meta) -> mapProduct(row))
                .all();
    }

    @Override
    public Mono<Long> countSearchVisibleInRegion(String search, UUID regionId) {
        String sql = "SELECT COUNT(*) AS c FROM products p WHERE p.is_active = true"
                + " AND LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%'))"
                + " AND " + REGION_VISIBLE_CLAUSE;
        return db.sql(sql)
                .bind("search", search)
                .bind("regionId", regionId)
                .map(row -> ((Number) row.get("c")).longValue())
                .one()
                .defaultIfEmpty(0L);
    }

    @Override
    public Mono<Boolean> isProductVisibleInRegion(UUID productId, UUID regionId) {
        String sql = "SELECT EXISTS ("
                + "  SELECT 1 FROM products p WHERE p.id = :productId"
                + "    AND p.is_active = true AND " + REGION_VISIBLE_CLAUSE
                + ") AS visible";
        return db.sql(sql)
                .bind("productId", productId)
                .bind("regionId", regionId)
                .map(row -> (Boolean) row.get("visible"))
                .one()
                .defaultIfEmpty(false);
    }

    @Override
    public Mono<Void> replaceProductRegions(UUID productId, List<UUID> regionIds) {
        Mono<Long> deleted = db.sql("DELETE FROM product_regions WHERE product_id = :productId")
                .bind("productId", productId)
                .fetch().rowsUpdated();
        if (regionIds == null || regionIds.isEmpty()) {
            return deleted.then();
        }
        return deleted.thenMany(Flux.fromIterable(regionIds)
                .concatMap(rid -> db.sql("INSERT INTO product_regions (product_id, region_id) VALUES (:p, :r)")
                        .bind("p", productId)
                        .bind("r", rid)
                        .fetch().rowsUpdated()))
                .then();
    }

    @Override
    public Flux<UUID> findRegionIdsForProduct(UUID productId) {
        return db.sql("SELECT region_id FROM product_regions WHERE product_id = :productId")
                .bind("productId", productId)
                .map(row -> (UUID) row.get("region_id"))
                .all();
    }

    /** Maps a products row to the Product entity. Mirrors the @Column names. */
    private static Product mapProduct(io.r2dbc.spi.Row row) {
        Product p = Product.builder()
                .name(row.get("name", String.class))
                .description(row.get("description", String.class))
                .price(row.get("price", BigDecimal.class))
                .imageUrl(row.get("image_url", String.class))
                .images(row.get("images", String.class))
                .categoryId(row.get("category_id", UUID.class))
                .attributes(row.get("attributes", String.class))
                .stock(row.get("stock", Integer.class))
                .sku(row.get("sku", String.class))
                .isActive(row.get("is_active", Boolean.class))
                .vendorId(row.get("vendor_id", UUID.class))
                .build();
        p.setId(row.get("id", UUID.class));
        p.setCreatedAt(row.get("created_at", java.time.LocalDateTime.class));
        p.setUpdatedAt(row.get("updated_at", java.time.LocalDateTime.class));
        return p;
    }
}
