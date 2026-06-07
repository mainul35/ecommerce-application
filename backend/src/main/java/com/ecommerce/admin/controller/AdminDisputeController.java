package com.ecommerce.admin.controller;

import com.ecommerce.dto.DisputeDto;
import com.ecommerce.dto.DisputeMessageDto;
import com.ecommerce.dto.DisputeResolveRequest;
import com.ecommerce.dto.PagedResponse;
import com.ecommerce.model.Dispute;
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
 * Admin/support dispute queue: full conversation visibility (the thread the
 * buyer and seller built before escalating is forwarded as-is), the ability
 * to participate as STAFF, and the final release/refund verdict.
 *
 * Authorization is enforced by path matching in
 * {@link com.ecommerce.security.AccessRules} (/api/admin/** -> hasRole(ADMIN)).
 */
@RestController
@RequestMapping("/api/admin/disputes")
@RequiredArgsConstructor
@Tag(name = "Admin - Disputes", description = "Dispute resolution queue (admin)")
public class AdminDisputeController {

    private final DisputeService disputeService;
    private final UserService userService;

    @GetMapping
    @Operation(summary = "List disputes (paged, optional status filter - use ESCALATED for the support queue)")
    public Mono<PagedResponse<DisputeDto>> list(
            @RequestParam(required = false) Dispute.DisputeStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return disputeService.findAllForAdmin(status, page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Dispute detail")
    public Mono<DisputeDto> byId(@PathVariable UUID id) {
        return disputeService.findByIdForAdmin(id);
    }

    @GetMapping("/{id}/messages")
    @Operation(summary = "Full forwarded conversation incl. attachments, oldest first")
    public Flux<DisputeMessageDto> messages(@PathVariable UUID id) {
        return disputeService.getMessagesForAdmin(id);
    }

    @PostMapping(value = "/{id}/messages", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Reply in the thread as support staff")
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

    @PostMapping("/{id}/resolve")
    @Operation(summary = "Resolve: RELEASE to the seller, or REFUND the buyer (full or partial)")
    public Mono<DisputeDto> resolve(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id,
            @Valid @RequestBody DisputeResolveRequest request) {
        return userService.findUserEntityByEmail(userDetails.getUsername())
                .flatMap(user -> disputeService.resolve(id, user.getId(), request));
    }
}
