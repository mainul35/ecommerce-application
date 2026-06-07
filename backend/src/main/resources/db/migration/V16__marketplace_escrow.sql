-- Marketplace escrow: held funds per (order, seller), dispute threads with
-- attachments, in-app wallets, and per-item partial returns.
--
-- Also converts status columns to SMALLINT numeric codes (system-wide enum
-- convention). Codes mirror the Java NumericEnum implementations and are a
-- persisted contract - never renumber, only append.
--
--   orders.status:          0 PENDING, 1 CONFIRMED, 2 PROCESSING, 3 SHIPPED,
--                           4 DELIVERED, 5 CANCELLED, 6 REFUNDED
--   orders.payment_status:  0 PENDING, 1 COMPLETED, 2 FAILED, 3 REFUNDED,
--                           4 PARTIALLY_REFUNDED

-- ============================================================
-- 1. orders.status / orders.payment_status -> SMALLINT codes
-- ============================================================

-- The V4 partial index references status = 'PENDING'; its predicate cannot be
-- auto-rewritten by ALTER TYPE, so drop and recreate it afterwards.
DROP INDEX IF EXISTS idx_orders_expires_at;

ALTER TABLE orders ALTER COLUMN status DROP DEFAULT;
ALTER TABLE orders ALTER COLUMN status TYPE SMALLINT USING (
    CASE status
        WHEN 'PENDING'    THEN 0
        WHEN 'CONFIRMED'  THEN 1
        WHEN 'PROCESSING' THEN 2
        WHEN 'SHIPPED'    THEN 3
        WHEN 'DELIVERED'  THEN 4
        WHEN 'CANCELLED'  THEN 5
        WHEN 'REFUNDED'   THEN 6
    END
);
ALTER TABLE orders ALTER COLUMN status SET DEFAULT 0;
ALTER TABLE orders ALTER COLUMN status SET NOT NULL;

ALTER TABLE orders ALTER COLUMN payment_status DROP DEFAULT;
ALTER TABLE orders ALTER COLUMN payment_status TYPE SMALLINT USING (
    CASE payment_status
        WHEN 'PENDING'   THEN 0
        WHEN 'COMPLETED' THEN 1
        WHEN 'FAILED'    THEN 2
        WHEN 'REFUNDED'  THEN 3
    END
);
ALTER TABLE orders ALTER COLUMN payment_status SET DEFAULT 0;
ALTER TABLE orders ALTER COLUMN payment_status SET NOT NULL;

-- Recreate the V4 partial index with the numeric PENDING code.
CREATE INDEX idx_orders_expires_at ON orders(expires_at) WHERE status = 0;

-- ============================================================
-- 2. Seller snapshot + returned units on order items
-- ============================================================

ALTER TABLE order_items ADD COLUMN seller_id UUID REFERENCES users(id);
ALTER TABLE order_items ADD COLUMN returned_quantity INTEGER NOT NULL DEFAULT 0;

-- Backfill historical items from the product's current vendor.
UPDATE order_items oi
SET seller_id = p.vendor_id
FROM products p
WHERE oi.product_id = p.id
  AND p.vendor_id IS NOT NULL;

CREATE INDEX idx_order_items_seller_id ON order_items(seller_id);

-- ============================================================
-- 3. Escrow transactions: one row per (order, seller) group
--    status: 0 HELD, 1 RELEASED, 2 DISPUTED, 3 REFUNDED
-- ============================================================

CREATE TABLE escrow_transactions (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    order_id        UUID NOT NULL REFERENCES orders(id),
    seller_id       UUID REFERENCES users(id),          -- NULL = platform-owned items
    amount          DECIMAL(10, 2) NOT NULL CHECK (amount >= 0),
    refunded_amount DECIMAL(10, 2) NOT NULL DEFAULT 0 CHECK (refunded_amount >= 0),
    currency_code   VARCHAR(3) NOT NULL DEFAULT 'USD',
    status          SMALLINT NOT NULL DEFAULT 0,
    gateway_id      VARCHAR(30),                        -- PaymentGateway that captured the funds
    payment_ref     VARCHAR(255),                       -- provider reference for refunds
    hold_until      TIMESTAMP,                          -- set when the order is DELIVERED
    released_at     TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_escrow_refund_within_amount CHECK (refunded_amount <= amount),
    -- NULLS NOT DISTINCT so two platform rows (seller_id NULL) for one order collide.
    CONSTRAINT uq_escrow_order_seller UNIQUE NULLS NOT DISTINCT (order_id, seller_id)
);

CREATE INDEX idx_escrow_order_id   ON escrow_transactions(order_id);
CREATE INDEX idx_escrow_seller_id  ON escrow_transactions(seller_id);
CREATE INDEX idx_escrow_status     ON escrow_transactions(status);
-- Auto-release scan: HELD rows whose protection window has ended.
CREATE INDEX idx_escrow_hold_until ON escrow_transactions(hold_until) WHERE status = 0;

CREATE TRIGGER update_escrow_transactions_updated_at
    BEFORE UPDATE ON escrow_transactions
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ============================================================
-- 4. Disputes + conversation messages + attachments
--    disputes.status: 0 OPEN, 1 ESCALATED, 2 RESOLVED_RELEASED,
--                     3 RESOLVED_REFUNDED, 4 WITHDRAWN
--    messages.author_role: 0 BUYER, 1 SELLER, 2 STAFF
--    attachments.attachment_type: 0 IMAGE, 1 VIDEO
-- ============================================================

CREATE TABLE disputes (
    id                    UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    escrow_transaction_id UUID NOT NULL REFERENCES escrow_transactions(id),
    order_id              UUID NOT NULL REFERENCES orders(id),
    order_item_id         UUID REFERENCES order_items(id),
    opened_by_user_id     UUID NOT NULL REFERENCES users(id),
    reason                TEXT NOT NULL,
    status                SMALLINT NOT NULL DEFAULT 0,
    escalated_at          TIMESTAMP,
    resolved_by_user_id   UUID REFERENCES users(id),
    resolution_note       TEXT,
    refund_amount         DECIMAL(10, 2),
    resolved_at           TIMESTAMP,
    created_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_disputes_escrow_tx ON disputes(escrow_transaction_id);
CREATE INDEX idx_disputes_order_id  ON disputes(order_id);
CREATE INDEX idx_disputes_status    ON disputes(status);
-- One ACTIVE (OPEN/ESCALATED) dispute per escrow transaction at a time.
CREATE UNIQUE INDEX uq_disputes_active_per_escrow
    ON disputes(escrow_transaction_id) WHERE status IN (0, 1);

CREATE TRIGGER update_disputes_updated_at
    BEFORE UPDATE ON disputes
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TABLE dispute_messages (
    id             UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    dispute_id     UUID NOT NULL REFERENCES disputes(id) ON DELETE CASCADE,
    sender_user_id UUID NOT NULL REFERENCES users(id),
    author_role    SMALLINT NOT NULL,
    body           TEXT,
    created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_dispute_messages_dispute_id ON dispute_messages(dispute_id, created_at);

CREATE TRIGGER update_dispute_messages_updated_at
    BEFORE UPDATE ON dispute_messages
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TABLE dispute_attachments (
    id                 UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    dispute_message_id UUID NOT NULL REFERENCES dispute_messages(id) ON DELETE CASCADE,
    file_name          VARCHAR(255) NOT NULL,
    original_name      VARCHAR(255),
    url                VARCHAR(500) NOT NULL,
    content_type       VARCHAR(100),
    attachment_type    SMALLINT NOT NULL,
    size_bytes         BIGINT,
    created_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_dispute_attachments_message_id ON dispute_attachments(dispute_message_id);

CREATE TRIGGER update_dispute_attachments_updated_at
    BEFORE UPDATE ON dispute_attachments
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ============================================================
-- 5. Wallets + journal
--    transactions.type: 0 CREDIT, 1 DEBIT
--    transactions.reference_type: 0 ESCROW_RELEASE, 1 DISPUTE_REFUND,
--                                 2 RETURN_REFUND, 3 ADJUSTMENT, 4 WITHDRAWAL
-- ============================================================

CREATE TABLE wallets (
    id            UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id       UUID NOT NULL UNIQUE REFERENCES users(id),
    balance       DECIMAL(12, 2) NOT NULL DEFAULT 0 CHECK (balance >= 0),
    currency_code VARCHAR(3) NOT NULL DEFAULT 'USD',
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TRIGGER update_wallets_updated_at
    BEFORE UPDATE ON wallets
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TABLE wallet_transactions (
    id             UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    wallet_id      UUID NOT NULL REFERENCES wallets(id),
    type           SMALLINT NOT NULL,
    amount         DECIMAL(12, 2) NOT NULL CHECK (amount > 0),
    balance_after  DECIMAL(12, 2) NOT NULL,
    reference_type SMALLINT NOT NULL,
    reference_id   UUID,
    description    VARCHAR(500),
    created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_wallet_transactions_wallet_id ON wallet_transactions(wallet_id, created_at DESC);

CREATE TRIGGER update_wallet_transactions_updated_at
    BEFORE UPDATE ON wallet_transactions
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ============================================================
-- 6. Per-item partial returns
--    status: 0 REQUESTED, 1 REFUNDED, 2 REJECTED, 3 CANCELLED
--    refund_destination: 0 GATEWAY, 1 WALLET
-- ============================================================

CREATE TABLE return_requests (
    id                    UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    order_id              UUID NOT NULL REFERENCES orders(id),
    order_item_id         UUID NOT NULL REFERENCES order_items(id),
    escrow_transaction_id UUID NOT NULL REFERENCES escrow_transactions(id),
    requested_by_user_id  UUID NOT NULL REFERENCES users(id),
    quantity              INTEGER NOT NULL CHECK (quantity > 0),
    reason                TEXT NOT NULL,
    status                SMALLINT NOT NULL DEFAULT 0,
    refund_amount         DECIMAL(10, 2) NOT NULL CHECK (refund_amount >= 0),
    refund_destination    SMALLINT,
    resolved_by_user_id   UUID REFERENCES users(id),
    resolved_at           TIMESTAMP,
    rejection_reason      TEXT,
    created_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_return_requests_order_id ON return_requests(order_id);
CREATE INDEX idx_return_requests_status   ON return_requests(status);
-- One open request per order item at a time.
CREATE UNIQUE INDEX uq_return_requests_open_per_item
    ON return_requests(order_item_id) WHERE status = 0;

CREATE TRIGGER update_return_requests_updated_at
    BEFORE UPDATE ON return_requests
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
