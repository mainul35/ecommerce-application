-- Seller self-registration with e-KYC: photo-ID + multi-angle selfie + utility
-- bill verification. Verification DOCUMENTS are transient (purged on decision
-- or after 72 hours); only the boolean outcome and normal profile data persist.
--
-- Numeric enum codes (NumericEnum convention - never renumber, only append):
--   seller_profiles.seller_type:     0 BUSINESS, 1 INDIVIDUAL
--   seller_profiles.id_document_type:0 NATIONAL_ID, 1 PASSPORT, 2 DRIVING_LICENSE
--   kyc_cases.status:                0 DRAFT, 1 SUBMITTED, 2 CHECKING,
--                                    3 IN_REVIEW, 4 APPROVED, 5 REJECTED, 6 EXPIRED
--   kyc_cases.face_verdict:          0 UNKNOWN, 1 MATCH, 2 NO_MATCH
--   kyc_documents.doc_type:          0 ID_FRONT, 1 ID_BACK, 2 SELFIE_FRONT,
--                                    3 SELFIE_LEFT, 4 SELFIE_RIGHT, 5 UTILITY_BILL

-- ============================================================
-- 1. Verification outcome on the account (the only durable KYC result)
-- ============================================================

ALTER TABLE users ADD COLUMN id_verified BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN id_verified_at TIMESTAMP;

-- ============================================================
-- 2. Seller profile: durable personal/business data (normal account data,
--    NOT purged - the transient verification evidence lives in kyc_*)
-- ============================================================

CREATE TABLE seller_profiles (
    id               UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id          UUID NOT NULL UNIQUE REFERENCES users(id),
    seller_type      SMALLINT NOT NULL,
    legal_name       VARCHAR(200) NOT NULL,
    date_of_birth    DATE,
    phone            VARCHAR(30),
    id_document_type SMALLINT NOT NULL,
    address_line1    VARCHAR(255) NOT NULL,
    address_line2    VARCHAR(255),
    city             VARCHAR(100) NOT NULL,
    state            VARCHAR(100),
    postal_code      VARCHAR(20),
    country_code     VARCHAR(2) NOT NULL,
    created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TRIGGER update_seller_profiles_updated_at
    BEFORE UPDATE ON seller_profiles
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ============================================================
-- 3. KYC cases: one verification attempt with its automated check signals.
--    Columns marked [purged] are derived from documents and are nulled
--    together with the document purge.
-- ============================================================

CREATE TABLE kyc_cases (
    id                     UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id                UUID NOT NULL REFERENCES users(id),
    status                 SMALLINT NOT NULL DEFAULT 0,
    -- automated check signals (0..1 scores; kept - they contain no raw PII)
    name_match_score       NUMERIC(4, 3),
    address_match_score    NUMERIC(4, 3),
    face_verdict           SMALLINT NOT NULL DEFAULT 0,
    id_document_ok         BOOLEAN,
    bill_document_ok       BOOLEAN,
    -- OCR extracts shown to reviewers [purged]
    extracted_id_text      TEXT,
    extracted_bill_text    TEXT,
    face_note              TEXT,
    submitted_at           TIMESTAMP,
    -- hard retention deadline: submitted_at + 72h
    expires_at             TIMESTAMP,
    auto_decided           BOOLEAN NOT NULL DEFAULT FALSE,
    decided_by_user_id     UUID REFERENCES users(id),
    decided_at             TIMESTAMP,
    rejection_reason       TEXT,
    documents_purged_at    TIMESTAMP,
    created_at             TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_kyc_cases_user_id ON kyc_cases(user_id);
CREATE INDEX idx_kyc_cases_status  ON kyc_cases(status);
-- Retention sweep: any case whose evidence is past the 72h deadline.
CREATE INDEX idx_kyc_cases_expires ON kyc_cases(expires_at) WHERE documents_purged_at IS NULL;
-- One in-flight case per user (DRAFT/SUBMITTED/CHECKING/IN_REVIEW).
CREATE UNIQUE INDEX uq_kyc_cases_active_per_user
    ON kyc_cases(user_id) WHERE status IN (0, 1, 2, 3);

CREATE TRIGGER update_kyc_cases_updated_at
    BEFORE UPDATE ON kyc_cases
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ============================================================
-- 4. KYC documents: transient evidence files. Rows are DELETED (and files
--    removed from disk) on decision or at the 72h deadline. Files live in
--    the private kyc storage dir - never under the public /uploads mapping.
-- ============================================================

CREATE TABLE kyc_documents (
    id           UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    case_id      UUID NOT NULL REFERENCES kyc_cases(id) ON DELETE CASCADE,
    doc_type     SMALLINT NOT NULL,
    file_name    VARCHAR(255) NOT NULL,
    content_type VARCHAR(100),
    size_bytes   BIGINT,
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- re-uploading a slot replaces the previous document
    CONSTRAINT uq_kyc_documents_case_slot UNIQUE (case_id, doc_type)
);

CREATE INDEX idx_kyc_documents_case_id ON kyc_documents(case_id);

CREATE TRIGGER update_kyc_documents_updated_at
    BEFORE UPDATE ON kyc_documents
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
