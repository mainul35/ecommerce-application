package com.ecommerce.service;

import com.ecommerce.dto.CouponDto;
import com.ecommerce.dto.CouponValidationRequest;
import com.ecommerce.dto.CouponValidationResponse;
import com.ecommerce.dto.OrderItemRequest;
import com.ecommerce.exception.BadRequestException;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.mapper.CouponMapper;
import com.ecommerce.model.Coupon;
import com.ecommerce.model.Discount;
import com.ecommerce.model.Product;
import com.ecommerce.repository.CouponRepository;
import com.ecommerce.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Coupon validation, application, and admin CRUD.
 *
 * Validation runs the SAME checks at preview time (cart UI) and at order
 * create time. The cart preview is informational; the order-create call
 * re-validates server-side and atomically records the redemption (so two
 * customers racing for the last use of a one-shot coupon cannot both win).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CouponService {

    private static final String NOT_FOUND = "Coupon not found";

    private final CouponRepository couponRepository;
    private final ProductRepository productRepository;
    private final DiscountService discountService;
    private final CouponMapper couponMapper;
    private final DatabaseClient db;

    public Flux<CouponDto> listAll() {
        return couponRepository.findAllByOrderByCreatedAtDesc().map(couponMapper::toDto);
    }

    public Mono<CouponDto> findById(UUID id) {
        return couponRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(NOT_FOUND)))
                .map(couponMapper::toDto);
    }

    public Mono<CouponDto> create(CouponDto dto) {
        validateDto(dto);
        Coupon entity = couponMapper.toEntity(dto);
        entity.setId(UUID.randomUUID());
        entity.setCode(canonical(dto.getCode()));
        if (entity.getIsActive() == null) entity.setIsActive(true);
        return couponRepository.save(entity).map(couponMapper::toDto);
    }

    public Mono<CouponDto> update(UUID id, CouponDto dto) {
        validateDto(dto);
        return couponRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(NOT_FOUND)))
                .flatMap(existing -> {
                    existing.setCode(canonical(dto.getCode()));
                    existing.setName(dto.getName());
                    existing.setType(dto.getType());
                    existing.setValue(dto.getValue());
                    existing.setMinOrderAmount(dto.getMinOrderAmount());
                    existing.setMaxUses(dto.getMaxUses());
                    existing.setMaxUsesPerUser(dto.getMaxUsesPerUser());
                    existing.setValidFrom(dto.getValidFrom());
                    existing.setValidUntil(dto.getValidUntil());
                    if (dto.getIsActive() != null) existing.setIsActive(dto.getIsActive());
                    return couponRepository.save(existing);
                })
                .map(couponMapper::toDto);
    }

    public Mono<Void> delete(UUID id) {
        return couponRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(NOT_FOUND)))
                .flatMap(couponRepository::delete);
    }

    /**
     * Customer-side preview: "if I apply this code, what happens?". Does not
     * record a redemption. The same checks run again at order create time.
     */
    public Mono<CouponValidationResponse> previewForCustomer(UUID userId, CouponValidationRequest request) {
        return computeSubtotal(request.getItems())
                .flatMap(subtotal -> validateAndCompute(request.getCode(), userId, subtotal)
                        .map(result -> CouponValidationResponse.builder()
                                .valid(true)
                                .code(result.getCanonicalCode())
                                .type(result.getType())
                                .subtotal(subtotal)
                                .discountAmount(result.getDiscountAmount())
                                .finalAmount(subtotal.subtract(result.getDiscountAmount()))
                                .build())
                        .onErrorResume(BadRequestException.class, ex -> Mono.just(
                                CouponValidationResponse.builder()
                                        .valid(false)
                                        .message(ex.getMessage())
                                        .subtotal(subtotal)
                                        .discountAmount(BigDecimal.ZERO)
                                        .finalAmount(subtotal)
                                        .build())));
    }

    /**
     * Order-create-time application. Validates against the SAME rules as the
     * preview, then atomically inserts a coupon_uses row only when limits
     * still allow it (race-safe).
     *
     * Returns the discount amount actually applied. Throws BadRequestException
     * if the code is no longer redeemable.
     */
    public Mono<AppliedCoupon> applyToOrder(UUID userId, UUID orderId, String code, BigDecimal subtotal) {
        return validateAndCompute(code, userId, subtotal)
                .flatMap(result -> recordUseAtomically(result.getCouponId(), userId, orderId,
                                result.getMaxUses(), result.getMaxUsesPerUser())
                        .thenReturn(new AppliedCoupon(
                                result.getCanonicalCode(),
                                result.getDiscountAmount(),
                                result.getType())));
    }

    // ---------- helpers ----------

    /**
     * Loads the coupon, runs all rule checks, and returns the would-be discount.
     * Throws BadRequestException with a customer-facing message on any failure.
     */
    private Mono<ValidatedCoupon> validateAndCompute(String code, UUID userId, BigDecimal subtotal) {
        if (code == null || code.isBlank()) {
            return Mono.error(new BadRequestException("Coupon code is required"));
        }
        return couponRepository.findByCodeIgnoreCase(code.trim())
                .switchIfEmpty(Mono.error(new BadRequestException("Coupon not found")))
                .flatMap(coupon -> {
                    LocalDateTime now = LocalDateTime.now();
                    if (Boolean.FALSE.equals(coupon.getIsActive())) {
                        return Mono.error(new BadRequestException("Coupon is not active"));
                    }
                    if (coupon.getValidFrom() != null && now.isBefore(coupon.getValidFrom())) {
                        return Mono.error(new BadRequestException("Coupon is not yet valid"));
                    }
                    if (coupon.getValidUntil() != null && !now.isBefore(coupon.getValidUntil())) {
                        return Mono.error(new BadRequestException("Coupon has expired"));
                    }
                    if (coupon.getMinOrderAmount() != null
                            && subtotal.compareTo(coupon.getMinOrderAmount()) < 0) {
                        return Mono.error(new BadRequestException(
                                "Minimum order of $" + coupon.getMinOrderAmount() + " required"));
                    }
                    BigDecimal discountAmount = computeDiscount(coupon, subtotal);
                    return checkUsageLimits(coupon, userId)
                            .thenReturn(new ValidatedCoupon(
                                    coupon.getId(),
                                    coupon.getCode(),
                                    coupon.getType(),
                                    coupon.getMaxUses(),
                                    coupon.getMaxUsesPerUser(),
                                    discountAmount));
                });
    }

    private Mono<Void> checkUsageLimits(Coupon coupon, UUID userId) {
        // Read-side check used in the preview path. The order-create path also
        // does an atomic conditional INSERT in {@link #recordUseAtomically} so
        // the limits cannot be exceeded under concurrency.
        return Mono.zip(
                        countUses(coupon.getId(), null),
                        countUses(coupon.getId(), userId))
                .flatMap(t -> {
                    long total = t.getT1();
                    long perUser = t.getT2();
                    if (coupon.getMaxUses() != null && total >= coupon.getMaxUses()) {
                        return Mono.error(new BadRequestException("Coupon has been fully redeemed"));
                    }
                    if (coupon.getMaxUsesPerUser() != null && perUser >= coupon.getMaxUsesPerUser()) {
                        return Mono.error(new BadRequestException("You have already used this coupon"));
                    }
                    return Mono.empty();
                })
                .then();
    }

    private Mono<Long> countUses(UUID couponId, UUID userId) {
        String sql = userId == null
                ? "SELECT COUNT(*) AS c FROM coupon_uses WHERE coupon_id = :couponId"
                : "SELECT COUNT(*) AS c FROM coupon_uses WHERE coupon_id = :couponId AND user_id = :userId";
        var spec = db.sql(sql).bind("couponId", couponId);
        if (userId != null) spec = spec.bind("userId", userId);
        return spec.map(row -> ((Number) row.get("c")).longValue()).one().defaultIfEmpty(0L);
    }

    /**
     * Single-statement INSERT that only emits a row when both limits still
     * allow the redemption. If RETURNING comes back empty, the redemption
     * lost a race - we throw BadRequestException so the order create rolls back.
     */
    private Mono<UUID> recordUseAtomically(UUID couponId, UUID userId, UUID orderId,
                                            Integer maxUses, Integer maxUsesPerUser) {
        UUID id = UUID.randomUUID();
        // Sentinel: -1 means "unlimited" for either cap (so the predicate is trivially true).
        int maxUsesSentinel = maxUses == null ? -1 : maxUses;
        int maxUsesPerUserSentinel = maxUsesPerUser == null ? -1 : maxUsesPerUser;
        String sql = """
                INSERT INTO coupon_uses (id, coupon_id, user_id, order_id, created_at, updated_at)
                SELECT :id, :couponId, :userId, :orderId, NOW(), NOW()
                WHERE (
                    :maxUses < 0
                    OR (SELECT COUNT(*) FROM coupon_uses WHERE coupon_id = :couponId) < :maxUses
                ) AND (
                    :maxUsesPerUser < 0
                    OR (SELECT COUNT(*) FROM coupon_uses
                        WHERE coupon_id = :couponId AND user_id = :userId) < :maxUsesPerUser
                )
                RETURNING id
                """;
        return db.sql(sql)
                .bind("id", id)
                .bind("couponId", couponId)
                .bind("userId", userId)
                .bind("orderId", orderId)
                .bind("maxUses", maxUsesSentinel)
                .bind("maxUsesPerUser", maxUsesPerUserSentinel)
                .map(row -> (UUID) row.get("id"))
                .one()
                .switchIfEmpty(Mono.error(new BadRequestException(
                        "Coupon usage limit reached - cannot redeem")));
    }

    private BigDecimal computeDiscount(Coupon coupon, BigDecimal subtotal) {
        return switch (coupon.getType()) {
            case PERCENTAGE -> subtotal.multiply(coupon.getValue())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            case FIXED -> coupon.getValue().min(subtotal);
            // Free-shipping has no impact on subtotal/discount; the order's
            // shipping calculation (when modelled) would zero out separately.
            case FREE_SHIPPING -> BigDecimal.ZERO;
        };
    }

    /**
     * Compute the cart subtotal the same way the order-create flow will,
     * applying any active item-level discounts.
     */
    private Mono<BigDecimal> computeSubtotal(List<OrderItemRequest> items) {
        return Mono.zip(
                Flux.fromIterable(items)
                        .flatMap(it -> productRepository.findById(it.getProductId())
                                .switchIfEmpty(Mono.error(new BadRequestException(
                                        "Product not found: " + it.getProductId()))))
                        .collectMap(Product::getId, p -> p),
                discountService.findActive().collectList()
        ).map(t -> {
            Map<UUID, Product> productsById = t.getT1();
            List<Discount> active = t.getT2();
            BigDecimal sum = BigDecimal.ZERO;
            for (OrderItemRequest item : items) {
                Product product = productsById.get(item.getProductId());
                BigDecimal unit = discountService.bestFor(product, active)
                        .map(DiscountService.DiscountResult::getDiscountedPrice)
                        .orElse(product.getPrice());
                sum = sum.add(unit.multiply(BigDecimal.valueOf(item.getQuantity())));
            }
            return sum.setScale(2, RoundingMode.HALF_UP);
        });
    }

    private void validateDto(CouponDto dto) {
        if (dto.getCode() == null || dto.getCode().isBlank()) {
            throw new BadRequestException("Code is required");
        }
        Coupon.CouponType type = dto.getType();
        BigDecimal value = dto.getValue();
        if (type == Coupon.CouponType.FREE_SHIPPING) {
            if (value != null) {
                throw new BadRequestException("FREE_SHIPPING coupons must not have a value");
            }
        } else {
            if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BadRequestException("Value must be positive for " + type);
            }
            if (type == Coupon.CouponType.PERCENTAGE
                    && value.compareTo(BigDecimal.valueOf(100)) > 0) {
                throw new BadRequestException("Percentage coupon must be at most 100");
            }
        }
        if (dto.getValidFrom() != null && dto.getValidUntil() != null
                && !dto.getValidUntil().isAfter(dto.getValidFrom())) {
            throw new BadRequestException("Valid-until must be after valid-from");
        }
    }

    private static String canonical(String code) {
        return code == null ? null : code.trim().toUpperCase();
    }

    @Value
    private static class ValidatedCoupon {
        UUID couponId;
        String canonicalCode;
        Coupon.CouponType type;
        Integer maxUses;
        Integer maxUsesPerUser;
        BigDecimal discountAmount;
    }

    @Value
    public static class AppliedCoupon {
        String code;
        BigDecimal discountAmount;
        Coupon.CouponType type;
    }
}
