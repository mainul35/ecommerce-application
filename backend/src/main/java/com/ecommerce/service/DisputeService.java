package com.ecommerce.service;

import com.ecommerce.dto.DisputeAttachmentDto;
import com.ecommerce.dto.DisputeDto;
import com.ecommerce.dto.DisputeMessageDto;
import com.ecommerce.dto.DisputeResolveRequest;
import com.ecommerce.dto.PagedResponse;
import com.ecommerce.exception.BadRequestException;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.exception.UnauthorizedException;
import com.ecommerce.model.Dispute;
import com.ecommerce.model.DisputeAttachment;
import com.ecommerce.model.DisputeMessage;
import com.ecommerce.model.EscrowTransaction;
import com.ecommerce.model.Order;
import com.ecommerce.model.User;
import com.ecommerce.model.WalletTransaction;
import com.ecommerce.repository.DisputeAttachmentRepository;
import com.ecommerce.repository.DisputeMessageRepository;
import com.ecommerce.repository.DisputeRepository;
import com.ecommerce.repository.OrderItemRepository;
import com.ecommerce.repository.OrderRepository;
import com.ecommerce.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Dispute lifecycle on top of HELD escrow funds.
 *
 * Conversation model: the buyer opens a dispute against one escrow group and
 * talks to the seller in a message thread (image/video evidence supported).
 * Either side can ESCALATE, which forwards the whole conversation to the
 * admin/support queue. Staff resolves with the full thread in view:
 * RELEASE (seller wins) or REFUND (buyer wins, full or partial - partial
 * refunds release the remainder to the seller, concluding the dispute).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DisputeService {

    private final DisputeRepository disputeRepository;
    private final DisputeMessageRepository messageRepository;
    private final DisputeAttachmentRepository attachmentRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final UserRepository userRepository;
    private final EscrowService escrowService;
    private final DisputeMediaService disputeMediaService;

    private static final String DISPUTE_NOT_FOUND = "Dispute not found";

    // ------------------------------------------------------------------
    // Open / escalate / withdraw
    // ------------------------------------------------------------------

    /** Buyer opens a dispute on a HELD escrow group of their own order. */
    @Transactional
    public Mono<DisputeDto> open(UUID buyerId, UUID escrowTransactionId, UUID orderItemId,
                                 String reason) {
        return escrowService.findEntityById(escrowTransactionId)
                .flatMap(tx -> orderRepository.findByIdAndUserId(tx.getOrderId(), buyerId)
                        .switchIfEmpty(Mono.error(new UnauthorizedException(
                                "Only the buyer of the order can open a dispute")))
                        .thenReturn(tx))
                // Integrity: if the buyer names a specific order item, it must belong
                // to the disputed order - never accept a foreign/arbitrary item id.
                .flatMap(tx -> validateOrderItem(tx.getOrderId(), orderItemId).thenReturn(tx))
                .flatMap(tx -> escrowService.markDisputed(tx.getId()))
                .flatMap(tx -> disputeRepository.save(Dispute.builder()
                        .id(UUID.randomUUID())
                        .escrowTransactionId(tx.getId())
                        .orderId(tx.getOrderId())
                        .orderItemId(orderItemId)
                        .openedByUserId(buyerId)
                        .reason(reason)
                        .status(Dispute.DisputeStatus.OPEN)
                        .build()))
                .flatMap(this::toDtoEnriched)
                .doOnSuccess(d -> log.info("Dispute {} opened on escrow {}", d.getId(), escrowTransactionId));
    }

    /** Ensure an optionally-supplied order item actually belongs to the order. */
    private Mono<Void> validateOrderItem(UUID orderId, UUID orderItemId) {
        if (orderItemId == null) {
            return Mono.empty();
        }
        return orderItemRepository.findById(orderItemId)
                .switchIfEmpty(Mono.error(new BadRequestException("Order item not found")))
                .flatMap(item -> orderId.equals(item.getOrderId())
                        ? Mono.empty()
                        : Mono.error(new BadRequestException(
                                "Order item does not belong to the disputed order")));
    }

    /**
     * Forward the dispute (with its full conversation) to admin/support.
     * Both buyer and seller may escalate.
     */
    @Transactional
    public Mono<DisputeDto> escalate(UUID disputeId, UUID userId) {
        return requireParty(disputeId, userId)
                .flatMap(ctx -> {
                    if (ctx.dispute().getStatus() != Dispute.DisputeStatus.OPEN) {
                        return Mono.error(new BadRequestException("Only OPEN disputes can be escalated"));
                    }
                    ctx.dispute().setStatus(Dispute.DisputeStatus.ESCALATED);
                    ctx.dispute().setEscalatedAt(LocalDateTime.now());
                    return disputeRepository.save(ctx.dispute());
                })
                .flatMap(this::toDtoEnriched)
                .doOnSuccess(d -> log.info("Dispute {} escalated to support", disputeId));
    }

    /** Buyer withdraws the dispute - escrow returns to HELD (auto-release resumes). */
    @Transactional
    public Mono<DisputeDto> withdraw(UUID disputeId, UUID buyerId) {
        return disputeRepository.findById(disputeId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(DISPUTE_NOT_FOUND)))
                .flatMap(dispute -> {
                    if (!buyerId.equals(dispute.getOpenedByUserId())) {
                        return Mono.error(new UnauthorizedException("Only the opener can withdraw a dispute"));
                    }
                    if (!isActive(dispute)) {
                        return Mono.error(new BadRequestException("Dispute is already closed"));
                    }
                    dispute.setStatus(Dispute.DisputeStatus.WITHDRAWN);
                    dispute.setResolvedAt(LocalDateTime.now());
                    return escrowService.markHeld(dispute.getEscrowTransactionId())
                            .then(disputeRepository.save(dispute));
                })
                .flatMap(this::toDtoEnriched);
    }

    // ------------------------------------------------------------------
    // Conversation
    // ------------------------------------------------------------------

    /**
     * Append a message (with optional image/video attachments) to the thread.
     * Buyer, the escrow group's seller, and staff may post while the dispute
     * is active. The author side is snapshotted on the row for rendering.
     */
    @Transactional
    public Mono<DisputeMessageDto> addMessage(UUID disputeId, UUID senderId,
                                              String body, List<FilePart> files) {
        boolean hasFiles = files != null && !files.isEmpty();
        if ((body == null || body.isBlank()) && !hasFiles) {
            return Mono.error(new BadRequestException("Message must have text or attachments"));
        }
        return requireParty(disputeId, senderId)
                .flatMap(ctx -> {
                    if (!isActive(ctx.dispute())) {
                        return Mono.error(new BadRequestException("Dispute is closed - no further messages"));
                    }
                    return messageRepository.save(DisputeMessage.builder()
                                    .id(UUID.randomUUID())
                                    .disputeId(disputeId)
                                    .senderUserId(senderId)
                                    .authorRole(ctx.role())
                                    .body(body)
                                    .build())
                            .flatMap(message -> Flux.fromIterable(hasFiles ? files : List.<FilePart>of())
                                    .concatMap(file -> disputeMediaService.store(disputeId, message.getId(), file))
                                    .collectList()
                                    .flatMap(attachments -> toMessageDto(message, attachments)));
                });
    }

    /** Full conversation, oldest first, attachments and sender names included. */
    public Flux<DisputeMessageDto> getMessages(UUID disputeId, UUID requesterId) {
        return requireParty(disputeId, requesterId)
                .flatMapMany(ctx -> messageRepository.findByDisputeIdOrderByCreatedAtAsc(disputeId))
                .concatMap(message -> attachmentRepository.findByDisputeMessageId(message.getId())
                        .collectList()
                        .flatMap(attachments -> toMessageDto(message, attachments)));
    }

    /**
     * Authorize and resolve a dispute attachment for streaming. The caller must
     * be a party to the dispute (buyer, the escrow group's seller, or staff),
     * AND the attachment must belong to a message of THIS dispute (blocks
     * cross-dispute id reuse). This replaces the previous static file serving,
     * which exposed evidence to any authenticated user who knew the URL.
     * Mirrors the KYC {@code documentForViewer} owner-or-staff check.
     */
    public Mono<DisputeAttachment> attachmentForViewer(UUID disputeId, UUID viewerId, UUID attachmentId) {
        return requireParty(disputeId, viewerId)
                .flatMap(ctx -> attachmentRepository.findById(attachmentId)
                        .switchIfEmpty(Mono.error(new ResourceNotFoundException("Attachment not found")))
                        .flatMap(attachment -> messageRepository.findById(attachment.getDisputeMessageId())
                                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Attachment not found")))
                                .flatMap(message -> disputeId.equals(message.getDisputeId())
                                        ? Mono.just(attachment)
                                        : Mono.error(new UnauthorizedException(
                                                "Attachment does not belong to this dispute")))));
    }

    // ------------------------------------------------------------------
    // Resolution (staff)
    // ------------------------------------------------------------------

    /**
     * Staff verdict on an active dispute:
     *  - RELEASE: seller wins; remaining escrow goes to the seller's wallet.
     *  - REFUND:  buyer wins; refundAmount (default: full remainder) goes back
     *    through the original gateway or to the buyer's wallet. A partial
     *    refund releases the remainder to the seller - the dispute concludes
     *    either way.
     */
    @Transactional
    public Mono<DisputeDto> resolve(UUID disputeId, UUID staffUserId, DisputeResolveRequest request) {
        return disputeRepository.findById(disputeId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(DISPUTE_NOT_FOUND)))
                .flatMap(dispute -> {
                    if (!isActive(dispute)) {
                        return Mono.error(new BadRequestException("Dispute is already resolved"));
                    }
                    return escrowService.findEntityById(dispute.getEscrowTransactionId())
                            .flatMap(tx -> switch (request.getAction()) {
                                case RELEASE -> resolveRelease(dispute, tx, staffUserId, request);
                                case REFUND -> resolveRefund(dispute, tx, staffUserId, request);
                            });
                })
                .flatMap(this::toDtoEnriched)
                .doOnSuccess(d -> log.info("Dispute {} resolved as {}", disputeId, d.getStatus()));
    }

    private Mono<Dispute> resolveRelease(Dispute dispute, EscrowTransaction tx,
                                         UUID staffUserId, DisputeResolveRequest request) {
        dispute.setStatus(Dispute.DisputeStatus.RESOLVED_RELEASED);
        dispute.setResolvedByUserId(staffUserId);
        dispute.setResolutionNote(request.getNote());
        dispute.setResolvedAt(LocalDateTime.now());
        return escrowService.release(tx).then(disputeRepository.save(dispute));
    }

    private Mono<Dispute> resolveRefund(Dispute dispute, EscrowTransaction tx,
                                        UUID staffUserId, DisputeResolveRequest request) {
        BigDecimal remaining = tx.getAmount().subtract(tx.getRefundedAmount());
        BigDecimal amount = request.getRefundAmount() != null ? request.getRefundAmount() : remaining;

        return escrowService.refundToBuyer(tx, amount,
                        WalletTransaction.ReferenceType.DISPUTE_REFUND, dispute.getId(),
                        "Dispute refund for order " + dispute.getOrderId())
                .flatMap(destination -> {
                    dispute.setStatus(Dispute.DisputeStatus.RESOLVED_REFUNDED);
                    dispute.setResolvedByUserId(staffUserId);
                    dispute.setResolutionNote(request.getNote());
                    dispute.setRefundAmount(amount);
                    dispute.setResolvedAt(LocalDateTime.now());
                    // Partial refund: hand the remainder to the seller now - the
                    // dispute is concluded, nothing is left to argue over.
                    Mono<?> remainder = amount.compareTo(remaining) < 0
                            ? escrowService.findEntityById(tx.getId()).flatMap(escrowService::release)
                            : Mono.just(tx);
                    return remainder.then(disputeRepository.save(dispute));
                });
    }

    // ------------------------------------------------------------------
    // Queries
    // ------------------------------------------------------------------

    /** Disputes the user participates in - as buyer or as seller. */
    public Flux<DisputeDto> findMine(UUID userId) {
        return disputeRepository.findByOpenedByUserIdOrderByCreatedAtDesc(userId)
                .concatWith(disputeRepository.findBySellerId(userId))
                .distinct(Dispute::getId)
                .concatMap(this::toDtoEnriched);
    }

    public Mono<DisputeDto> findByIdForParty(UUID disputeId, UUID userId) {
        return requireParty(disputeId, userId)
                .flatMap(ctx -> toDtoEnriched(ctx.dispute()));
    }

    /** Admin queue: defaults to ESCALATED first when no filter is given. */
    public Mono<PagedResponse<DisputeDto>> findAllForAdmin(Dispute.DisputeStatus status,
                                                           int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        if (status != null) {
            return disputeRepository.findByStatus(status, pageRequest)
                    .concatMap(this::toDtoEnriched)
                    .collectList()
                    .zipWith(disputeRepository.countByStatus(status))
                    .map(t -> PagedResponse.of(t.getT1(), page, size, t.getT2()));
        }
        return disputeRepository.findAll()
                .sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .skip((long) page * size)
                .take(size)
                .concatMap(this::toDtoEnriched)
                .collectList()
                .zipWith(disputeRepository.count())
                .map(t -> PagedResponse.of(t.getT1(), page, size, t.getT2()));
    }

    public Mono<DisputeDto> findByIdForAdmin(UUID disputeId) {
        return disputeRepository.findById(disputeId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(DISPUTE_NOT_FOUND)))
                .flatMap(this::toDtoEnriched);
    }

    /** Admin thread view - no party check. */
    public Flux<DisputeMessageDto> getMessagesForAdmin(UUID disputeId) {
        return messageRepository.findByDisputeIdOrderByCreatedAtAsc(disputeId)
                .concatMap(message -> attachmentRepository.findByDisputeMessageId(message.getId())
                        .collectList()
                        .flatMap(attachments -> toMessageDto(message, attachments)));
    }

    // ------------------------------------------------------------------
    // Party / role plumbing
    // ------------------------------------------------------------------

    private record PartyContext(Dispute dispute, DisputeMessage.AuthorRole role) {
    }

    /**
     * Resolve the caller's side in this dispute: buyer of the order, seller of
     * the escrow group, or staff. Everyone else is rejected.
     */
    private Mono<PartyContext> requireParty(UUID disputeId, UUID userId) {
        return disputeRepository.findById(disputeId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(DISPUTE_NOT_FOUND)))
                .flatMap(dispute -> escrowService.findEntityById(dispute.getEscrowTransactionId())
                        .flatMap(tx -> orderRepository.findById(dispute.getOrderId())
                                .flatMap(order -> resolveRole(order, tx, userId)
                                        .map(role -> new PartyContext(dispute, role)))));
    }

    private Mono<DisputeMessage.AuthorRole> resolveRole(Order order, EscrowTransaction tx, UUID userId) {
        if (userId.equals(order.getUserId())) {
            return Mono.just(DisputeMessage.AuthorRole.BUYER);
        }
        if (userId.equals(tx.getSellerId())) {
            return Mono.just(DisputeMessage.AuthorRole.SELLER);
        }
        return userRepository.findById(userId)
                .filter(u -> u.getRole() == User.UserRole.ADMIN || u.getRole() == User.UserRole.MANAGER)
                .map(u -> DisputeMessage.AuthorRole.STAFF)
                .switchIfEmpty(Mono.error(new UnauthorizedException("Not a party to this dispute")));
    }

    private static boolean isActive(Dispute dispute) {
        return dispute.getStatus() == Dispute.DisputeStatus.OPEN
                || dispute.getStatus() == Dispute.DisputeStatus.ESCALATED;
    }

    // ------------------------------------------------------------------
    // Mapping
    // ------------------------------------------------------------------

    private Mono<DisputeDto> toDtoEnriched(Dispute dispute) {
        return escrowService.findEntityById(dispute.getEscrowTransactionId())
                .flatMap(tx -> {
                    DisputeDto dto = DisputeDto.builder()
                            .id(dispute.getId())
                            .escrowTransactionId(dispute.getEscrowTransactionId())
                            .orderId(dispute.getOrderId())
                            .orderItemId(dispute.getOrderItemId())
                            .openedByUserId(dispute.getOpenedByUserId())
                            .sellerId(tx.getSellerId())
                            .escrowAmount(tx.getAmount())
                            .escrowRefundedAmount(tx.getRefundedAmount())
                            .reason(dispute.getReason())
                            .status(dispute.getStatus())
                            .escalatedAt(dispute.getEscalatedAt())
                            .resolvedByUserId(dispute.getResolvedByUserId())
                            .resolutionNote(dispute.getResolutionNote())
                            .refundAmount(dispute.getRefundAmount())
                            .resolvedAt(dispute.getResolvedAt())
                            .createdAt(dispute.getCreatedAt())
                            .build();
                    Mono<DisputeDto> withOpener = userRepository.findById(dispute.getOpenedByUserId())
                            .map(u -> {
                                dto.setOpenedByName(u.getFirstName() + " " + u.getLastName());
                                return dto;
                            })
                            .defaultIfEmpty(dto);
                    if (tx.getSellerId() == null) {
                        dto.setSellerName("Platform");
                        return withOpener;
                    }
                    return withOpener.flatMap(d -> userRepository.findById(tx.getSellerId())
                            .map(u -> {
                                d.setSellerName(u.getFirstName() + " " + u.getLastName());
                                return d;
                            })
                            .defaultIfEmpty(d));
                });
    }

    private Mono<DisputeMessageDto> toMessageDto(DisputeMessage message, List<DisputeAttachment> attachments) {
        DisputeMessageDto dto = DisputeMessageDto.builder()
                .id(message.getId())
                .disputeId(message.getDisputeId())
                .senderUserId(message.getSenderUserId())
                .authorRole(message.getAuthorRole())
                .body(message.getBody())
                .attachments(attachments.stream().map(this::toAttachmentDto).toList())
                .createdAt(message.getCreatedAt())
                .build();
        return userRepository.findById(message.getSenderUserId())
                .map(u -> {
                    dto.setSenderName(u.getFirstName() + " " + u.getLastName());
                    return dto;
                })
                .defaultIfEmpty(dto);
    }

    private DisputeAttachmentDto toAttachmentDto(DisputeAttachment a) {
        return DisputeAttachmentDto.builder()
                .id(a.getId())
                .url(a.getUrl())
                .originalName(a.getOriginalName())
                .contentType(a.getContentType())
                .attachmentType(a.getAttachmentType())
                .sizeBytes(a.getSizeBytes())
                .build();
    }
}
