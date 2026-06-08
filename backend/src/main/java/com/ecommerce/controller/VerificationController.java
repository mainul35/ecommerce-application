package com.ecommerce.controller;

import com.ecommerce.dto.PhoneSendRequest;
import com.ecommerce.dto.PhoneVerifyRequest;
import com.ecommerce.dto.VerificationStatusDto;
import com.ecommerce.dto.VerifyEmailRequest;
import com.ecommerce.service.UserService;
import com.ecommerce.service.VerificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Mandatory account verification - email (real, via SMTP) and phone (dummy
 * OTP for now). Both must be verified before checkout / placing orders /
 * starting seller KYC (enforced in the respective services).
 *
 * verify-email is PUBLIC (the link is opened from the inbox, possibly without
 * a session); everything else is for the authenticated account.
 */
@RestController
@RequestMapping("/api/verification")
@RequiredArgsConstructor
@Tag(name = "Account verification", description = "Email and phone verification")
public class VerificationController {

    private final VerificationService verificationService;
    private final UserService userService;

    @GetMapping("/status")
    @Operation(summary = "My email/phone verification status")
    public Mono<VerificationStatusDto> status(@AuthenticationPrincipal UserDetails userDetails) {
        return userService.findUserEntityByEmail(userDetails.getUsername())
                .flatMap(user -> verificationService.status(user.getId()));
    }

    @PostMapping("/email/verify")
    @Operation(summary = "Confirm an email verification link token (public)")
    public Mono<Map<String, String>> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        return verificationService.verifyEmail(request.getToken())
                .thenReturn(Map.of("status", "verified"));
    }

    @PostMapping("/email/resend")
    @Operation(summary = "Resend the email verification link")
    public Mono<Map<String, String>> resendEmail(@AuthenticationPrincipal UserDetails userDetails) {
        return userService.findUserEntityByEmail(userDetails.getUsername())
                .flatMap(user -> verificationService.resendEmail(user.getId()))
                .thenReturn(Map.of("status", "sent"));
    }

    @PostMapping("/phone/send")
    @Operation(summary = "Save my phone number and send a verification code (dummy SMS)")
    public Mono<Map<String, String>> sendPhone(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody PhoneSendRequest request) {
        return userService.findUserEntityByEmail(userDetails.getUsername())
                .flatMap(user -> verificationService.sendPhoneOtp(user.getId(), request.getPhone()))
                .thenReturn(Map.of("status", "sent"));
    }

    @PostMapping("/phone/verify")
    @Operation(summary = "Verify my phone with the code")
    public Mono<Map<String, String>> verifyPhone(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody PhoneVerifyRequest request) {
        return userService.findUserEntityByEmail(userDetails.getUsername())
                .flatMap(user -> verificationService.verifyPhone(user.getId(), request.getCode()))
                .thenReturn(Map.of("status", "verified"));
    }
}
