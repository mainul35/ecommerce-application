-- Currencies, regions, and per-product region restrictions.
--
-- Pricing model: products carry a single base price (in the base currency,
-- e.g. USD). Each enabled currency has an exchange_rate relative to the
-- base. The storefront converts on the fly for DISPLAY only - orders and
-- payments are always settled in the base currency. The "is_base" flag is
-- exclusive (partial unique index ensures at most one row has is_base=true).
--
-- Region availability: a product is available in a region if either no
-- product_regions rows exist for it (open/global) OR a row links it to that
-- region. Customers in a region with an explicit allow-list cannot see
-- products outside it - the catalog filters them out (see ProductService).

-- ISO 4217 currency codes are 3 characters.
CREATE TABLE currencies (
    code            VARCHAR(3)    PRIMARY KEY,
    name            VARCHAR(100)  NOT NULL,
    symbol          VARCHAR(10)   NOT NULL,
    -- Multiplier from base. base currency has rate 1.0; others express
    -- "1 unit of base = exchange_rate units of this currency".
    exchange_rate   DECIMAL(18,8) NOT NULL DEFAULT 1.0 CHECK (exchange_rate > 0),
    is_base         BOOLEAN       NOT NULL DEFAULT FALSE,
    is_active       BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Exactly one base currency at a time. Partial unique index over the
-- subset of rows where is_base is true.
CREATE UNIQUE INDEX uq_currencies_one_base ON currencies(is_base) WHERE is_base = TRUE;

CREATE TRIGGER update_currencies_updated_at
    BEFORE UPDATE ON currencies
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ISO 3166-1 alpha-2 country codes are 2 characters.
CREATE TABLE regions (
    id              UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    name            VARCHAR(100) NOT NULL,
    country_code    VARCHAR(2)   NOT NULL UNIQUE,
    currency_code   VARCHAR(3)   NOT NULL REFERENCES currencies(code),
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_regions_country_code ON regions(country_code);
CREATE INDEX idx_regions_currency_code ON regions(currency_code);

CREATE TRIGGER update_regions_updated_at
    BEFORE UPDATE ON regions
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Many-to-many link between products and the regions where they're sold.
-- Empty set for a product = available globally (not restricted).
CREATE TABLE product_regions (
    product_id  UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    region_id   UUID NOT NULL REFERENCES regions(id) ON DELETE CASCADE,
    PRIMARY KEY (product_id, region_id)
);

CREATE INDEX idx_product_regions_region_id ON product_regions(region_id);

-- Seed: USD as base + a handful of common currencies / regions so the
-- system is usable out of the box. Admins can edit / disable / extend.
INSERT INTO currencies (code, name, symbol, exchange_rate, is_base, is_active) VALUES
    ('USD', 'US Dollar',   '$',  1.00000000, TRUE,  TRUE),
    ('EUR', 'Euro',        '€',  0.92000000, FALSE, TRUE),
    ('GBP', 'British Pound', '£', 0.79000000, FALSE, TRUE),
    ('JPY', 'Japanese Yen', '¥', 152.00000000, FALSE, TRUE),
    ('CAD', 'Canadian Dollar', 'CA$', 1.36000000, FALSE, TRUE),
    ('AUD', 'Australian Dollar', 'A$', 1.51000000, FALSE, TRUE),
    ('INR', 'Indian Rupee', '₹',  83.50000000, FALSE, TRUE),
    ('BDT', 'Bangladeshi Taka', '৳', 110.00000000, FALSE, TRUE);

INSERT INTO regions (name, country_code, currency_code, is_active) VALUES
    ('United States',  'US', 'USD', TRUE),
    ('United Kingdom', 'GB', 'GBP', TRUE),
    ('Germany',        'DE', 'EUR', TRUE),
    ('France',         'FR', 'EUR', TRUE),
    ('Japan',          'JP', 'JPY', TRUE),
    ('Canada',         'CA', 'CAD', TRUE),
    ('Australia',      'AU', 'AUD', TRUE),
    ('India',          'IN', 'INR', TRUE),
    ('Bangladesh',     'BD', 'BDT', TRUE);
