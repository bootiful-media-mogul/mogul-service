CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE document
(
    id          serial primary key,
    source_type TEXT, -- e.g. 'podcast', 'pdf', 'blog'
    source_uri  TEXT, -- optional (e.g. s3://…)
    title       TEXT,
    created_at  TIMESTAMPTZ DEFAULT now(),
    raw_text    TEXT
);


CREATE TABLE document_chunk
(
    id          BIGSERIAL PRIMARY KEY,
    document_id BIGINT NOT NULL REFERENCES document (id) ON DELETE CASCADE,
    chunk_index INT    NOT NULL,
    text        TEXT   NOT NULL,
    tsv         TSVECTOR,
    embedding         VECTOR(1536),
    clean_text  TEXT   NOT NULL,
    tokens      TEXT[] NOT NULL DEFAULT ARRAY []::TEXT[]
);


CREATE OR REPLACE FUNCTION chunk_tsv_trigger()
    RETURNS TRIGGER AS
$$
BEGIN
    NEW.tsv := to_tsvector('english', NEW.text);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION chunk_clean_text_and_tokens_trigger()
    RETURNS TRIGGER AS
$$
BEGIN
    NEW.clean_text :=
            lower(regexp_replace(coalesce(NEW.text, ''), '[^a-zA-Z0-9 ]', ' ', 'g'));
    NEW.tokens := string_to_array(NEW.clean_text, ' ');
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER chunk_tsv_update
    BEFORE INSERT OR UPDATE
    ON document_chunk
    FOR EACH ROW
EXECUTE PROCEDURE chunk_tsv_trigger();

CREATE TRIGGER chunk_clean_text_and_tokens_update
    BEFORE INSERT OR UPDATE
    ON document_chunk
    FOR EACH ROW
EXECUTE PROCEDURE chunk_clean_text_and_tokens_trigger();

-- Full-text GIN index on tsv
CREATE INDEX idx_document_chunk_tsv ON document_chunk USING GIN (tsv);

-- Optional trigram search on literal text
CREATE INDEX idx_document_chunk_text_trgm ON document_chunk USING GIN (text gin_trgm_ops);

ALTER TABLE document
    ADD COLUMN metadata JSONB NOT NULL DEFAULT '{}';

CREATE INDEX idx_document_metadata ON document USING GIN (metadata);

