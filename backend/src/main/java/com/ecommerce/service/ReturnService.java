package com.ecommerce.service;

import com.ecommerce.dto.PagedResponse;
import com.ecommerce.dto.ReturnRequestDto;
import com.ecommerce.exception.BadRequestException;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.exception.UnauthorizedException;
import com.ecommerce.model.EscrowTransaction;
import com.ecommerce.model.Order;
import com.ecommerce.model.OrderItem;
import com.ecommerce.model.ReturnRequest;
import com.ecommerce.model.User;
import com.ecommerce.model.WalletTransaction;
import com.ecommerce.repository.EscrowTransactionRepository;
import com.ecommerce.repository.OrderItemRepository;
import com.ecommerce.repository.OrderRepository;
import com.ecommerce.repository.ReturnRequestRepository;
import com.ecommerce.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Per-item partial returns. Items bought together (even from different
 * sellers) can be returned independently with a quantity, while that seller's
 * escrow group is still HELD. The refund is the item's effective unit price x
 * quantity minus a proportional share of the order-level coupon discount,
 * clawed back from the seller's escrow before it is ever released.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReturnService {

    private final ReturnRequestRepository returnRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final EscrowTransactionRepository escrowRepository;
    private final UserRepository userRepository;
    private final EscrowService escrowService;

    private static final String RETURN_NOT_FOUND = "Return request not found";

    // ------------------------------------------------------------------
    // Buyer side
    // ------------------------------------------------------------------

    /** Buyer requests to return {@code quantity} units of one delivered item. */
    @Transactional
    public Mono<ReturnRequestDto> request(UUID buyerId, UUID orderId, UUID orderItemId,
                                          int quantity, String reason) {
        return orderRepository.findByIdAndUserId(orderId, buyerId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Order not found")))
                .flatMap(order -> {
                    if (order.getStatus() != Order.OrderStatus.DELIVERED) {
                        return Mono.error(new BadRequestException(
                                "Returns can only be requested after delivery"));
                    }
                    return orderItemRepository.findById(orderItemId)
                            .filter(item -> orderId.equals(item.getOrderId()))
                            .switchIfEmpty(Mono.error(new ResourceNotFoundException("Order item not found")))
                            .flatMap(item -> validateAndCreate(order, item, buyerId, quantity, reason));
                })
                .flatMap(this::toDtoEnriched);
    }

    private Mono<ReturnRequest> validateAndCreate(Order order, OrderItem item, UUID buyerId,
                                                  int quantity, String reason) {
        int returnable = item.getQuantity()
                - (item.getReturnedQuantity() != null ? item.getReturnedQuantity() : 0);
        if (quantity < 1 || quantity > returnable) {
            return Mono.error(new BadRequestException(
                    "Return quantity must be between 1 and " + returnable));
        }
        return returnRepository.findByOrderItemIdAndStatus(item.getId(), ReturnRequest.ReturnStatus.REQUESTED)
                .flatMap(existing -> Mono.<ReturnRequest>error(new BadRequestException(
                        "A return request for this item is already pending")))
                .switchIfEmpty(Mono.defer(() -> findHeldEscrowForItem(order, item)
                        .flatMap(tx -> {
                            BigDecimal refund = computeRefund(order, item, quantity, tx);
                            return returnRepository.save(ReturnRequest.builder()
                                    .id(UUID.randomUUID())
                                    .orderId(order.getId())
                                    .orderItemId(item.getId())
                                    .escrowTransactionId(tx.getId())
                                    .requestedByUserId(buyerId)
                                    .quantity(quantity)
                                    .reason(reason)
                                    .status(ReturnRequest.ReturnStatus.REQUESTED)
                                    .refundAmount(refund)
                                    .build());
                        })));
    }

    private Mono<EscrowTransaction> findHeldEscrowForItem(Order order, OrderItem item) {
        return escrowRepository.findByOrderId(order.getId())
                .filter(tx -> java.util.Objects.equals(tx.getSellerId(), item.getSellerId()))
                .next()
                .switchIfEmpty(Mono.error(new BadRequestException(
                        "No escrow found for this item - was the order paid?")))
                .flatMap(tx -> switch (tx.getStatus()) {
                    case HELD -> Mono.just(tx);
                    case DISPUTED -> Mono.error(new BadRequestException(
                            "This seller group has an active dispute - resolve it first"));
                    default -> Mono.error(new BadRequestException(
                            "The return window for this item has closed (funds already settled)"));
                });
    }

    /**
     * Refund = unit price x qty minus the proportional coupon share for those
     * units, capped at what is still held in the seller's escrow.
     */
    private BigDecimal computeRefund(Order order, OrderItem item, int quantity, EscrowTransaction tx) {
        BigDecimal gross = item.getPrice().multiply(BigDecimal.valueOf(quantity));
        BigDecimal coupon = order.getCouponDiscountAmount() != null
                ? order.getCouponDiscountAmount() : BigDecimal.ZERO;
        BigDecimal subtotal = order.getSubtotalAmount();
        BigDecimal couponShare = (coupon.signum() > 0 && subtotal != null && subtotal.signum() > 0)
                ? coupon.multiply(gross).divide(subtotal, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal refund = gross.subtract(couponShare).setScale(2, RoundingMode.HALF_UP);
        BigDecimal remaining = tx.getAmount().subtract(tx.getRefundedAmount());
        return refund.min(remaining).max(BigDecimal.ZERO);
    }

    /** Buyer withdraws a pending request. */
    @Transactional
    public Mono<ReturnRequestDto> cancel(UUID buyerId, UUID returnId) {
        return returnRepository.findById(returnId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(RETURN_NOT_FOUND)))
                .flatMap(request -> {
                    if (!buyerId.equals(request.getRequestedByUserId())) {
                        return Mono.error(new UnauthorizedException("Not your return request"));
                    }
                    if (request.getStatus() != ReturnRequest.ReturnStatus.REQUESTED) {
                        return Mono.error(new BadRequestException("Request was already decided"));
                    }
                    request.setStatus(ReturnRequest.ReturnStatus.CANCELLED);
                    request.setResolvedAt(LocalDateTime.now());
                    return returnRepository.save(request);
                })
                .flatMap(this::toDtoEnriched);
    }

    public Flux<ReturnRequestDto> findMine(UUID userId) {
        return returnRepository.findByRequestedByUserIdOrderByCreatedAtDesc(userId)
                .concatMap(this::toDtoEnriched);
    }

    // ------------------------------------------------------------------
    // Decision side (staff, or the seller of the escrow group)
    // ------------------------------------------------------------------

    /** Approve: executes the refund (gateway or wallet) and books the returned units. */
    @Transactional
    public Mono<ReturnRequestDto> approve(UUID deciderId, UUID returnId) {
        return loadPendingForDecider(deciderId, returnId)
                .flatMap(request -> escrowService.findEntityById(request.getEscrowTransactionId())
                        .flatMap(tx -> escrowService.refundToBuyer(tx, request.getRefundAmount(),
                                WalletTransaction.ReferenceType.RETURN_REFUND, request.getId(),
                                "Return refund for order " + request.getOrderId()))
                        .flatMap(destination -> {
                            request.setStatus(ReturnRequest.ReturnStatus.REFUNDED);
                            request.setRefundDestination(destination);
                            request.setResolvedByUserId(deciderId);
                            request.setResolvedAt(LocalDateTime.now());
                            return bookReturnedUnits(request).then(returnRepository.save(request));
                        }))
                .flatMap(this::toDtoEnriched)
                .doOnSuccess(r -> log.info("Return {} approved - refunded {} via {}",
                        returnId, r.getRefundAmount(), r.getRefundDestination()));
    }

    @Transactional
    public Mono<ReturnRequestDto> reject(UUID deciderId, UUID returnId, String reason) {
        return loadPendingForDecider(deciderId, returnId)
                .flatMap(request -> {
                    request.setStatus(ReturnRequest.ReturnStatus.REJECTED);
                    request.setRejectionReason(reason);
                    request.setResolvedByUserId(deciderId);
                    request.setResolvedAt(LocalDateTime.now());
                    return returnRepository.save(request);
                })
                .flatMap(this::toDtoEnriched);
    }

    private Mono<ReturnRequest> loadPendingForDecider(UUID deciderId, UUID returnId) {
        return returnRepository.findById(returnId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(RETURN_NOT_FOUND)))
                .flatMap(request -> {
                    if (request.getStatus() != ReturnRequest.ReturnStatus.REQUESTED) {
                        return Mono.error(new BadRequestException("Request was already decided"));
                    }
                    return escrowService.findEntityById(request.getEscrowTransactionId())
                            .flatMap(tx -> {
                                if (deciderId.equals(tx.getSellerId())) {
                                    return Mono.just(request); // the seller may decide their own group
                                }
                                return userRepository.findById(deciderId)
                                        .filter(u -> u.getRole() == User.UserRole.ADMIN
                                                || u.getRole() == User.UserRole.MANAGER)
                                        .map(u -> request)
                                        .switchIfEmpty(Mono.error(new UnauthorizedException(
                                                "Only staff or the item's seller can decide a return")));
                            });
                });
    }

    private Mono<Void> bookReturnedUnits(ReturnRequest request) {
        return orderItemRepository.findById(request.getOrderItemId())
                .flatMap(item -> {
                    int already = item.getReturnedQuantity() != null ? item.getReturnedQuantity() : 0;
                    item.setReturnedQuantity(already + request.getQuantity());
                    return orderItemRepository.save(item);
                })
                .then();
    }

    // ------------------------------------------------------------------
    // Admin queries
    // ------------------------------------------------------------------

    public Mono<PagedResponse<ReturnRequestDto>> findAllForAdmin(ReturnRequest.ReturnStatus status,
                                                                 int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        if (status != null) {
            return returnRepository.findByStatus(status, pageRequest)
                    .concatMap(this::toDtoEnriched)
                    .collectList()
                    .zipWith(returnRepository.countByStatus(status))
                    .map(t -> PagedResponse.of(t.getT1(), page, size, t.getT2()));
        }
        return returnRepository.findAll()
                .sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .skip((long) page * size)
                .take(size)
                .concatMap(this::toDtoEnriched)
                .collectList()
                .zipWith(returnRepository.count())
                .map(t -> PagedResponse.of(t.getT1(), page, size, t.getT2()));
    }

    // ------------------------------------------------------------------
    // Mapping
    // ------------------------------------------------------------------

    private Mono<ReturnRequestDto> toDtoEnriched(ReturnRequest request) {
        ReturnRequestDto dto = ReturnRequestDto.builder()
                .id(request.getId())
                .orderId(request.getOrderId())
                .orderItemId(request.getOrderItemId())
                .escrowTransactionId(request.getEscrowTransactionId())
                .quantity(request.getQuantity())
                .reason(request.getReason())
                .status(request.getStatus())
                .refundAmount(request.getRefundAmount())
                .refundDestination(request.getRefundDestination())
                .rejectionReason(request.getRejectionReason())
                .resolvedAt(request.getResolvedAt())
                .createdAt(request.getCreatedAt())
                .build();
        return orderItemRepository.findById(request.getOrderItemId())
                .map(item -> {
                    dto.setProductName(item.getProductName());
                    dto.setProductImage(item.getProductImage());
                    return dto;
                })
                .defaultIfEmpty(dto);
    }
}
