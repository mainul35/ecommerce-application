-- Phase A1: stock reservations + payment-intent fields on orders.
--
-- Stock is now PROMISED (reserved) when an order is created and only
-- COMMITTED (decremented from products.stock) when payment confirms.
-- Reservations expire after `reservation.ttl-hours` (default 7 days).

-- Guard against negative stock at the database level.
ALTER TABLE products
    ADD CONSTRAINT chk_products_stock_nonneg CHECK (stock >= 0);

-- Store addresses as TEXT (JSON-serialized) instead of JSONB. We never query
-- inside the address blob, so JSONB just complicated the R2DBC binding without
-- any practical benefit.
ALTER TABLE orders
    ALTER COLUMN shipping_address TYPE TEXT USING shipping_address::text,
    ALTER COLUMN billing_address  TYPE TEXT USING billing_address::text;

-- New order columns: admin-on-behalf attribution, expiry, cancellation, payment.
ALTER TABLE orders
    ADD COLUMN placed_by_user_id    UUID REFERENCES users(id),
    ADD COLUMN expires_at           TIMESTAMP,
    ADD COLUMN cancelled_at         TIMESTAMP,
    ADD COLUMN cancellation_reason  TEXT,
    ADD COLUMN payment_intent_id    VARCHAR(255),
    ADD COLUMN paid_at              TIMESTAMP;

CREATE INDEX idx_orders_placed_by_user_id ON orders(placed_by_user_id);
CREATE INDEX idx_orders_expires_at         ON orders(expires_at) WHERE status = 'PENDING';

-- Reservations table: each row is a promise of N units of a product, tied to
-- an order, valid until expires_at. Available stock for any product is:
--   products.stock - SUM(stock_reservations.quantity WHERE expires_at > NOW())
CREATE TABLE stock_reservations (
    id          UUID      PRIMARY KEY DEFAULT uuid_generate_v4(),
    order_id    UUID      NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id  UUID      NOT NULL REFERENCES products(id),
    quantity    INTEGER   NOT NULL CHECK (quantity > 0),
    expires_at  TIMESTAMP NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_stock_reservations_expires_at ON stock_reservations(expires_at);
CREATE INDEX idx_stock_reservations_product_id ON stock_reservations(product_id);
CREATE INDEX idx_stock_reservations_order_id   ON stock_reservations(order_id);

CREATE TRIGGER update_stock_reservations_updated_at
    BEFORE UPDATE ON stock_reservations
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
