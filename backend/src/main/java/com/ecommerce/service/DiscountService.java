package com.ecommerce.service;

import com.ecommerce.dto.DiscountDto;
import com.ecommerce.exception.BadRequestException;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.mapper.DiscountMapper;
import com.ecommerce.model.Discount;
import com.ecommerce.model.Product;
import com.ecommerce.repository.DiscountRepository;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Resolves discounts to effective product pricing and exposes admin CRUD.
 *
 * Resolution rule when multiple active discounts apply to the same product:
 * the MOST SPECIFIC scope wins - PRODUCT beats CATEGORY beats SITEWIDE,
 * regardless of value. Within the SITEWIDE tier (where multiple discounts
 * can coexist), the largest absolute saving wins as a tiebreak. Per-product
 * and per-category discounts are constrained to 1 by the
 * uq_discount_scope_target unique index, so no tiebreak is needed there.
 * No stacking across tiers - the winning discount is the sole price modifier.
 *
 * The discounts table is intentionally small (campaigns, not per-row), so
 * the active set is loaded once per product-list request and matched in
 * memory rather than via a per-product subquery.
 */
@Service
@RequiredArgsConstructor
public class DiscountService {

    private static final String NOT_FOUND = "Discount not found";

    private final DiscountRepository discountRepository;
    private final DiscountMapper discountMapper;

    public Flux<Discount> findActive() {
        return discountRepository.findActive();
    }

    public Flux<DiscountDto> listAll() {
        return discountRepository.findAllByOrderByCreatedAtDesc().map(discountMapper::toDto);
    }

    /**
     * List discounts whose scope target matches the given product/category id.
     * Used by the inline "Discounts for this product/category" panel in admin
     * edit pages. Sitewide discounts are not returned (they have no target).
     */
    public Flux<DiscountDto> listForScopeTarget(Discount.DiscountScope scope, UUID scopeTargetId) {
        return discountRepository
                .findByScopeAndScopeTargetIdOrderByCreatedAtDesc(scope, scopeTargetId)
                .map(discountMapper::toDto);
    }

    /**
     * Every ACTIVE discount that could affect the given product right now:
     * the direct PRODUCT-scope discount, the CATEGORY-scope discount on its
     * category (if any), and all SITEWIDE discounts. Used by the inline
     * panel so admins see exactly the discounts the storefront resolves
     * against, not just the ones directly targeting this product.
     */
    public Flux<DiscountDto> findApplicableToProduct(Product product) {
        return findActive()
                .filter(d -> appliesTo(d, product))
                .sort((a, b) -> Integer.compare(scopeOrder(a.getScope()), scopeOrder(b.getScope())))
                .map(discountMapper::toDto);
    }

    /**
     * Every ACTIVE discount that could affect products in this category:
     * the CATEGORY-scope discount on this category (if any), plus SITEWIDE.
     */
    public Flux<DiscountDto> findApplicableToCategory(UUID categoryId) {
        return findActive()
                .filter(d -> d.getScope() == Discount.DiscountScope.SITEWIDE
                        || (d.getScope() == Discount.DiscountScope.CATEGORY
                                && categoryId.equals(d.getScopeTargetId())))
                .sort((a, b) -> Integer.compare(scopeOrder(a.getScope()), scopeOrder(b.getScope())))
                .map(discountMapper::toDto);
    }

    private static int scopeOrder(Discount.DiscountScope s) {
        return switch (s) {
            case PRODUCT  -> 0;
            case CATEGORY -> 1;
            case SITEWIDE -> 2;
        };
    }

    public Mono<DiscountDto> findById(UUID id) {
        return discountRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(NOT_FOUND)))
                .map(discountMapper::toDto);
    }

    public Mono<DiscountDto> create(DiscountDto dto) {
        validateScopeTarget(dto);
        return ensureNoConflictingTarget(dto.getScope(), dto.getScopeTargetId(), null)
                .then(Mono.defer(() -> {
                    Discount d = discountMapper.toEntity(dto);
                    d.setId(UUID.randomUUID());
                    if (d.getIsActive() == null) d.setIsActive(true);
                    return discountRepository.save(d).map(discountMapper::toDto);
                }));
    }

    public Mono<DiscountDto> update(UUID id, DiscountDto dto) {
        validateScopeTarget(dto);
        return discountRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(NOT_FOUND)))
                .flatMap(existing -> ensureNoConflictingTarget(dto.getScope(), dto.getScopeTargetId(), id)
                        .then(Mono.defer(() -> {
                            existing.setName(dto.getName());
                            existing.setType(dto.getType());
                            existing.setValue(dto.getValue());
                            existing.setScope(dto.getScope());
                            existing.setScopeTargetId(dto.getScopeTargetId());
                            existing.setStartsAt(dto.getStartsAt());
                            existing.setEndsAt(dto.getEndsAt());
                            if (dto.getIsActive() != null) existing.setIsActive(dto.getIsActive());
                            return discountRepository.save(existing);
                        })))
                .map(discountMapper::toDto);
    }

    /**
     * A product or category can have at most one discount. Sitewide is exempt
     * (multiple sitewide campaigns can coexist). When updating, the row being
     * updated is excluded from the conflict check via {@code excludingId}.
     */
    private Mono<Void> ensureNoConflictingTarget(Discount.DiscountScope scope, UUID targetId, UUID excludingId) {
        if (scope == Discount.DiscountScope.SITEWIDE) {
            return Mono.empty();
        }
        return discountRepository
                .findByScopeAndScopeTargetIdOrderByCreatedAtDesc(scope, targetId)
                .filter(existing -> excludingId == null || !existing.getId().equals(excludingId))
                .next()
                .flatMap(existing -> Mono.<Void>error(new BadRequestException(
                        "This " + scope.name().toLowerCase()
                                + " already has a discount (\"" + existing.getName()
                                + "\"). Edit or delete it before adding a new one.")))
                .then();
    }

    public Mono<Void> delete(UUID id) {
        return discountRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(NOT_FOUND)))
                .flatMap(discountRepository::delete);
    }

    /**
     * Pick the discount that applies to this product. Resolution rule:
     * <strong>most-specific scope wins</strong> - PRODUCT beats CATEGORY beats
     * SITEWIDE. Within the SITEWIDE tier (the only tier where multiple
     * entries can coexist; PRODUCT/CATEGORY are constrained to 1 by the
     * uq_discount_scope_target unique index) the largest absolute saving
     * wins as a tiebreak.
     * <p>
     * Rationale: an admin who sets a discount directly on a product
     * expects it to apply, regardless of a broader campaign's value. Most
     * e-commerce platforms work this way.
     */
    public Optional<DiscountResult> bestFor(Product product, List<Discount> activeDiscounts) {
        Discount productScope = null;
        Discount categoryScope = null;
        Discount bestSitewide = null;
        BigDecimal bestSitewideAmount = BigDecimal.ZERO;

        for (Discount d : activeDiscounts) {
            if (!appliesTo(d, product)) continue;
            BigDecimal amount = computeAmount(d, product.getPrice());
            if (amount.compareTo(BigDecimal.ZERO) <= 0) continue;

            switch (d.getScope()) {
                case PRODUCT  -> productScope = d;
                case CATEGORY -> categoryScope = d;
                case SITEWIDE -> {
                    if (amount.compareTo(bestSitewideAmount) > 0) {
                        bestSitewide = d;
                        bestSitewideAmount = amount;
                    }
                }
            }
        }

        Discount winner = productScope != null ? productScope
                : categoryScope != null ? categoryScope
                : bestSitewide;
        if (winner == null) return Optional.empty();
        return Optional.of(buildResult(winner, product.getPrice()));
    }

    private DiscountResult buildResult(Discount d, BigDecimal price) {
        BigDecimal amount = computeAmount(d, price);
        BigDecimal effective = price.subtract(amount).max(BigDecimal.ZERO);
        BigDecimal pct = price.compareTo(BigDecimal.ZERO) > 0
                ? amount.multiply(BigDecimal.valueOf(100)).divide(price, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        return new DiscountResult(d.getId(), d.getName(), amount, effective, pct, d.getEndsAt());
    }

    private boolean appliesTo(Discount d, Product p) {
        return switch (d.getScope()) {
            case SITEWIDE -> true;
            case PRODUCT  -> p.getId().equals(d.getScopeTargetId());
            case CATEGORY -> p.getCategoryId().equals(d.getScopeTargetId());
        };
    }

    private BigDecimal computeAmount(Discount d, BigDecimal price) {
        return switch (d.getType()) {
            case PERCENTAGE -> price.multiply(d.getValue())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            case FIXED -> d.getValue().min(price);  // never discount below 0
        };
    }

    private void validateScopeTarget(DiscountDto dto) {
        boolean siteWide = dto.getScope() == Discount.DiscountScope.SITEWIDE;
        if (siteWide && dto.getScopeTargetId() != null) {
            throw new BadRequestException("Sitewide discounts must not have a scopeTargetId");
        }
        if (!siteWide && dto.getScopeTargetId() == null) {
            throw new BadRequestException("Product/category discounts require scopeTargetId");
        }
        if (dto.getType() == Discount.DiscountType.PERCENTAGE
                && dto.getValue() != null
                && dto.getValue().compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new BadRequestException("Percentage discount must be at most 100");
        }
        if (dto.getStartsAt() != null && dto.getEndsAt() != null
                && !dto.getEndsAt().isAfter(dto.getStartsAt())) {
            throw new BadRequestException("End time must be after start time");
        }
    }

    @Value
    public static class DiscountResult {
        UUID id;
        String name;
        BigDecimal amount;
        BigDecimal discountedPrice;
        BigDecimal percent;
        LocalDateTime endsAt;
    }
}
