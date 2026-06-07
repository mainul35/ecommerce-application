package com.ecommerce.controller;

import com.ecommerce.dto.PagedResponse;
import com.ecommerce.dto.WalletDto;
import com.ecommerce.dto.WalletTransactionDto;
import com.ecommerce.service.UserService;
import com.ecommerce.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * The caller's in-app wallet: refund store for buyers, earnings store for
 * sellers. Read-only for now - spending/withdrawal flows come later.
 */
@RestController
@RequestMapping("/api/wallet")
@RequiredArgsConstructor
@Tag(name = "Wallet", description = "In-app wallet balance and ledger")
public class WalletController {

    private final WalletService walletService;
    private final UserService userService;

    @GetMapping
    @Operation(summary = "My wallet balance")
    public Mono<WalletDto> myWallet(@AuthenticationPrincipal UserDetails userDetails) {
        return userService.findUserEntityByEmail(userDetails.getUsername())
                .flatMap(user -> walletService.getWallet(user.getId()));
    }

    @GetMapping("/transactions")
    @Operation(summary = "My wallet ledger, newest first")
    public Mono<PagedResponse<WalletTransactionDto>> myTransactions(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return userService.findUserEntityByEmail(userDetails.getUsername())
                .flatMap(user -> walletService.getTransactions(user.getId(), page, size));
    }
}
