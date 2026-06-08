package com.ecommerce.service.kyc;

import com.ecommerce.config.KycProperties;
import com.ecommerce.dto.kyc.KycCaseDto;
import com.ecommerce.dto.kyc.KycDocumentDto;
import com.ecommerce.dto.kyc.SellerProfileDto;
import com.ecommerce.dto.kyc.SellerProfileRequest;
import com.ecommerce.dto.PagedResponse;
import com.ecommerce.exception.BadRequestException;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.exception.UnauthorizedException;
import com.ecommerce.model.KycCase;
import com.ecommerce.model.KycDocument;
import com.ecommerce.model.SellerProfile;
import com.ecommerce.model.User;
import com.ecommerce.repository.KycCaseRepository;
import com.ecommerce.repository.KycDocumentRepository;
import com.ecommerce.repository.SellerProfileRepository;
import com.ecommerce.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Seller e-KYC lifecycle.
 *
 * Privacy contract (user requirement): verification EVIDENCE is transient.
 * Documents (and the OCR text / face notes derived from them) are purged
 * when a case is decided, or at the 72h retention deadline - whichever
 * comes first. The durable outcome is users.id_verified plus the normal
 * seller profile data. A purged case keeps only its non-PII signal scores.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KycService {

    private final SellerProfileRepository profileRepository;
    private final KycCaseRepository caseRepository;
    private final KycDocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final KycStorageService storageService;
    private final KycCheckService checkService;
    private final KycProperties props;

    private static final String CASE_NOT_FOUND = "KYC case not found";

    // ------------------------------------------------------------------
    // Seller profile (durable personal data)
    // ------------------------------------------------------------------

    @Transactional
    public Mono<SellerProfileDto> upsertProfile(UUID userId, SellerProfileRequest request) {
        return profileRepository.findByUserId(userId)
                .flatMap(existing -> {
                    applyRequest(existing, request);
                    return profileRepository.save(existing);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    SellerProfile profile = SellerProfile.builder()
                            .id(UUID.randomUUID())
                            .userId(userId)
                            .build();
                    applyRequest(profile, request);
                    return profileRepository.save(profile);
                }))
                .map(this::toDto);
    }

    public Mono<SellerProfileDto> getProfile(UUID userId) {
        return profileRepository.findByUserId(userId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Seller profile not found")))
                .map(this::toDto);
    }

    private void applyRequest(SellerProfile profile, SellerProfileRequest request) {
        profile.setSellerType(request.getSellerType());
        profile.setLegalName(request.getLegalName());
        profile.setDateOfBirth(request.getDateOfBirth());
        profile.setPhone(request.getPhone());
        profile.setIdDocumentType(request.getIdDocumentType());
        profile.setAddressLine1(request.getAddressLine1());
        profile.setAddressLine2(request.getAddressLine2());
        profile.setCity(request.getCity());
        profile.setState(request.getState());
        profile.setPostalCode(request.getPostalCode());
        profile.setCountryCode(request.getCountryCode() != null
                ? request.getCountryCode().toUpperCase() : null);
    }

    // ------------------------------------------------------------------
    // Case lifecycle (buyer side)
    // ------------------------------------------------------------------

    /** Start (or resume) a verification case. Requires a profile; one active case per user. */
    @Transactional
    public Mono<KycCaseDto> openCase(UUID userId) {
        return userRepository.findById(userId)
                .flatMap(user -> {
                    if (Boolean.TRUE.equals(user.getIdVerified())) {
                        return Mono.error(new BadRequestException("Your identity is already verified"));
                    }
                    // Contact channels must be verified before identity verification.
                    if (!com.ecommerce.service.VerificationService.isFullyVerified(user)) {
                        return Mono.error(new com.ecommerce.exception.VerificationRequiredException(
                                "Verify your email and phone number before starting seller verification."));
                    }
                    return profileRepository.findByUserId(userId)
                            .switchIfEmpty(Mono.error(new BadRequestException(
                                    "Complete your seller profile before starting verification")));
                })
                .flatMap(profile -> caseRepository.findActiveByUserId(userId)
                        .switchIfEmpty(caseRepository.save(KycCase.builder()
                                .id(UUID.randomUUID())
                                .userId(userId)
                                .status(KycCase.KycStatus.DRAFT)
                                .faceVerdict(KycCase.FaceVerdict.UNKNOWN)
                                .autoDecided(false)
                                .build())))
                .flatMap(this::toDtoWithDocuments);
    }

    public Mono<KycCaseDto> getCurrentCase(UUID userId) {
        return caseRepository.findActiveByUserId(userId)
                .switchIfEmpty(caseRepository.findByUserIdOrderByCreatedAtDesc(userId).next())
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(CASE_NOT_FOUND)))
                .flatMap(this::toDtoWithDocuments);
    }

    /** Upload one evidence slot; re-uploading a slot replaces the previous file. */
    @Transactional
    public Mono<KycDocumentDto> uploadDocument(UUID userId, UUID caseId,
                                               KycDocument.KycDocType docType, FilePart file) {
        return ownedDraftCase(userId, caseId)
                .flatMap(kycCase -> storageService.store(caseId, file)
                        .flatMap(stored -> documentRepository.findByCaseIdAndDocType(caseId, docType)
                                .flatMap(previous -> storageService
                                        .delete(caseId, previous.getFileName())
                                        .then(documentRepository.delete(previous))
                                        .thenReturn(stored))
                                .defaultIfEmpty(stored)
                                .flatMap(s -> documentRepository.save(KycDocument.builder()
                                        .id(UUID.randomUUID())
                                        .caseId(caseId)
                                        .docType(docType)
                                        .fileName(s.fileName())
                                        .contentType(s.contentType())
                                        .sizeBytes(s.sizeBytes())
                                        .build()))))
                .map(this::toDto);
    }

    /**
     * Submit for verification: all required evidence present, status -> SUBMITTED,
     * the 72h retention clock starts, and the automated pipeline kicks off
     * asynchronously (vision checks can take minutes - the client polls status).
     */
    @Transactional
    public Mono<KycCaseDto> submit(UUID userId, UUID caseId) {
        return ownedDraftCase(userId, caseId)
                .flatMap(kycCase -> profileRepository.findByUserId(userId)
                        .switchIfEmpty(Mono.error(new BadRequestException("Seller profile missing")))
                        .flatMap(profile -> documentRepository.findByCaseId(caseId).collectList()
                                .flatMap(documents -> {
                                    validateRequiredDocuments(profile, documents);
                                    LocalDateTime now = LocalDateTime.now();
                                    kycCase.setStatus(KycCase.KycStatus.SUBMITTED);
                                    kycCase.setSubmittedAt(now);
                                    kycCase.setExpiresAt(now.plusHours(props.getRetentionHours()));
                                    return caseRepository.save(kycCase);
                                })))
                .doOnSuccess(saved -> runChecksAsync(saved.getId()))
                .flatMap(this::toDtoWithDocuments);
    }

    private void validateRequiredDocuments(SellerProfile profile, List<KycDocument> documents) {
        List<KycDocument.KycDocType> required = new java.util.ArrayList<>(List.of(
                KycDocument.KycDocType.ID_FRONT,
                KycDocument.KycDocType.SELFIE_FRONT,
                KycDocument.KycDocType.SELFIE_LEFT,
                KycDocument.KycDocType.SELFIE_RIGHT,
                KycDocument.KycDocType.UTILITY_BILL));
        // Passports have no separate back side.
        if (profile.getIdDocumentType() != SellerProfile.IdDocumentType.PASSPORT) {
            required.add(KycDocument.KycDocType.ID_BACK);
        }
        List<KycDocument.KycDocType> present = documents.stream()
                .map(KycDocument::getDocType).toList();
        List<KycDocument.KycDocType> missing = required.stream()
                .filter(type -> !present.contains(type)).toList();
        if (!missing.isEmpty()) {
            throw new BadRequestException("Missing required documents: " + missing);
        }
    }

    /**
     * Fire-and-forget like the scheduled jobs: this is background work spawned
     * after the submit response - there is no request pipeline to compose with,
     * and vision inference may take minutes.
     */
    private void runChecksAsync(UUID caseId) {
        runChecks(caseId).subscribe(
                kycCase -> log.info("KYC case {} checks finished: {}", caseId, kycCase.getStatus()),
                err -> log.error("KYC case {} checks failed", caseId, err));
    }

    /** Visible for tests: the full pipeline run + auto-decision. */
    @Transactional
    public Mono<KycCase> runChecks(UUID caseId) {
        return caseRepository.findById(caseId)
                .filter(c -> c.getStatus() == KycCase.KycStatus.SUBMITTED)
                .flatMap(kycCase -> {
                    kycCase.setStatus(KycCase.KycStatus.CHECKING);
                    return caseRepository.save(kycCase);
                })
                .flatMap(kycCase -> Mono.zip(
                                profileRepository.findByUserId(kycCase.getUserId()),
                                documentRepository.findByCaseId(caseId).collectList())
                        .flatMap(t -> checkService.runChecks(kycCase, t.getT1(), t.getT2()))
                        .flatMap(outcome -> outcome.autoApprovable()
                                ? approveInternal(outcome.kycCase(), null, true)
                                : moveToReview(outcome.kycCase())));
    }

    private Mono<KycCase> moveToReview(KycCase kycCase) {
        kycCase.setStatus(KycCase.KycStatus.IN_REVIEW);
        return caseRepository.save(kycCase);
    }

    // ------------------------------------------------------------------
    // Decisions
    // ------------------------------------------------------------------

    /** Admin approval from the review queue. */
    @Transactional
    public Mono<KycCaseDto> approve(UUID adminId, UUID caseId) {
        return reviewableCase(caseId)
                .flatMap(kycCase -> approveInternal(kycCase, adminId, false))
                .flatMap(this::toDtoWithDocuments);
    }

    @Transactional
    public Mono<KycCaseDto> reject(UUID adminId, UUID caseId, String reason) {
        return reviewableCase(caseId)
                .flatMap(kycCase -> {
                    kycCase.setStatus(KycCase.KycStatus.REJECTED);
                    kycCase.setDecidedByUserId(adminId);
                    kycCase.setAutoDecided(false);
                    kycCase.setDecidedAt(LocalDateTime.now());
                    kycCase.setRejectionReason(reason);
                    return caseRepository.save(kycCase).flatMap(this::purgeEvidence);
                })
                .flatMap(this::toDtoWithDocuments);
    }

    private Mono<KycCase> reviewableCase(UUID caseId) {
        return caseRepository.findById(caseId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(CASE_NOT_FOUND)))
                .flatMap(kycCase -> switch (kycCase.getStatus()) {
                    case IN_REVIEW, SUBMITTED, CHECKING -> Mono.just(kycCase);
                    default -> Mono.error(new BadRequestException(
                            "Case is not awaiting a decision (status: " + kycCase.getStatus() + ")"));
                });
    }

    private Mono<KycCase> approveInternal(KycCase kycCase, UUID decidedBy, boolean auto) {
        kycCase.setStatus(KycCase.KycStatus.APPROVED);
        kycCase.setDecidedByUserId(decidedBy);
        kycCase.setAutoDecided(auto);
        kycCase.setDecidedAt(LocalDateTime.now());
        return caseRepository.save(kycCase)
                .flatMap(saved -> userRepository.findById(saved.getUserId())
                        .flatMap(user -> {
                            user.setIdVerified(true);
                            user.setIdVerifiedAt(LocalDateTime.now());
                            return userRepository.save(user);
                        })
                        .thenReturn(saved))
                .flatMap(this::purgeEvidence)
                .doOnSuccess(saved -> log.info("KYC case {} APPROVED ({})",
                        saved.getId(), auto ? "auto" : "by " + decidedBy));
    }

    // ------------------------------------------------------------------
    // Evidence purge (privacy contract)
    // ------------------------------------------------------------------

    /**
     * Delete the evidence: files on disk, document rows, and the
     * document-derived case fields (OCR extracts, face note). The signal
     * scores stay - they contain no raw PII and document the decision.
     */
    @Transactional
    public Mono<KycCase> purgeEvidence(KycCase kycCase) {
        if (kycCase.getDocumentsPurgedAt() != null) {
            return Mono.just(kycCase); // already purged
        }
        return storageService.deleteCaseDir(kycCase.getId())
                .then(documentRepository.deleteByCaseId(kycCase.getId()))
                .then(Mono.defer(() -> {
                    kycCase.setExtractedIdText(null);
                    kycCase.setExtractedBillText(null);
                    kycCase.setFaceNote(null);
                    kycCase.setDocumentsPurgedAt(LocalDateTime.now());
                    return caseRepository.save(kycCase);
                }));
    }

    /** Retention sweep body, called by the scheduled purge job. */
    @Transactional
    public Mono<Long> purgeExpired() {
        return caseRepository.findExpiredUnpurged(LocalDateTime.now())
                .concatMap(kycCase -> {
                    // Undecided cases that hit the deadline expire; the user must redo KYC.
                    if (kycCase.getStatus() == KycCase.KycStatus.SUBMITTED
                            || kycCase.getStatus() == KycCase.KycStatus.CHECKING
                            || kycCase.getStatus() == KycCase.KycStatus.IN_REVIEW) {
                        kycCase.setStatus(KycCase.KycStatus.EXPIRED);
                    }
                    return purgeEvidence(kycCase);
                })
                .count();
    }

    // ------------------------------------------------------------------
    // Admin queue / document access
    // ------------------------------------------------------------------

    public Mono<PagedResponse<KycCaseDto>> findAllForAdmin(KycCase.KycStatus status,
                                                           int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "submittedAt"));
        if (status != null) {
            return caseRepository.findByStatus(status, pageRequest)
                    .concatMap(this::toDtoWithDocuments)
                    .collectList()
                    .zipWith(caseRepository.countByStatus(status))
                    .map(t -> PagedResponse.of(t.getT1(), page, size, t.getT2()));
        }
        return caseRepository.findAll()
                .sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .skip((long) page * size)
                .take(size)
                .concatMap(this::toDtoWithDocuments)
                .collectList()
                .zipWith(caseRepository.count())
                .map(t -> PagedResponse.of(t.getT1(), page, size, t.getT2()));
    }

    public Mono<KycCaseDto> findByIdForAdmin(UUID caseId) {
        return caseRepository.findById(caseId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(CASE_NOT_FOUND)))
                .flatMap(this::toDtoWithDocuments);
    }

    /**
     * Resolve a document for streaming, enforcing the party check: the case
     * owner or staff (ADMIN/MANAGER) only.
     */
    public Mono<KycDocument> documentForViewer(UUID viewerId, UUID caseId, UUID documentId) {
        return caseRepository.findById(caseId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(CASE_NOT_FOUND)))
                .flatMap(kycCase -> {
                    if (viewerId.equals(kycCase.getUserId())) {
                        return Mono.just(kycCase);
                    }
                    return userRepository.findById(viewerId)
                            .filter(u -> u.getRole() == User.UserRole.ADMIN
                                    || u.getRole() == User.UserRole.MANAGER)
                            .map(u -> kycCase)
                            .switchIfEmpty(Mono.error(new UnauthorizedException(
                                    "Not authorized to view this document")));
                })
                .then(documentRepository.findById(documentId))
                .filter(doc -> caseId.equals(doc.getCaseId()))
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(
                        "Document not found (it may have been purged)")));
    }

    // ------------------------------------------------------------------
    // Helpers / mapping
    // ------------------------------------------------------------------

    private Mono<KycCase> ownedDraftCase(UUID userId, UUID caseId) {
        return caseRepository.findById(caseId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(CASE_NOT_FOUND)))
                .flatMap(kycCase -> {
                    if (!userId.equals(kycCase.getUserId())) {
                        return Mono.error(new UnauthorizedException("Not your verification case"));
                    }
                    if (kycCase.getStatus() != KycCase.KycStatus.DRAFT) {
                        return Mono.error(new BadRequestException(
                                "Case is no longer editable (status: " + kycCase.getStatus() + ")"));
                    }
                    return Mono.just(kycCase);
                });
    }

    private Mono<KycCaseDto> toDtoWithDocuments(KycCase kycCase) {
        return documentRepository.findByCaseId(kycCase.getId())
                .map(this::toDto)
                .collectList()
                .map(documents -> KycCaseDto.builder()
                        .id(kycCase.getId())
                        .userId(kycCase.getUserId())
                        .status(kycCase.getStatus())
                        .nameMatchScore(kycCase.getNameMatchScore())
                        .addressMatchScore(kycCase.getAddressMatchScore())
                        .faceVerdict(kycCase.getFaceVerdict())
                        .idDocumentOk(kycCase.getIdDocumentOk())
                        .billDocumentOk(kycCase.getBillDocumentOk())
                        .extractedIdText(kycCase.getExtractedIdText())
                        .extractedBillText(kycCase.getExtractedBillText())
                        .faceNote(kycCase.getFaceNote())
                        .submittedAt(kycCase.getSubmittedAt())
                        .expiresAt(kycCase.getExpiresAt())
                        .autoDecided(kycCase.getAutoDecided())
                        .decidedAt(kycCase.getDecidedAt())
                        .rejectionReason(kycCase.getRejectionReason())
                        .documentsPurgedAt(kycCase.getDocumentsPurgedAt())
                        .documents(documents)
                        .createdAt(kycCase.getCreatedAt())
                        .build());
    }

    private KycDocumentDto toDto(KycDocument document) {
        return KycDocumentDto.builder()
                .id(document.getId())
                .caseId(document.getCaseId())
                .docType(document.getDocType())
                .contentType(document.getContentType())
                .sizeBytes(document.getSizeBytes())
                .createdAt(document.getCreatedAt())
                .build();
    }

    private SellerProfileDto toDto(SellerProfile profile) {
        return SellerProfileDto.builder()
                .id(profile.getId())
                .userId(profile.getUserId())
                .sellerType(profile.getSellerType())
                .legalName(profile.getLegalName())
                .dateOfBirth(profile.getDateOfBirth())
                .phone(profile.getPhone())
                .idDocumentType(profile.getIdDocumentType())
                .addressLine1(profile.getAddressLine1())
                .addressLine2(profile.getAddressLine2())
                .city(profile.getCity())
                .state(profile.getState())
                .postalCode(profile.getPostalCode())
                .countryCode(profile.getCountryCode())
                .build();
    }
}
