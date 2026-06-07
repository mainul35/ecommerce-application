package com.ecommerce.controller;

import com.ecommerce.dto.DisputeCreateRequest;
import com.ecommerce.dto.DisputeDto;
import com.ecommerce.dto.DisputeMessageDto;
import com.ecommerce.service.DisputeService;
import com.ecommerce.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Dispute conversation between buyer and seller, with escalation to support.
 * Message posting is multipart so image/video evidence can ride along.
 */
@RestController
@RequestMapping("/api/disputes")
@RequiredArgsConstructor
@Tag(name = "Disputes", description = "Buyer protection disputes with conversation and evidence")
public class DisputeController {

    private final DisputeService disputeService;
    private final UserService userService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Open a dispute on a held escrow transaction (buyer only)")
    public Mono<DisputeDto> open(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody DisputeCreateRequest request) {
        return userService.findUserEntityByEmail(userDetails.getUsername())
                .flatMap(user -> disputeService.open(user.getId(), request.getEscrowTransactionId(),
                        request.getOrderItemId(), request.getReason()));
    }

    @GetMapping
    @Operation(summary = "Disputes I participate in (as buyer or seller)")
    public Flux<DisputeDto> mine(@AuthenticationPrincipal UserDetails userDetails) {
        return userService.findUserEntityByEmail(userDetails.getUsername())
                .flatMapMany(user -> disputeService.findMine(user.getId()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Dispute detail (parties only)")
    public Mono<DisputeDto> byId(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id) {
        return userService.findUserEntityByEmail(userDetails.getUsername())
                .flatMap(user -> disputeService.findByIdForParty(id, user.getId()));
    }

    @GetMapping("/{id}/messages")
    @Operation(summary = "Conversation thread, oldest first (parties only)")
    public Flux<DisputeMessageDto> messages(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id) {
        return userService.findUserEntityByEmail(userDetails.getUsername())
                .flatMapMany(user -> disputeService.getMessages(id, user.getId()));
    }

    @PostMapping(value = "/{id}/messages", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Post a message with optional image/video attachments")
    public Mono<DisputeMessageDto> postMessage(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id,
            @RequestPart(value = "body", required = false) String body,
            @RequestPart(value = "files", required = false) Flux<FilePart> files) {
        Flux<FilePart> safeFiles = files != null ? files : Flux.empty();
        return userService.findUserEntityByEmail(userDetails.getUsername())
                .flatMap(user -> safeFiles.collectList()
                        .flatMap((List<FilePart> list) ->
                                disputeService.addMessage(id, user.getId(), body, list)));
    }

    @PostMapping("/{id}/escalate")
    @Operation(summary = "Forward the dispute and its conversation to admin/support")
    public Mono<DisputeDto> escalate(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id) {
        return userService.findUserEntityByEmail(userDetails.getUsername())
                .flatMap(user -> disputeService.escalate(id, user.getId()));
    }

    @PostMapping("/{id}/withdraw")
    @Operation(summary = "Withdraw the dispute (opener only) - escrow resumes its normal flow")
    public Mono<DisputeDto> withdraw(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id) {
        return userService.findUserEntityByEmail(userDetails.getUsername())
                .flatMap(user -> disputeService.withdraw(id, user.getId()));
    }
}
