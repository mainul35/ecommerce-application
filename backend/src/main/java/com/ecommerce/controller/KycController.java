package com.ecommerce.controller;

import com.ecommerce.dto.kyc.KycCaseDto;
import com.ecommerce.dto.kyc.KycDocumentDto;
import com.ecommerce.dto.kyc.SellerProfileDto;
import com.ecommerce.dto.kyc.SellerProfileRequest;
import com.ecommerce.model.KycDocument;
import com.ecommerce.service.UserService;
import com.ecommerce.service.kyc.KycService;
import com.ecommerce.service.kyc.KycStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Seller self-service e-KYC: profile, verification case, evidence uploads,
 * submission, and status polling. Evidence files are transient (purged on
 * decision or after 72h) and only ever streamed back to the case owner or
 * staff via the party-checked file endpoint below.
 */
@RestController
@RequestMapping("/api/kyc")
@RequiredArgsConstructor
@Tag(name = "Seller KYC", description = "Seller registration and identity verification")
public class KycController {

    private final KycService kycService;
    private final KycStorageService storageService;
    private final UserService userService;

    // ---- Profile ----

    @PutMapping("/profile")
    @Operation(summary = "Create or update my seller profile (personal info + manual address)")
    public Mono<SellerProfileDto> upsertProfile(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody SellerProfileRequest request) {
        return userService.findUserEntityByEmail(userDetails.getUsername())
                .flatMap(user -> kycService.upsertProfile(user.getId(), request));
    }

    @GetMapping("/profile")
    @Operation(summary = "My seller profile")
    public Mono<SellerProfileDto> getProfile(@AuthenticationPrincipal UserDetails userDetails) {
        return userService.findUserEntityByEmail(userDetails.getUsername())
                .flatMap(user -> kycService.getProfile(user.getId()));
    }

    // ---- Case lifecycle ----

    @PostMapping("/cases")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Start (or resume) my verification case")
    public Mono<KycCaseDto> openCase(@AuthenticationPrincipal UserDetails userDetails) {
        return userService.findUserEntityByEmail(userDetails.getUsername())
                .flatMap(user -> kycService.openCase(user.getId()))
                .map(KycCaseDto::forOwner);
    }

    @GetMapping("/cases/current")
    @Operation(summary = "My latest verification case (poll this for check progress)")
    public Mono<KycCaseDto> currentCase(@AuthenticationPrincipal UserDetails userDetails) {
        return userService.findUserEntityByEmail(userDetails.getUsername())
                .flatMap(user -> kycService.getCurrentCase(user.getId()))
                .map(KycCaseDto::forOwner);
    }

    @PostMapping(value = "/cases/{caseId}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Upload one evidence slot (re-upload replaces it). Selfies come from the in-browser camera.")
    public Mono<KycDocumentDto> uploadDocument(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID caseId,
            @RequestParam("docType") KycDocument.KycDocType docType,
            @RequestPart("file") Mono<FilePart> file) {
        return userService.findUserEntityByEmail(userDetails.getUsername())
                .flatMap(user -> file.flatMap(part ->
                        kycService.uploadDocument(user.getId(), caseId, docType, part)));
    }

    @PostMapping("/cases/{caseId}/submit")
    @Operation(summary = "Submit for verification - starts automated checks and the 72h retention clock")
    public Mono<KycCaseDto> submit(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID caseId) {
        return userService.findUserEntityByEmail(userDetails.getUsername())
                .flatMap(user -> kycService.submit(user.getId(), caseId))
                .map(KycCaseDto::forOwner);
    }

    // ---- Evidence viewing (party-checked) ----

    @GetMapping("/cases/{caseId}/documents/{documentId}/file")
    @Operation(summary = "Stream an evidence file (case owner or staff only)")
    public Mono<ResponseEntity<Flux<DataBuffer>>> documentFile(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID caseId,
            @PathVariable UUID documentId) {
        return userService.findUserEntityByEmail(userDetails.getUsername())
                .flatMap(user -> kycService.documentForViewer(user.getId(), caseId, documentId))
                .map(document -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(
                                document.getContentType() != null
                                        ? document.getContentType() : "application/octet-stream"))
                        .body(storageService.read(caseId, document.getFileName())));
    }
}
