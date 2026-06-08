-- Mandatory account verification: email (real, via SMTP) + phone (dummy OTP
-- for now - real bulk SMS needs a paid subscription). Both must be verified
-- before sensitive actions (checkout / placing orders / starting KYC).
--
-- verification_tokens.channel (NumericEnum - never renumber, only append):
--   0 EMAIL, 1 PHONE

-- Phone lives on the account (collected on the verify page, not at signup).
-- email_verified already exists from V1; add its phone counterpart.
ALTER TABLE users ADD COLUMN phone VARCHAR(30);
ALTER TABLE users ADD COLUMN phone_verified BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE verification_tokens (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id     UUID NOT NULL REFERENCES users(id),
    channel     SMALLINT NOT NULL,
    -- EMAIL: opaque link token; PHONE: short numeric OTP.
    secret      VARCHAR(255) NOT NULL,
    expires_at  TIMESTAMP NOT NULL,
    consumed_at TIMESTAMP,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Email verification resolves the user from the opaque token in the link.
CREATE INDEX idx_verification_tokens_secret ON verification_tokens(secret);
-- Phone OTP / resend resolves the latest active challenge for a user+channel.
CREATE INDEX idx_verification_tokens_user_channel
    ON verification_tokens(user_id, channel, created_at DESC);

CREATE TRIGGER update_verification_tokens_updated_at
    BEFORE UPDATE ON verification_tokens
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
