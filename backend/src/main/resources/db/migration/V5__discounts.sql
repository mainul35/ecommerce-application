-- Phase 1 of discounts/coupons: product/category/sitewide discounts.
-- A discount applies a percentage or fixed amount off matching products.
-- Effective price is computed at read time (no denormalised columns to keep in sync).
--
-- Resolution rule when multiple active discounts apply to the same product:
-- the BEST one for the customer wins (highest absolute savings). Stacking
-- across discounts is intentionally not supported - keeps reasoning simple
-- and prevents accidentally giving away the store with overlapping campaigns.

CREATE TABLE discounts (
    id              UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    name            VARCHAR(255) NOT NULL,
    type            VARCHAR(20)  NOT NULL CHECK (type IN ('PERCENTAGE', 'FIXED')),
    value           DECIMAL(10,2) NOT NULL CHECK (value > 0),
    scope           VARCHAR(20)  NOT NULL CHECK (scope IN ('PRODUCT', 'CATEGORY', 'SITEWIDE')),
    -- Target is a product id when scope=PRODUCT, a category id when scope=CATEGORY, NULL when SITEWIDE.
    scope_target_id UUID         NULL,
    starts_at       TIMESTAMP    NULL,
    ends_at         TIMESTAMP    NULL,
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Sitewide must NOT have a target; product/category MUST have one.
    CONSTRAINT chk_discount_target CHECK (
        (scope = 'SITEWIDE' AND scope_target_id IS NULL)
        OR (scope IN ('PRODUCT', 'CATEGORY') AND scope_target_id IS NOT NULL)
    ),
    -- Percentage value must be in (0, 100]. Fixed is just > 0 (covered above).
    CONSTRAINT chk_discount_pct_range CHECK (type <> 'PERCENTAGE' OR value <= 100),
    -- If both timestamps set, ends must be after starts.
    CONSTRAINT chk_discount_window CHECK (
        starts_at IS NULL OR ends_at IS NULL OR ends_at > starts_at
    )
);

-- Hot-path index: "what active discounts are there right now?"
CREATE INDEX idx_discounts_active_window ON discounts(is_active, starts_at, ends_at);
CREATE INDEX idx_discounts_scope_target  ON discounts(scope, scope_target_id);

CREATE TRIGGER update_discounts_updated_at
    BEFORE UPDATE ON discounts
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
