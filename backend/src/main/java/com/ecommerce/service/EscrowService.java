package com.ecommerce.service;

import com.ecommerce.dto.EscrowTransactionDto;
import com.ecommerce.dto.PagedResponse;
import com.ecommerce.exception.BadRequestException;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.model.EscrowTransaction;
import com.ecommerce.model.Order;
import com.ecommerce.model.OrderItem;
import com.ecommerce.model.ReturnRequest;
import com.ecommerce.model.WalletTransaction;
import com.ecommerce.repository.EscrowTransactionRepository;
import com.ecommerce.repository.OrderItemRepository;
import com.ecommerce.repository.OrderRepository;
import com.ecommerce.repository.UserRepository;
import com.ecommerce.service.payment.PaymentGateway;
import com.ecommerce.service.payment.PaymentGatewayRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Marketplace escrow lifecycle. Money captured at checkout is journaled HELD
 * per (order, seller) group; it is RELEASED to the seller's wallet when the
 * buyer confirms receipt or the protection window expires, and clawed back to
 * the buyer by dispute resolutions / item returns while still HELD.
 *
 * Buyer refunds route through the capturing PaymentGateway when it supports
 * refunds (Stripe -> original card); otherwise they credit the buyer's in-app
 * wallet (cash / one-time methods, unavailable gateways).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EscrowService {

    private final EscrowTransactionRepository escrowRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final UserRepository userRepository;
    private final WalletService walletService;
    private final PaymentGatewayRegistry gatewayRegistry;

    /** Days after delivery during which the buyer can confirm/dispute/return. */
    @Value("${escrow.protection-days:7}")
    private long protectionDays;

    private static final String ESCROW_NOT_FOUND = "Escrow transaction not found";

    // ------------------------------------------------------------------
    // Hold
    // ------------------------------------------------------------------

    /**
     * Called from OrderService.markPaid: split the paid total into one HELD
     * escrow row per seller group. Each group holds its item subtotal minus a
     * proportional share of the order-level coupon discount; rounding remainder
     * lands on the last group so the rows sum exactly to the order total.
     * Idempotent: skips creation if rows already exist (webhook re-delivery).
     */
    @Transactional
    public Mono<Void> holdFunds(Order order) {
        return escrowRepository.findByOrderId(order.getId()).hasElements()
                .flatMap(exists -> {
                    if (Boolean.TRUE.equals(exists)) {
                        return Mono.empty(); // already held (idempotent re-delivery)
                    }
                    return orderItemRepository.findByOrderId(order.getId())
                            .collectList()
                            .flatMapMany(items -> Flux.fromIterable(buildEscrowRows(order, items)))
                            .flatMap(escrowRepository::save)
                            .then();
                });
    }

    private List<EscrowTransaction> buildEscrowRows(Order order, List<OrderItem> items) {
        // Group item subtotals by seller, preserving encounter order so the
        // rounding remainder deterministically lands on the last group.
        Map<UUID, BigDecimal> bySeller = new LinkedHashMap<>();
        for (OrderItem item : items) {
            BigDecimal line = item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
            bySeller.merge(item.getSellerId(), line, BigDecimal::add);
        }

        BigDecimal subtotal = order.getSubtotalAmount() != null
                ? order.getSubtotalAmount()
                : bySeller.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal coupon = order.getCouponDiscountAmount() != null
                ? order.getCouponDiscountAmount() : BigDecimal.ZERO;

        List<EscrowTransaction> rows = new ArrayList<>();
        BigDecimal couponAssigned = BigDecimal.ZERO;
        int i = 0;
        for (Map.Entry<UUID, BigDecimal> group : bySeller.entrySet()) {
            BigDecimal share;
            if (++i == bySeller.size()) {
                share = coupon.subtract(couponAssigned); // remainder -> last group
            } else if (subtotal.signum() > 0) {
                share = coupon.multiply(group.getValue())
                        .divide(subtotal, 2, RoundingMode.HALF_UP);
            } else {
                share = BigDecimal.ZERO;
            }
            couponAssigned = couponAssigned.add(share);

            rows.add(EscrowTransaction.builder()
                    .id(UUID.randomUUID())
                    .orderId(order.getId())
                    .sellerId(group.getKey())
                    .amount(group.getValue().subtract(share).setScale(2, RoundingMode.HALF_UP))
                    .refundedAmount(BigDecimal.ZERO)
                    .currencyCode("USD")
                    .status(EscrowTransaction.EscrowStatus.HELD)
                    .gatewayId(order.getPaymentMethod())
                    .paymentRef(order.getPaymentIntentId())
                    .build());
        }
        return rows;
    }

    // ------------------------------------------------------------------
    // Protection window / release
    // ------------------------------------------------------------------

    /** Called when the order transitions to DELIVERED: start the countdown. */
    @Transactional
    public Mono<Void> beginProtectionWindow(UUID orderId) {
        LocalDateTime deadline = LocalDateTime.now().plusDays(protectionDays);
        return escrowRepository.findByOrderId(orderId)
                .filter(tx -> tx.getStatus() == EscrowTransaction.EscrowStatus.HELD)
                .flatMap(tx -> {
                    tx.setHoldUntil(deadline);
                    return escrowRepository.save(tx);
                })
                .then();
    }

    /**
     * Buyer explicitly confirms receipt: releases every HELD group of the order
     * immediately. DISPUTED groups stay frozen until staff resolves them.
     */
    @Transactional
    public Mono<List<EscrowTransactionDto>> confirmReceipt(UUID orderId, UUID buyerId) {
        return orderRepository.findByIdAndUserId(orderId, buyerId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Order not found")))
                .flatMap(order -> {
                    if (order.getStatus() != Order.OrderStatus.DELIVERED) {
                        return Mono.error(new BadRequestException(
                                "Receipt can only be confirmed once the order is delivered"));
                    }
                    return escrowRepository.findByOrderId(orderId)
                            .filter(tx -> tx.getStatus() == EscrowTransaction.EscrowStatus.HELD)
                            .flatMap(this::release)
                            .map(this::toDto)
                            .collectList();
                });
    }

    /**
     * Release the un-refunded remainder to the seller's wallet and mark the
     * row RELEASED. Platform-owned groups (sellerId null) just flip status -
     * the money is already in the platform account.
     */
    @Transactional
    public Mono<EscrowTransaction> release(EscrowTransaction tx) {
        if (tx.getStatus() == EscrowTransaction.EscrowStatus.RELEASED
                || tx.getStatus() == EscrowTransaction.EscrowStatus.REFUNDED) {
            return Mono.just(tx); // terminal already
        }
        BigDecimal net = tx.getAmount().subtract(tx.getRefundedAmount());
        tx.setStatus(net.signum() > 0
                ? EscrowTransaction.EscrowStatus.RELEASED
                : EscrowTransaction.EscrowStatus.REFUNDED);
        tx.setReleasedAt(LocalDateTime.now());

        Mono<?> payout = (tx.getSellerId() != null && net.signum() > 0)
                ? walletService.credit(tx.getSellerId(), net,
                        WalletTransaction.ReferenceType.ESCROW_RELEASE, tx.getId(),
                        "Escrow release for order " + tx.getOrderId())
                : Mono.just(tx);

        return payout.then(escrowRepository.save(tx))
                .doOnSuccess(saved -> log.info("Escrow {} released ({} to seller {})",
                        saved.getId(), net, saved.getSellerId()));
    }

    /** Admin: force-release a single escrow row (e.g. after an offline agreement). */
    @Transactional
    public Mono<EscrowTransactionDto> releaseById(UUID escrowId) {
        return escrowRepository.findById(escrowId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ESCROW_NOT_FOUND)))
                .flatMap(tx -> {
                    if (tx.getStatus() == EscrowTransaction.EscrowStatus.DISPUTED) {
                        return Mono.error(new BadRequestException(
                                "Escrow has an active dispute - resolve the dispute instead"));
                    }
                    return release(tx);
                })
                .map(this::toDto);
    }

    // ------------------------------------------------------------------
    // Buyer refunds (shared by dispute resolutions and item returns)
    // ------------------------------------------------------------------

    /**
     * Claw {@code amount} back from a non-terminal escrow row to the buyer.
     * Routes through the capturing gateway when it supports refunds, otherwise
     * credits the buyer's wallet. Updates the order's payment status to
     * (PARTIALLY_)REFUNDED accordingly.
     *
     * @return the destination the money actually went to
     */
    @Transactional
    public Mono<ReturnRequest.RefundDestination> refundToBuyer(
            EscrowTransaction tx, BigDecimal amount,
            WalletTransaction.ReferenceType referenceType, UUID referenceId, String description) {

        if (tx.getStatus() == EscrowTransaction.EscrowStatus.RELEASED
                || tx.getStatus() == EscrowTransaction.EscrowStatus.REFUNDED) {
            return Mono.error(new BadRequestException("Escrow funds were already settled"));
        }
        BigDecimal remaining = tx.getAmount().subtract(tx.getRefundedAmount());
        if (amount.signum() <= 0 || amount.compareTo(remaining) > 0) {
            return Mono.error(new BadRequestException(
                    "Refund amount must be between 0 and the remaining held amount (" + remaining + ")"));
        }

        return orderRepository.findById(tx.getOrderId())
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Order not found")))
                .flatMap(order -> executeRefund(order, tx, amount, referenceType, referenceId, description)
                        .flatMap(destination -> {
                            tx.setRefundedAmount(tx.getRefundedAmount().add(amount));
                            if (tx.getRefundedAmount().compareTo(tx.getAmount()) >= 0) {
                                tx.setStatus(EscrowTransaction.EscrowStatus.REFUNDED);
                                tx.setReleasedAt(LocalDateTime.now());
                            }
                            return escrowRepository.save(tx)
                                    .then(updateOrderPaymentStatus(order))
                                    .thenReturn(destination);
                        }));
    }

    private Mono<ReturnRequest.RefundDestination> executeRefund(
            Order order, EscrowTransaction tx, BigDecimal amount,
            WalletTransaction.ReferenceType referenceType, UUID referenceId, String description) {

        Optional<PaymentGateway> gateway = tx.getGatewayId() != null
                ? gatewayRegistry.find(tx.getGatewayId())
                : Optional.empty();

        if (gateway.isPresent() && gateway.get().supportsRefunds() && tx.getPaymentRef() != null) {
            return gateway.get().refund(tx.getPaymentRef(), amount)
                    .doOnSuccess(v -> log.info("Refunded {} via {} for order {}",
                            amount, tx.getGatewayId(), order.getId()))
                    .thenReturn(ReturnRequest.RefundDestination.GATEWAY);
        }

        // Cash / one-time methods or refund-incapable gateway: in-app wallet.
        return walletService.credit(order.getUserId(), amount, referenceType, referenceId, description)
                .doOnSuccess(w -> log.info("Refunded {} to wallet of user {} for order {}",
                        amount, order.getUserId(), order.getId()))
                .thenReturn(ReturnRequest.RefundDestination.WALLET);
    }

    private Mono<Order> updateOrderPaymentStatus(Order order) {
        return escrowRepository.findByOrderId(order.getId())
                .map(EscrowTransaction::getRefundedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .flatMap(totalRefunded -> {
                    if (totalRefunded.signum() <= 0) {
                        return Mono.just(order);
                    }
                    order.setPaymentStatus(totalRefunded.compareTo(order.getTotalAmount()) >= 0
                            ? Order.PaymentStatus.REFUNDED
                            : Order.PaymentStatus.PARTIALLY_REFUNDED);
                    return orderRepository.save(order);
                });
    }

    // ------------------------------------------------------------------
    // Dispute hooks
    // ------------------------------------------------------------------

    /** Freeze auto-release while a dispute is active. */
    @Transactional
    public Mono<EscrowTransaction> markDisputed(UUID escrowId) {
        return requireStatus(escrowId, EscrowTransaction.EscrowStatus.HELD,
                "Only HELD escrow funds can be disputed")
                .flatMap(tx -> {
                    tx.setStatus(EscrowTransaction.EscrowStatus.DISPUTED);
                    return escrowRepository.save(tx);
                });
    }

    /** Back to HELD after a withdrawn dispute. */
    @Transactional
    public Mono<EscrowTransaction> markHeld(UUID escrowId) {
        return requireStatus(escrowId, EscrowTransaction.EscrowStatus.DISPUTED,
                "Escrow is not in DISPUTED state")
                .flatMap(tx -> {
                    tx.setStatus(EscrowTransaction.EscrowStatus.HELD);
                    return escrowRepository.save(tx);
                });
    }

    private Mono<EscrowTransaction> requireStatus(UUID escrowId,
                                                  EscrowTransaction.EscrowStatus expected,
                                                  String message) {
        return escrowRepository.findById(escrowId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ESCROW_NOT_FOUND)))
                .flatMap(tx -> tx.getStatus() == expected
                        ? Mono.just(tx)
                        : Mono.error(new BadRequestException(message)));
    }

    // ------------------------------------------------------------------
    // Queries
    // ------------------------------------------------------------------

    public Mono<EscrowTransaction> findEntityById(UUID id) {
        return escrowRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(ESCROW_NOT_FOUND)));
    }

    /** Buyer view: escrow state of one of their own orders. */
    public Flux<EscrowTransactionDto> findByOrderForBuyer(UUID orderId, UUID buyerId) {
        return orderRepository.findByIdAndUserId(orderId, buyerId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Order not found")))
                .flatMapMany(order -> escrowRepository.findByOrderId(orderId))
                .flatMap(this::toDtoWithSellerName);
    }

    /** Admin: list escrow rows, optionally filtered by status. */
    public Mono<PagedResponse<EscrowTransactionDto>> findAllForAdmin(
            EscrowTransaction.EscrowStatus status, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        if (status != null) {
            return escrowRepository.findByStatus(status, pageRequest)
                    .flatMap(this::toDtoWithSellerName)
                    .collectList()
                    .zipWith(escrowRepository.countByStatus(status))
                    .map(t -> PagedResponse.of(t.getT1(), page, size, t.getT2()));
        }
        return escrowRepository.findAll()
                .sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .skip((long) page * size)
                .take(size)
                .flatMap(this::toDtoWithSellerName)
                .collectList()
                .zipWith(escrowRepository.count())
                .map(t -> PagedResponse.of(t.getT1(), page, size, t.getT2()));
    }

    // ------------------------------------------------------------------
    // Mapping
    // ------------------------------------------------------------------

    public EscrowTransactionDto toDto(EscrowTransaction tx) {
        return EscrowTransactionDto.builder()
                .id(tx.getId())
                .orderId(tx.getOrderId())
                .sellerId(tx.getSellerId())
                .amount(tx.getAmount())
                .refundedAmount(tx.getRefundedAmount())
                .currencyCode(tx.getCurrencyCode())
                .status(tx.getStatus())
                .gatewayId(tx.getGatewayId())
                .holdUntil(tx.getHoldUntil())
                .releasedAt(tx.getReleasedAt())
                .createdAt(tx.getCreatedAt())
                .build();
    }

    private Mono<EscrowTransactionDto> toDtoWithSellerName(EscrowTransaction tx) {
        EscrowTransactionDto dto = toDto(tx);
        if (tx.getSellerId() == null) {
            dto.setSellerName("Platform");
            return Mono.just(dto);
        }
        return userRepository.findById(tx.getSellerId())
                .map(u -> {
                    dto.setSellerName(u.getFirstName() + " " + u.getLastName());
                    return dto;
                })
                .defaultIfEmpty(dto);
    }
}
