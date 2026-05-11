-- Discount templates: reusable blueprints the admin can re-apply to
-- different products / categories / time windows. Templates are NOT live
-- discounts - they're saved configurations that the admin instantiates
-- into actual discounts (V5: discounts table) on demand.
--
-- Useful for recurring promotions: save "Holiday 25% Off" once, apply to
-- Smartphones in November, Laptops in December, sitewide on New Year's Eve.

CREATE TABLE discount_templates (
    id                     UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    name                   VARCHAR(255) NOT NULL,
    description            TEXT         NULL,
    type                   VARCHAR(20)  NOT NULL CHECK (type IN ('PERCENTAGE', 'FIXED')),
    value                  DECIMAL(10,2) NOT NULL CHECK (value > 0),
    -- Optional convenience: when set, "apply" pre-fills ends_at = now + N days.
    default_duration_days  INTEGER      NULL CHECK (default_duration_days IS NULL OR default_duration_days > 0),
    created_at             TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- PERCENTAGE templates must be in (0, 100]
    CONSTRAINT chk_template_pct_range CHECK (type <> 'PERCENTAGE' OR value <= 100)
);

CREATE INDEX idx_discount_templates_name ON discount_templates(LOWER(name));

CREATE TRIGGER update_discount_templates_updated_at
    BEFORE UPDATE ON discount_templates
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
