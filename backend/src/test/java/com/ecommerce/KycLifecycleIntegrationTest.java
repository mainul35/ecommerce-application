package com.ecommerce;

import com.ecommerce.dto.kyc.KycCaseDto;
import com.ecommerce.dto.kyc.SellerProfileRequest;
import com.ecommerce.model.KycCase;
import com.ecommerce.model.KycDocument;
import com.ecommerce.model.SellerProfile;
import com.ecommerce.model.User;
import com.ecommerce.repository.KycCaseRepository;
import com.ecommerce.repository.KycDocumentRepository;
import com.ecommerce.repository.UserRepository;
import com.ecommerce.service.kyc.KycService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import reactor.test.StepVerifier;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Seller e-KYC lifecycle against a REAL PostgreSQL database (same dedicated
 * instance as the escrow test - see EscrowLifecycleIntegrationTest javadoc).
 *
 * The OCR/vision engines are deliberately absent in the test environment, so
 * the automated pipeline must route submitted cases to IN_REVIEW (engine
 * unavailability is never treated as user fraud, and auto-approval requires
 * every signal green). The test then exercises the privacy contract:
 * admin approval flips users.id_verified and PURGES the evidence; the
 * retention sweep expires + purges cases that outlive the 72h deadline.
 */
@SpringBootTest(properties = {
        "spring.r2dbc.url=r2dbc:postgresql://localhost:${IT_DB_PORT:5433}/ecommerce",
        "spring.flyway.url=jdbc:postgresql://localhost:${IT_DB_PORT:5433}/ecommerce",
        "kyc.storage-dir=target/kyc-it-private",
        "kyc.ocr.tessdata-path=",                       // OCR unavailable on purpose
        "kyc.vision.timeout-seconds=5",
        "escrow.release-initial-delay-ms=3600000",
        "reservation.cleanup-initial-delay-ms=3600000",
        "kyc.purge-initial-delay-ms=3600000"
})
class KycLifecycleIntegrationTest {

    @Autowired private KycService kycService;
    @Autowired private KycCaseRepository caseRepository;
    @Autowired private KycDocumentRepository documentRepository;
    @Autowired private UserRepository userRepository;

    @Value("${kyc.storage-dir}")
    private String storageDir;

    @Test
    void submittedCase_fallsToReview_thenApprovalVerifiesAndPurges() throws Exception {
        User seller = saveUser("kyc-" + UUID.randomUUID() + "@test.local");
        User admin = saveUser("kyc-admin-" + UUID.randomUUID() + "@test.local");
        admin.setRole(User.UserRole.ADMIN);
        userRepository.save(admin).block();

        // Profile + draft case
        kycService.upsertProfile(seller.getId(), profileRequest()).block();
        KycCaseDto draft = kycService.openCase(seller.getId()).block();
        assertThat(draft).isNotNull();
        assertThat(draft.getStatus()).isEqualTo(KycCase.KycStatus.DRAFT);
        UUID caseId = draft.getId();

        // Evidence: tiny placeholder files in the private storage dir + slot rows
        seedDocuments(caseId);

        // Submit: retention clock starts, async checks kick off
        KycCaseDto submitted = kycService.submit(seller.getId(), caseId).block();
        assertThat(submitted).isNotNull();
        assertThat(submitted.getExpiresAt()).isAfter(LocalDateTime.now().plusHours(71));

        // Run the pipeline deterministically (the async run no-ops if we won the race,
        // and vice versa - runChecks only acts on SUBMITTED cases).
        kycService.runChecks(caseId).block();
        KycCase checked = awaitStatus(caseId, KycCase.KycStatus.IN_REVIEW);
        // OCR engine missing => document signals are UNKNOWN (null), never false.
        assertThat(checked.getIdDocumentOk()).isNull();
        assertThat(checked.getFaceVerdict()).isNotNull();

        // Admin approves from the review queue
        StepVerifier.create(kycService.approve(admin.getId(), caseId))
                .assertNext(dto -> {
                    assertThat(dto.getStatus()).isEqualTo(KycCase.KycStatus.APPROVED);
                    assertThat(dto.getDocumentsPurgedAt()).isNotNull();
                    assertThat(dto.getDocuments()).isEmpty();          // rows purged
                    assertThat(dto.getExtractedIdText()).isNull();     // PII fields nulled
                    assertThat(dto.getFaceNote()).isNull();
                })
                .verifyComplete();

        // The durable outcome: the boolean on the account
        StepVerifier.create(userRepository.findById(seller.getId()))
                .assertNext(u -> {
                    assertThat(u.getIdVerified()).isTrue();
                    assertThat(u.getIdVerifiedAt()).isNotNull();
                })
                .verifyComplete();

        // And the files are gone from disk
        assertThat(Files.exists(Paths.get(storageDir, caseId.toString()))).isFalse();
    }

    @Test
    void retentionSweep_expiresAndPurges_undecidedCasesPast72h() throws Exception {
        User seller = saveUser("kyc-exp-" + UUID.randomUUID() + "@test.local");
        kycService.upsertProfile(seller.getId(), profileRequest()).block();
        KycCaseDto draft = kycService.openCase(seller.getId()).block();
        UUID caseId = draft.getId();
        seedDocuments(caseId);

        // Simulate a case stuck IN_REVIEW past the deadline
        KycCase kycCase = caseRepository.findById(caseId).block();
        kycCase.setStatus(KycCase.KycStatus.IN_REVIEW);
        kycCase.setSubmittedAt(LocalDateTime.now().minusHours(73));
        kycCase.setExpiresAt(LocalDateTime.now().minusHours(1));
        caseRepository.save(kycCase).block();

        StepVerifier.create(kycService.purgeExpired())
                .assertNext(count -> assertThat(count).isGreaterThanOrEqualTo(1L))
                .verifyComplete();

        KycCase swept = caseRepository.findById(caseId).block();
        assertThat(swept.getStatus()).isEqualTo(KycCase.KycStatus.EXPIRED);
        assertThat(swept.getDocumentsPurgedAt()).isNotNull();
        assertThat(documentRepository.countByCaseId(caseId).block()).isZero();
        assertThat(Files.exists(Paths.get(storageDir, caseId.toString()))).isFalse();
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private User saveUser(String email) {
        return userRepository.save(User.builder()
                .id(UUID.randomUUID())
                .email(email)
                .password("{noop}irrelevant")
                .firstName("Kyc")
                .lastName("Tester")
                .role(User.UserRole.CUSTOMER)
                .isActive(true)
                .emailVerified(true)
                .phoneVerified(true)   // contact verification gates openCase()
                .idVerified(false)
                .build()).block();
    }

    private SellerProfileRequest profileRequest() {
        return SellerProfileRequest.builder()
                .sellerType(SellerProfile.SellerType.INDIVIDUAL)
                .legalName("Kyc Tester")
                .idDocumentType(SellerProfile.IdDocumentType.NATIONAL_ID)
                .addressLine1("12 Test Lane")
                .city("Dhaka")
                .postalCode("1207")
                .countryCode("BD")
                .build();
    }

    /**
     * Maps each evidence slot to its fixture image under
     * src/test/resources/kyc/. The ID and bill fixtures carry the same
     * legal name / address as {@link #profileRequest()}, so an OCR-enabled
     * run would actually exercise the name/address matchers.
     */
    private static final Map<KycDocument.KycDocType, String> FIXTURES = Map.of(
            KycDocument.KycDocType.ID_FRONT, "kyc/id-front.jpg",
            KycDocument.KycDocType.ID_BACK, "kyc/id-back.jpg",
            KycDocument.KycDocType.SELFIE_FRONT, "kyc/selfie-front.jpg",
            KycDocument.KycDocType.SELFIE_LEFT, "kyc/selfie-left.jpg",
            KycDocument.KycDocType.SELFIE_RIGHT, "kyc/selfie-right.jpg",
            KycDocument.KycDocType.UTILITY_BILL, "kyc/utility-bill.jpg");

    /** Copy each fixture image into the case's storage dir + create a slot row. */
    private void seedDocuments(UUID caseId) throws Exception {
        Path dir = Paths.get(storageDir, caseId.toString());
        Files.createDirectories(dir);
        for (Map.Entry<KycDocument.KycDocType, String> entry : FIXTURES.entrySet()) {
            byte[] bytes = readFixture(entry.getValue());
            String fileName = UUID.randomUUID() + ".jpg";
            Files.write(dir.resolve(fileName), bytes);
            documentRepository.save(KycDocument.builder()
                    .id(UUID.randomUUID())
                    .caseId(caseId)
                    .docType(entry.getKey())
                    .fileName(fileName)
                    .contentType("image/jpeg")
                    .sizeBytes((long) bytes.length)
                    .build()).block();
        }
    }

    private byte[] readFixture(String classpathLocation) throws Exception {
        try (var in = new ClassPathResource(classpathLocation).getInputStream()) {
            return in.readAllBytes();
        }
    }

    /** Poll until the async pipeline lands the case in the expected status. */
    private KycCase awaitStatus(UUID caseId, KycCase.KycStatus expected) throws InterruptedException {
        for (int i = 0; i < 60; i++) {
            KycCase current = caseRepository.findById(caseId).block();
            if (current != null && current.getStatus() == expected) {
                return current;
            }
            Thread.sleep(1000);
        }
        throw new AssertionError("Case " + caseId + " never reached " + expected);
    }
}
