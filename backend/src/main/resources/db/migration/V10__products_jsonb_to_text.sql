-- R2DBC binding mismatch: products.images and products.attributes were
-- JSONB columns, but the Product entity carries them as a JSON-serialized
-- String. Every INSERT/UPDATE through the entity failed with a type
-- mismatch ("expression is of type character varying").
--
-- Same fix that was applied to orders.shipping_address / billing_address
-- in V4: drop the JSONB-only GIN index on attributes, retype both columns
-- to TEXT (we never query inside them anyway), and update the defaults so
-- new rows still come out as valid JSON.

DROP INDEX IF EXISTS idx_products_attributes;

ALTER TABLE products
    ALTER COLUMN images     TYPE TEXT USING images::text,
    ALTER COLUMN attributes TYPE TEXT USING attributes::text;

ALTER TABLE products
    ALTER COLUMN images     SET DEFAULT '[]',
    ALTER COLUMN attributes SET DEFAULT '{}';
