package com.ecommerce.service;

import com.ecommerce.config.VerificationProperties;
import com.ecommerce.dto.VerificationStatusDto;
import com.ecommerce.exception.BadRequestException;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.model.User;
import com.ecommerce.model.VerificationToken;
import com.ecommerce.model.VerificationToken.VerificationChannel;
import com.ecommerce.repository.UserRepository;
import com.ecommerce.repository.VerificationTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Email + phone account verification.
 *
 * Email is real (token e-mailed via {@link EmailService} -> Mailpit in dev).
 * Phone is a DUMMY OTP for now (real bulk SMS needs a paid subscription):
 * the OTP is logged rather than sent, and a configured fixed code always
 * passes - the flow is wired end-to-end so swapping in a real SMS provider
 * later is a one-method change.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VerificationService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final VerificationTokenRepository tokenRepository;
    private final EmailService emailService;
    private final VerificationProperties props;

    // ------------------------------------------------------------------
    // Email
    // ------------------------------------------------------------------

    /** Issue an email token and send the verification link. Used on register + resend. */
    @Transactional
    public Mono<Void> sendEmailVerification(User user) {
        if (Boolean.TRUE.equals(user.getEmailVerified())) {
            return Mono.error(new BadRequestException("Email is already verified"));
        }
        String token = UUID.randomUUID().toString().replace("-", "")
                + Long.toHexString(RANDOM.nextLong());
        return tokenRepository.save(VerificationToken.builder()
                        .id(UUID.randomUUID())
                        .userId(user.getId())
                        .channel(VerificationChannel.EMAIL)
                        .secret(token)
                        .expiresAt(LocalDateTime.now().plusHours(props.getEmailTtlHours()))
                        .build())
                .then(emailService.sendEmailVerification(user.getEmail(), token));
    }

    /** Resend for the authenticated user. */
    public Mono<Void> resendEmail(UUID userId) {
        return userRepository.findById(userId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("User not found")))
                .flatMap(this::sendEmailVerification);
    }

    /** Confirm an email link token (public - the user may not have a session). */
    @Transactional
    public Mono<Void> verifyEmail(String token) {
        return tokenRepository.findBySecret(token)
                .switchIfEmpty(Mono.error(new BadRequestException("Invalid or unknown verification link")))
                .flatMap(t -> {
                    if (t.getChannel() != VerificationChannel.EMAIL) {
                        return Mono.error(new BadRequestException("Invalid verification link"));
                    }
                    if (t.getConsumedAt() != null) {
                        return Mono.error(new BadRequestException("This link has already been used"));
                    }
                    if (t.getExpiresAt().isBefore(LocalDateTime.now())) {
                        return Mono.error(new BadRequestException("This link has expired - request a new one"));
                    }
                    t.setConsumedAt(LocalDateTime.now());
                    return tokenRepository.save(t)
                            .then(userRepository.findById(t.getUserId()))
                            .flatMap(user -> {
                                user.setEmailVerified(true);
                                return userRepository.save(user);
                            });
                })
                .then();
    }

    // ------------------------------------------------------------------
    // Phone (dummy)
    // ------------------------------------------------------------------

    /** Save the phone the user entered on the verify page and issue an OTP. */
    @Transactional
    public Mono<Void> sendPhoneOtp(UUID userId, String phone) {
        if (phone == null || phone.isBlank()) {
            return Mono.error(new BadRequestException("Phone number is required"));
        }
        String otp = String.format("%06d", RANDOM.nextInt(1_000_000));
        return userRepository.findById(userId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("User not found")))
                .flatMap(user -> {
                    user.setPhone(phone.trim());
                    user.setPhoneVerified(false); // a new number must be re-verified
                    return userRepository.save(user);
                })
                .then(tokenRepository.save(VerificationToken.builder()
                        .id(UUID.randomUUID())
                        .userId(userId)
                        .channel(VerificationChannel.PHONE)
                        .secret(otp)
                        .expiresAt(LocalDateTime.now().plusMinutes(props.getPhoneOtpTtlMinutes()))
                        .build()))
                .doOnSuccess(t -> {
                    if (props.isPhoneDummy()) {
                        // DUMMY: no SMS provider. Log so the dev/QA can read the code,
                        // and note the always-accepted fixed code.
                        log.info("[DUMMY SMS] OTP for user {} ({}): {}  (fixed code {} also accepted)",
                                userId, phone, otp, props.getDummyPhoneOtp());
                    }
                })
                .then();
    }

    /** Verify the phone OTP. While dummy, the configured fixed code also passes. */
    @Transactional
    public Mono<Void> verifyPhone(UUID userId, String code) {
        if (code == null || code.isBlank()) {
            return Mono.error(new BadRequestException("Verification code is required"));
        }
        String submitted = code.trim();
        if (props.isPhoneDummy() && submitted.equals(props.getDummyPhoneOtp())) {
            return markPhoneVerified(userId);
        }
        return tokenRepository
                .findFirstByUserIdAndChannelOrderByCreatedAtDesc(userId, VerificationChannel.PHONE)
                .switchIfEmpty(Mono.error(new BadRequestException("Request a code first")))
                .flatMap(t -> {
                    if (t.getConsumedAt() != null) {
                        return Mono.error(new BadRequestException("This code has already been used"));
                    }
                    if (t.getExpiresAt().isBefore(LocalDateTime.now())) {
                        return Mono.error(new BadRequestException("Code expired - request a new one"));
                    }
                    if (!t.getSecret().equals(submitted)) {
                        return Mono.error(new BadRequestException("Incorrect code"));
                    }
                    t.setConsumedAt(LocalDateTime.now());
                    return tokenRepository.save(t).then(markPhoneVerified(userId));
                });
    }

    private Mono<Void> markPhoneVerified(UUID userId) {
        return userRepository.findById(userId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("User not found")))
                .flatMap(user -> {
                    if (user.getPhone() == null || user.getPhone().isBlank()) {
                        return Mono.error(new BadRequestException("Add a phone number before verifying"));
                    }
                    user.setPhoneVerified(true);
                    return userRepository.save(user);
                })
                .then();
    }

    // ------------------------------------------------------------------
    // Status / gate
    // ------------------------------------------------------------------

    public Mono<VerificationStatusDto> status(UUID userId) {
        return userRepository.findById(userId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("User not found")))
                .map(user -> VerificationStatusDto.builder()
                        .emailVerified(Boolean.TRUE.equals(user.getEmailVerified()))
                        .phoneVerified(Boolean.TRUE.equals(user.getPhoneVerified()))
                        .email(user.getEmail())
                        .phone(user.getPhone())
                        .fullyVerified(isFullyVerified(user))
                        .build());
    }

    /** True once both contact channels are verified. */
    public static boolean isFullyVerified(User user) {
        return Boolean.TRUE.equals(user.getEmailVerified())
                && Boolean.TRUE.equals(user.getPhoneVerified());
    }
}
