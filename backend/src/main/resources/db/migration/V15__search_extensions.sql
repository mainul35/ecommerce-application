-- Hybrid search foundation: pg_trgm (fuzzy keyword) + pgvector (semantic similarity).
--
-- pg_trgm  : character-trigram indexes on name/description — typo-tolerant keyword matching.
-- pgvector : 768-dim float vector column — multilingual semantic search via Ollama embeddings.
--
-- Default model: nomic-embed-text (768 dims). To switch to a 1024-dim model (e.g. mxbai-embed-large):
--   1. Drop idx_products_embedding_hnsw and the embedding column.
--   2. Add: ALTER TABLE products ADD COLUMN embedding vector(1024);
--   3. Recreate the HNSW index.
--   4. Update ollama.model and ollama.dimensions in application.yml.
--   5. Run POST /api/search/reindex to regenerate all embeddings.

CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS vector;

ALTER TABLE products ADD COLUMN IF NOT EXISTS embedding vector(768);

-- GIN trigram indexes: accelerate the % (similarity threshold) operator and similarity() calls.
CREATE INDEX IF NOT EXISTS idx_products_name_trgm
    ON products USING GIN (name gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_products_description_trgm
    ON products USING GIN (description gin_trgm_ops);

-- HNSW approximate nearest-neighbor index for cosine distance queries.
-- m=16, ef_construction=64 are well-tested defaults for catalogs up to ~500k products.
-- Higher ef_construction improves recall at the cost of a slower initial build.
CREATE INDEX IF NOT EXISTS idx_products_embedding_hnsw
    ON products USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);
