package com.ecommerce.repository;

import com.ecommerce.model.Product;
import lombok.RequiredArgsConstructor;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

@Repository
@RequiredArgsConstructor
public class ProductRepositoryCustomImpl implements ProductRepositoryCustom {

    private final R2dbcEntityTemplate template;

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
}
