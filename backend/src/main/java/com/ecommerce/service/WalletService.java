package com.ecommerce.service;

import com.ecommerce.dto.PagedResponse;
import com.ecommerce.dto.WalletDto;
import com.ecommerce.dto.WalletTransactionDto;
import com.ecommerce.exception.BadRequestException;
import com.ecommerce.model.Wallet;
import com.ecommerce.model.WalletTransaction;
import com.ecommerce.repository.WalletRepository;
import com.ecommerce.repository.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * In-app wallet: refund store for buyers (cash / one-time payment methods,
 * unavailable gateways) and earnings store for sellers (escrow releases).
 * Every balance change writes an immutable wallet_transactions journal row.
 */
@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository transactionRepository;

    /** Find the user's wallet, creating an empty one on first touch. */
    public Mono<Wallet> getOrCreate(UUID userId) {
        return walletRepository.findByUserId(userId)
                .switchIfEmpty(Mono.defer(() -> walletRepository.save(Wallet.builder()
                                .id(UUID.randomUUID())
                                .userId(userId)
                                .balance(BigDecimal.ZERO)
                                .currencyCode("USD")
                                .build())
                        // Concurrent first-touch: the unique(user_id) constraint wins -
                        // fall back to the row the other writer created.
                        .onErrorResume(DataIntegrityViolationException.class,
                                e -> walletRepository.findByUserId(userId))));
    }

    /** Credit {@code amount} to the user's wallet and journal the change. */
    @Transactional
    public Mono<Wallet> credit(UUID userId, BigDecimal amount,
                               WalletTransaction.ReferenceType referenceType,
                               UUID referenceId, String description) {
        if (amount == null || amount.signum() <= 0) {
            return Mono.error(new BadRequestException("Credit amount must be positive"));
        }
        return getOrCreate(userId)
                .flatMap(wallet -> {
                    wallet.setBalance(wallet.getBalance().add(amount));
                    return walletRepository.save(wallet);
                })
                .flatMap(wallet -> journal(wallet, WalletTransaction.TransactionType.CREDIT,
                        amount, referenceType, referenceId, description).thenReturn(wallet));
    }

    /** Debit {@code amount} from the user's wallet (e.g. future withdrawals). */
    @Transactional
    public Mono<Wallet> debit(UUID userId, BigDecimal amount,
                              WalletTransaction.ReferenceType referenceType,
                              UUID referenceId, String description) {
        if (amount == null || amount.signum() <= 0) {
            return Mono.error(new BadRequestException("Debit amount must be positive"));
        }
        return getOrCreate(userId)
                .flatMap(wallet -> {
                    if (wallet.getBalance().compareTo(amount) < 0) {
                        return Mono.error(new BadRequestException("Insufficient wallet balance"));
                    }
                    wallet.setBalance(wallet.getBalance().subtract(amount));
                    return walletRepository.save(wallet);
                })
                .flatMap(wallet -> journal(wallet, WalletTransaction.TransactionType.DEBIT,
                        amount, referenceType, referenceId, description).thenReturn(wallet));
    }

    public Mono<WalletDto> getWallet(UUID userId) {
        return getOrCreate(userId).map(this::toDto);
    }

    public Mono<PagedResponse<WalletTransactionDto>> getTransactions(UUID userId, int page, int size) {
        return getOrCreate(userId)
                .flatMap(wallet -> transactionRepository
                        .findByWalletIdOrderByCreatedAtDesc(wallet.getId(), PageRequest.of(page, size))
                        .map(this::toDto)
                        .collectList()
                        .zipWith(transactionRepository.countByWalletId(wallet.getId()))
                        .map(t -> PagedResponse.of(t.getT1(), page, size, t.getT2())));
    }

    private Mono<WalletTransaction> journal(Wallet wallet, WalletTransaction.TransactionType type,
                                            BigDecimal amount,
                                            WalletTransaction.ReferenceType referenceType,
                                            UUID referenceId, String description) {
        return transactionRepository.save(WalletTransaction.builder()
                .id(UUID.randomUUID())
                .walletId(wallet.getId())
                .type(type)
                .amount(amount)
                .balanceAfter(wallet.getBalance())
                .referenceType(referenceType)
                .referenceId(referenceId)
                .description(description)
                .build());
    }

    private WalletDto toDto(Wallet wallet) {
        return WalletDto.builder()
                .id(wallet.getId())
                .userId(wallet.getUserId())
                .balance(wallet.getBalance())
                .currencyCode(wallet.getCurrencyCode())
                .updatedAt(wallet.getUpdatedAt())
                .build();
    }

    private WalletTransactionDto toDto(WalletTransaction tx) {
        return WalletTransactionDto.builder()
                .id(tx.getId())
                .type(tx.getType())
                .amount(tx.getAmount())
                .balanceAfter(tx.getBalanceAfter())
                .referenceType(tx.getReferenceType())
                .referenceId(tx.getReferenceId())
                .description(tx.getDescription())
                .createdAt(tx.getCreatedAt())
                .build();
    }
}
