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
 * the one yielding the LARGEST absolute saving wins. No stacking - keeps
 * reasoning simple and prevents overlapping campaigns from compounding.
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
     * Pick the discount that maximises savings on this product. Returns
     * empty when nothing applies.
     */
    public Optional<DiscountResult> bestFor(Product product, List<Discount> activeDiscounts) {
        DiscountResult best = null;
        for (Discount d : activeDiscounts) {
            if (!appliesTo(d, product)) continue;
            BigDecimal amount = computeAmount(d, product.getPrice());
            if (amount.compareTo(BigDecimal.ZERO) <= 0) continue;
            if (best == null || amount.compareTo(best.getAmount()) > 0) {
                BigDecimal effective = product.getPrice().subtract(amount).max(BigDecimal.ZERO);
                BigDecimal pct = product.getPrice().compareTo(BigDecimal.ZERO) > 0
                        ? amount.multiply(BigDecimal.valueOf(100))
                                .divide(product.getPrice(), 2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;
                best = new DiscountResult(d.getId(), d.getName(), amount, effective, pct, d.getEndsAt());
            }
        }
        return Optional.ofNullable(best);
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
