package com.ecommerce.admin.controller;

import com.ecommerce.dto.PagedResponse;
import com.ecommerce.dto.kyc.KycCaseDto;
import com.ecommerce.dto.kyc.KycRejectRequest;
import com.ecommerce.model.KycCase;
import com.ecommerce.service.UserService;
import com.ecommerce.service.kyc.KycService;
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
 * Human review queue for KYC cases the automation could not auto-approve.
 * Reviewers see the automated signals plus the OCR extracts and evidence
 * images (streamed via the party-checked KycController file endpoint, which
 * admits staff). Approve flips users.id_verified; both verdicts purge the
 * evidence immediately.
 *
 * Authorization is enforced by path matching in
 * {@link com.ecommerce.security.AccessRules} (/api/admin/** -> hasRole(ADMIN)).
 */
@RestController
@RequestMapping("/api/admin/kyc")
@RequiredArgsConstructor
@Tag(name = "Admin - KYC", description = "Identity verification review queue (admin)")
public class AdminKycController {

    private final KycService kycService;
    private final UserService userService;

    @GetMapping
    @Operation(summary = "List KYC cases (paged; filter IN_REVIEW for the work queue, oldest first)")
    public Mono<PagedResponse<KycCaseDto>> list(
            @RequestParam(required = false) KycCase.KycStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return kycService.findAllForAdmin(status, page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Case detail with automated signals and OCR extracts")
    public Mono<KycCaseDto> byId(@PathVariable UUID id) {
        return kycService.findByIdForAdmin(id);
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Approve: marks the account id_verified and purges the evidence")
    public Mono<KycCaseDto> approve(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id) {
        return userService.findUserEntityByEmail(userDetails.getUsername())
                .flatMap(user -> kycService.approve(user.getId(), id));
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "Reject with a reason shown to the user; purges the evidence")
    public Mono<KycCaseDto> reject(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id,
            @Valid @RequestBody KycRejectRequest request) {
        return userService.findUserEntityByEmail(userDetails.getUsername())
                .flatMap(user -> kycService.reject(user.getId(), id, request.getReason()));
    }
}
