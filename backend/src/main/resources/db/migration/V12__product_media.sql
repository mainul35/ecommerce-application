-- Product media: uploaded photos and short videos for each product.
--
-- Files are stored on the local filesystem under {upload-dir}/products/{product_id}/
-- and served via the /uploads/** static resource handler.  The URL column stores
-- the path relative to the backend root (e.g. /uploads/products/{id}/abc.jpg)
-- so the frontend can prepend the backend origin to build the full src URL.
--
-- media_type is constrained to IMAGE or VIDEO; enforcement of allowed MIME types
-- and per-file size limits happens in ProductMediaService, not in the DB.

CREATE TABLE product_media (
    id            UUID          PRIMARY KEY DEFAULT uuid_generate_v4(),
    product_id    UUID          NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    -- UUID-based filename on disk to avoid collisions / path-traversal attacks.
    file_name     VARCHAR(500)  NOT NULL,
    -- Original browser filename, kept for display purposes only.
    original_name VARCHAR(500)  NOT NULL,
    media_type    VARCHAR(10)   NOT NULL CHECK (media_type IN ('IMAGE', 'VIDEO')),
    -- Path served by the backend, e.g. /uploads/products/{id}/uuid.jpg
    url           VARCHAR(500)  NOT NULL,
    content_type  VARCHAR(100),
    size_bytes    BIGINT,
    sort_order    INTEGER       NOT NULL DEFAULT 0,
    created_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_product_media_product_id ON product_media(product_id);

CREATE TRIGGER update_product_media_updated_at
    BEFORE UPDATE ON product_media
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
