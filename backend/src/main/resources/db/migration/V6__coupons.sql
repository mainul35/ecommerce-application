-- Phase 2 of discounts/coupons: customer-applied coupon codes at checkout.
--
-- Coupons are distinct from product/category discounts (see V5):
--   - Discounts auto-apply to matching products at read time
--   - Coupons require the customer to type a CODE at checkout, are validated
--     against time/min-order/usage limits, and apply to the SUBTOTAL after
--     item-level discounts are already in effect.
--
-- One coupon per order. Stacking on top of item discounts is intentional
-- (the coupon is a separate "thanks for the code" reward layer).

CREATE TABLE coupons (
    id                  UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    code                VARCHAR(50)  NOT NULL UNIQUE,
    name                VARCHAR(255) NULL,
    type                VARCHAR(20)  NOT NULL
        CHECK (type IN ('PERCENTAGE', 'FIXED', 'FREE_SHIPPING')),
    -- Required for PERCENTAGE and FIXED, must be NULL for FREE_SHIPPING.
    value               DECIMAL(10,2) NULL,
    min_order_amount    DECIMAL(10,2) NULL,
    max_uses            INTEGER       NULL,   -- null = unlimited
    max_uses_per_user   INTEGER       NULL,   -- null = unlimited
    valid_from          TIMESTAMP     NULL,
    valid_until         TIMESTAMP     NULL,
    is_active           BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- value semantics per type
    CONSTRAINT chk_coupon_value CHECK (
        (type = 'PERCENTAGE'    AND value IS NOT NULL AND value > 0 AND value <= 100)
     OR (type = 'FIXED'         AND value IS NOT NULL AND value > 0)
     OR (type = 'FREE_SHIPPING' AND value IS NULL)
    ),
    -- positive thresholds
    CONSTRAINT chk_coupon_limits CHECK (
        (max_uses          IS NULL OR max_uses          > 0)
     AND (max_uses_per_user IS NULL OR max_uses_per_user > 0)
     AND (min_order_amount IS NULL OR min_order_amount >= 0)
    ),
    -- if both window timestamps set, end must be after start
    CONSTRAINT chk_coupon_window CHECK (
        valid_from IS NULL OR valid_until IS NULL OR valid_until > valid_from
    )
);

CREATE INDEX idx_coupons_code_lower ON coupons(LOWER(code));
CREATE INDEX idx_coupons_active_window ON coupons(is_active, valid_from, valid_until);

CREATE TRIGGER update_coupons_updated_at
    BEFORE UPDATE ON coupons
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Redemption ledger. One row per (coupon, order). Used for usage caps and reporting.
-- created_at IS the redemption timestamp.
CREATE TABLE coupon_uses (
    id          UUID      PRIMARY KEY DEFAULT uuid_generate_v4(),
    coupon_id   UUID      NOT NULL REFERENCES coupons(id) ON DELETE CASCADE,
    user_id     UUID      NOT NULL REFERENCES users(id),
    order_id    UUID      NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- one redemption per (coupon, order) - rules out double-applying via webhook re-delivery
    CONSTRAINT uq_coupon_uses_coupon_order UNIQUE (coupon_id, order_id)
);

CREATE INDEX idx_coupon_uses_coupon       ON coupon_uses(coupon_id);
CREATE INDEX idx_coupon_uses_user_coupon  ON coupon_uses(user_id, coupon_id);

CREATE TRIGGER update_coupon_uses_updated_at
    BEFORE UPDATE ON coupon_uses
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Order columns to record the redeemed coupon and the price breakdown.
-- subtotal_amount = sum of (effective price after item discounts) * quantity
-- coupon_discount_amount = absolute saving from the coupon
-- total_amount  = subtotal - coupon_discount  (existing column; shipping not modelled yet)
ALTER TABLE orders
    ADD COLUMN coupon_code             VARCHAR(50)   NULL,
    ADD COLUMN coupon_discount_amount  DECIMAL(10,2) NULL,
    ADD COLUMN subtotal_amount         DECIMAL(10,2) NULL;
