
CREATE EXTENSION IF NOT EXISTS pg_trgm;


CREATE INDEX idx_chunk_text_trgm ON document_chunk USING GIN (text gin_trgm_ops);

