-- Region-specific payment gateway configuration.
-- Allows admins to override which gateways are shown per region.
-- When no rows exist for a region the gateway registry falls back to
-- country-code matching on the PaymentGateway beans.
CREATE TABLE IF NOT EXISTS region_payment_gateways (
    region_id   UUID         NOT NULL REFERENCES regions(id) ON DELETE CASCADE,
    gateway_id  VARCHAR(50)  NOT NULL,
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order  INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (region_id, gateway_id)
);
CREATE INDEX IF NOT EXISTS idx_rpg_region ON region_payment_gateways(region_id);
