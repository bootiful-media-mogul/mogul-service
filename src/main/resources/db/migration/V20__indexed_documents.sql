-- the goal here is to support indexing arbitrary chunks of text.

-- one-time per DB
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS hstore;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE document
(
    id          BIGSERIAL PRIMARY KEY,
    source_type TEXT NOT NULL, -- e.g. 'podcast', 'pdf', 'blog'
    source_uri  TEXT,          -- optional (e.g. s3://…)
    title       TEXT,
    created_at  TIMESTAMPTZ DEFAULT now(),
    raw_text    TEXT
);



-- storage for openai embeddings: 1536 dims for text-embedding-3-small,
-- OR 3072 dims for text-embedding-3-large
CREATE TABLE document_chunk
(
    id          BIGSERIAL PRIMARY KEY,
    document_id BIGINT NOT NULL REFERENCES document (id) ON DELETE CASCADE,
    chunk_index INT    NOT NULL, -- 0,1,2,... in original order

    start_ms    INT,             -- optional: timestamp inside audio/video
    end_ms      INT,

    text        TEXT   NOT NULL,
    tsv         TSVECTOR,        -- full-text vector
    emb         VECTOR(1536)     -- or VECTOR(3072)
);

CREATE OR REPLACE FUNCTION chunk_tsv_trigger()
    RETURNS TRIGGER AS $$
BEGIN
    NEW.tsv := to_tsvector('english', NEW.text);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER chunk_tsv_update
    BEFORE INSERT OR UPDATE ON document_chunk
    FOR EACH ROW EXECUTE PROCEDURE chunk_tsv_trigger();


