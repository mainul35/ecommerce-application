package com.ecommerce.admin.controller;

import com.ecommerce.dto.EscrowTransactionDto;
import com.ecommerce.dto.PagedResponse;
import com.ecommerce.model.EscrowTransaction;
import com.ecommerce.service.EscrowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Admin escrow surface. Authorization is enforced by path matching in
 * {@link com.ecommerce.security.AccessRules} (/api/admin/** -> hasRole(ADMIN)).
 *
 *   GET  /              list (paged, filter by status)
 *   POST /{id}/release  force-release a non-disputed row to the seller
 */
@RestController
@RequestMapping("/api/admin/escrow")
@RequiredArgsConstructor
@Tag(name = "Admin - Escrow", description = "Held marketplace funds (admin)")
public class AdminEscrowController {

    private final EscrowService escrowService;

    @GetMapping
    @Operation(summary = "List escrow transactions (paged, optional status filter)")
    public Mono<PagedResponse<EscrowTransactionDto>> list(
            @RequestParam(required = false) EscrowTransaction.EscrowStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return escrowService.findAllForAdmin(status, page, size);
    }

    @PostMapping("/{id}/release")
    @Operation(summary = "Force-release held funds to the seller (disputed rows must be resolved instead)")
    public Mono<EscrowTransactionDto> release(@PathVariable UUID id) {
        return escrowService.releaseById(id);
    }
}
