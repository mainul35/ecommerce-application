package com.ecommerce.admin.controller;

import com.ecommerce.dto.PagedResponse;
import com.ecommerce.dto.ReturnRejectRequest;
import com.ecommerce.dto.ReturnRequestDto;
import com.ecommerce.model.ReturnRequest;
import com.ecommerce.service.ReturnService;
import com.ecommerce.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Admin decision surface for per-item returns. Approving executes the refund
 * (original gateway when possible, in-app wallet otherwise) out of the
 * seller's held escrow.
 *
 * Authorization is enforced by path matching in
 * {@link com.ecommerce.security.AccessRules} (/api/admin/** -> hasRole(ADMIN)).
 */
@RestController
@RequestMapping("/api/admin/returns")
@RequiredArgsConstructor
@Tag(name = "Admin - Returns", description = "Return request decisions (admin)")
public class AdminReturnController {

    private final ReturnService returnService;
    private final UserService userService;

    @GetMapping
    @Operation(summary = "List return requests (paged, optional status filter)")
    public Mono<PagedResponse<ReturnRequestDto>> list(
            @RequestParam(required = false) ReturnRequest.ReturnStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return returnService.findAllForAdmin(status, page, size);
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Approve: refund the buyer from the seller's held escrow")
    public Mono<ReturnRequestDto> approve(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id) {
        return userService.findUserEntityByEmail(userDetails.getUsername())
                .flatMap(user -> returnService.approve(user.getId(), id));
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "Reject the return request with a reason")
    public Mono<ReturnRequestDto> reject(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id,
            @Valid @RequestBody ReturnRejectRequest request) {
        return userService.findUserEntityByEmail(userDetails.getUsername())
                .flatMap(user -> returnService.reject(user.getId(), id, request.getReason()));
    }
}
