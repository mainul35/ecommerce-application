package com.ecommerce.service;

import com.ecommerce.model.Discount;
import com.ecommerce.model.Order;
import com.ecommerce.model.OrderItem;
import com.ecommerce.model.Product;
import com.ecommerce.dto.*;
import com.ecommerce.exception.BadRequestException;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.mapper.OrderMapper;
import com.ecommerce.repository.OrderItemRepository;
import com.ecommerce.repository.OrderRepository;
import com.ecommerce.repository.ProductRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;
    private final OrderMapper orderMapper;
    private final ObjectMapper objectMapper;
    private final StockReservationService stockReservationService;
    private final DiscountService discountService;
    private final CouponService couponService;
    private final EscrowService escrowService;

    @Value("${reservation.ttl-hours:168}")
    private long reservationTtlHours;

    private static final String ORDER_NOT_FOUND = "Order not found";

    public Mono<PagedResponse<OrderDto>> findByUserId(UUID userId, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        return orderRepository.findByUserId(userId, pageRequest)
                .flatMap(this::enrichWithItems)
                .collectList()
                .zipWith(orderRepository.countByUserId(userId))
                .map(tuple -> PagedResponse.of(tuple.getT1(), page, size, tuple.getT2()));
    }

    public Mono<OrderDto> findById(UUID id, UUID userId) {
        return orderRepository.findByIdAndUserId(id, userId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ORDER_NOT_FOUND)))
                .flatMap(this::enrichWithItems);
    }

    /** Admin: list all orders, optionally filtered by status. */
    public Mono<PagedResponse<OrderDto>> findAllForAdmin(Order.OrderStatus status, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        if (status != null) {
            return orderRepository.findByStatus(status, pageRequest)
                    .flatMap(this::enrichWithItems)
                    .collectList()
                    .zipWith(orderRepository.countByStatus(status))
                    .map(tuple -> PagedResponse.of(tuple.getT1(), page, size, tuple.getT2()));
        }
        return orderRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"))
                .skip((long) page * size)
                .take(size)
                .flatMap(this::enrichWithItems)
                .collectList()
                .zipWith(orderRepository.count())
                .map(tuple -> PagedResponse.of(tuple.getT1(), page, size, tuple.getT2()));
    }

    /** Admin: load any order regardless of buyer. */
    public Mono<OrderDto> findByIdForAdmin(UUID id) {
        return orderRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ORDER_NOT_FOUND)))
                .flatMap(this::enrichWithItems);
    }

    /**
     * Admin: move an order to {@code newStatus}, validated against the allowed
     * state machine. Forward-only transitions through fulfilment, plus the
     * REFUNDED terminal state from DELIVERED.
     */
    @Transactional
    public Mono<OrderDto> transitionStatus(UUID orderId, Order.OrderStatus newStatus) {
        return orderRepository.findById(orderId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ORDER_NOT_FOUND)))
                .flatMap(order -> {
                    if (!isAllowedTransition(order.getStatus(), newStatus)) {
                        return Mono.error(new BadRequestException(
                                "Illegal status transition: " + order.getStatus() + " -> " + newStatus));
                    }
                    order.setStatus(newStatus);
                    // DELIVERED starts the buyer-protection countdown on the held escrow.
                    Mono<Void> escrowHook = (newStatus == Order.OrderStatus.DELIVERED)
                            ? escrowService.beginProtectionWindow(order.getId())
                            : Mono.empty();
                    return orderRepository.save(order).flatMap(saved -> escrowHook.thenReturn(saved));
                })
                .flatMap(this::enrichWithItems);
    }

    /**
     * Admin cancel: works at any pre-shipped status. Releases reservations if the
     * order was PENDING (stock not yet committed); restocks if CONFIRMED/PROCESSING
     * (stock had been decremented at payment).
     */
    @Transactional
    public Mono<OrderDto> cancelByAdmin(UUID orderId, String reason) {
        return orderRepository.findById(orderId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ORDER_NOT_FOUND)))
                .flatMap(order -> {
                    Order.OrderStatus status = order.getStatus();
                    if (status == Order.OrderStatus.SHIPPED
                            || status == Order.OrderStatus.DELIVERED
                            || status == Order.OrderStatus.CANCELLED
                            || status == Order.OrderStatus.REFUNDED) {
                        return Mono.error(new BadRequestException(
                                "Cannot cancel order in status " + status + " (use refund flow instead)"));
                    }
                    Mono<Long> stockOp = (status == Order.OrderStatus.PENDING)
                            ? stockReservationService.release(order.getId())
                            : stockReservationService.restore(order.getId());
                    order.setStatus(Order.OrderStatus.CANCELLED);
                    order.setCancelledAt(LocalDateTime.now());
                    order.setCancellationReason(reason != null ? reason : "Cancelled by admin");
                    return stockOp.then(orderRepository.save(order));
                })
                .flatMap(this::enrichWithItems);
    }

    private static boolean isAllowedTransition(Order.OrderStatus from, Order.OrderStatus to) {
        return switch (from) {
            case PENDING    -> to == Order.OrderStatus.CONFIRMED || to == Order.OrderStatus.CANCELLED;
            case CONFIRMED  -> to == Order.OrderStatus.PROCESSING || to == Order.OrderStatus.CANCELLED;
            case PROCESSING -> to == Order.OrderStatus.SHIPPED || to == Order.OrderStatus.CANCELLED;
            case SHIPPED    -> to == Order.OrderStatus.DELIVERED;
            case DELIVERED  -> to == Order.OrderStatus.REFUNDED;
            default -> false;
        };
    }

    /**
     * Create a customer-self-checkout order. Stock is RESERVED here (not decremented);
     * the reservation expires after {@link #reservationTtlHours} unless the order is paid.
     */
    @Transactional
    public Mono<OrderDto> create(UUID userId, OrderCreateRequest request) {
        return createInternal(userId, null, request);
    }

    /**
     * Create an order on behalf of {@code customerId}, attributing it to {@code adminId}
     * via {@code placed_by_user_id}. Same reservation flow as customer self-checkout.
     */
    @Transactional
    public Mono<OrderDto> createForCustomer(UUID adminId, UUID customerId, OrderCreateRequest request) {
        return createInternal(customerId, adminId, request);
    }

    private Mono<OrderDto> createInternal(UUID userId, UUID placedByUserId, OrderCreateRequest request) {
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(reservationTtlHours);

        return Mono.zip(
                validateAndGetProducts(request.getItems()).collectList(),
                discountService.findActive().collectList()
        ).flatMap(t -> {
            List<Product> products = t.getT1();
            List<Discount> active = t.getT2();
            // Each line item is priced at the best applicable item-level discount.
            BigDecimal subtotal = computeSubtotal(products, active, request.getItems());
            return persist(userId, placedByUserId, request, products, active, subtotal, expiresAt);
        });
    }

    /**
     * Persist order + items + reservations + (optional) coupon redemption.
     * The coupon is recorded AFTER the order row exists because coupon_uses
     * has a foreign key to orders(id).
     */
    private Mono<OrderDto> persist(UUID userId, UUID placedByUserId, OrderCreateRequest request,
                                    List<Product> products, List<Discount> active,
                                    BigDecimal subtotal, LocalDateTime expiresAt) {
        UUID orderId = UUID.randomUUID();
        Order order = Order.builder()
                .id(orderId)
                .userId(userId)
                .placedByUserId(placedByUserId)
                .status(Order.OrderStatus.PENDING)
                .totalAmount(subtotal)             // overwritten below if a coupon applies
                .subtotalAmount(subtotal)
                .shippingAddress(toJsonString(request.getShippingAddress()))
                .billingAddress(toJsonString(request.getBillingAddress()))
                .paymentMethod(request.getPaymentMethod())
                .paymentStatus(Order.PaymentStatus.PENDING)
                .expiresAt(expiresAt)
                .build();

        Mono<Order> applyCoupon = (request.getCouponCode() == null || request.getCouponCode().isBlank())
                ? Mono.just(order)
                : couponService.applyToOrder(userId, orderId, request.getCouponCode(), subtotal)
                        .map(applied -> {
                            order.setCouponCode(applied.getCode());
                            order.setCouponDiscountAmount(applied.getDiscountAmount());
                            order.setTotalAmount(subtotal.subtract(applied.getDiscountAmount())
                                    .setScale(2, RoundingMode.HALF_UP));
                            return order;
                        });

        return orderRepository.save(order)
                .flatMap(saved -> reserveAll(orderId, request.getItems(), expiresAt)
                        .then(createOrderItems(orderId, products, active, request.getItems()))
                        .then(applyCoupon)
                        .flatMap(orderRepository::save)
                        .then(Mono.just(saved)))
                .flatMap(this::enrichWithItems);
    }

    private BigDecimal computeSubtotal(List<Product> products, List<Discount> active,
                                        List<OrderItemRequest> items) {
        Map<UUID, Product> byId = products.stream()
                .collect(Collectors.toMap(Product::getId, p -> p));
        BigDecimal sum = BigDecimal.ZERO;
        for (OrderItemRequest item : items) {
            Product p = byId.get(item.getProductId());
            BigDecimal unit = discountService.bestFor(p, active)
                    .map(DiscountService.DiscountResult::getDiscountedPrice)
                    .orElse(p.getPrice());
            sum = sum.add(unit.multiply(BigDecimal.valueOf(item.getQuantity())));
        }
        return sum.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Mark an order as paid: commit reservations to permanent stock decrement,
     * set status=CONFIRMED, paymentStatus=COMPLETED. Idempotent against re-delivery
     * of the same Stripe webhook (returns the order unchanged if already paid).
     */
    @Transactional
    public Mono<Order> markPaid(UUID orderId, String paymentIntentId) {
        return orderRepository.findById(orderId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ORDER_NOT_FOUND)))
                .flatMap(order -> {
                    if (order.getPaymentStatus() == Order.PaymentStatus.COMPLETED) {
                        return Mono.just(order); // already processed
                    }
                    if (order.getStatus() == Order.OrderStatus.CANCELLED) {
                        return Mono.error(new BadRequestException("Cannot mark a cancelled order as paid"));
                    }
                    return stockReservationService.commit(orderId)
                            .then(Mono.defer(() -> {
                                order.setStatus(Order.OrderStatus.CONFIRMED);
                                order.setPaymentStatus(Order.PaymentStatus.COMPLETED);
                                order.setPaidAt(LocalDateTime.now());
                                order.setPaymentIntentId(paymentIntentId);
                                return orderRepository.save(order);
                            }))
                            // Marketplace escrow: journal the captured funds as HELD
                            // per seller group until buyer protection concludes.
                            .flatMap(saved -> escrowService.holdFunds(saved).thenReturn(saved));
                });
    }

    /**
     * Cancel a customer's own order: releases the stock reservation if it was still
     * pending, marks the order CANCELLED. Cannot cancel a SHIPPED+ order here.
     */
    @Transactional
    public Mono<OrderDto> cancel(UUID id, UUID userId) {
        return orderRepository.findByIdAndUserId(id, userId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ORDER_NOT_FOUND)))
                .flatMap(order -> {
                    if (order.getStatus() != Order.OrderStatus.PENDING &&
                        order.getStatus() != Order.OrderStatus.CONFIRMED) {
                        return Mono.error(new BadRequestException("Cannot cancel order in current status"));
                    }
                    order.setStatus(Order.OrderStatus.CANCELLED);
                    order.setCancelledAt(LocalDateTime.now());
                    order.setCancellationReason("Cancelled by customer");
                    return stockReservationService.release(order.getId())
                            .then(orderRepository.save(order));
                })
                .flatMap(this::enrichWithItems);
    }

    private Mono<Void> reserveAll(UUID orderId, List<OrderItemRequest> items, LocalDateTime expiresAt) {
        // Reserve sequentially (concatMap) so a later failure doesn't leave half-applied
        // reservations - the @Transactional rollback unwinds the row inserts on error.
        return Flux.fromIterable(items)
                .concatMap(item -> stockReservationService.reserve(
                        orderId, item.getProductId(), item.getQuantity(), expiresAt))
                .then();
    }

    private Flux<Product> validateAndGetProducts(List<OrderItemRequest> items) {
        return Flux.fromIterable(items)
                .flatMap(item -> productRepository.findById(item.getProductId())
                        .switchIfEmpty(Mono.error(new BadRequestException("Product not found: " + item.getProductId())))
                        .flatMap(product -> {
                            if (!product.getIsActive()) {
                                return Mono.error(new BadRequestException("Product is not available: " + product.getName()));
                            }
                            return Mono.just(product);
                        }));
    }

    /**
     * Persist a row per cart line. The {@code price} stored is the actual
     * per-unit price at order time, including any item-level discount.
     */
    private Mono<Void> createOrderItems(UUID orderId, List<Product> products, List<Discount> active,
                                         List<OrderItemRequest> items) {
        Map<UUID, Product> byId = products.stream()
                .collect(Collectors.toMap(Product::getId, p -> p));
        return Flux.fromIterable(items)
                .flatMap(item -> {
                    Product product = byId.get(item.getProductId());
                    BigDecimal unitPrice = discountService.bestFor(product, active)
                            .map(DiscountService.DiscountResult::getDiscountedPrice)
                            .orElse(product.getPrice());

                    OrderItem orderItem = OrderItem.builder()
                            .id(UUID.randomUUID())
                            .orderId(orderId)
                            .productId(product.getId())
                            .productName(product.getName())
                            .productImage(product.getImageUrl())
                            .quantity(item.getQuantity())
                            .price(unitPrice)
                            // Seller snapshot: escrow groups items by this at payment time.
                            .sellerId(product.getVendorId())
                            .returnedQuantity(0)
                            .build();

                    return orderItemRepository.save(orderItem);
                })
                .then();
    }

    private Mono<OrderDto> enrichWithItems(Order order) {
        return orderItemRepository.findByOrderId(order.getId())
                .map(orderMapper::toItemDto)
                .collectList()
                .map(items -> orderMapper.toDto(order, items));
    }

    private String toJsonString(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
