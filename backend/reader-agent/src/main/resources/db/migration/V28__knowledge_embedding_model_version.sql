-- Keep the embedding provenance with every durable RAG record.  A model-version
-- change must re-index even when the chapter text itself has not changed.
ALTER TABLE t_knowledge_document
    ADD COLUMN IF NOT EXISTS embedding_model_version VARCHAR(128) NOT NULL DEFAULT 'hash-embedding-v1';

ALTER TABLE t_knowledge_chunk
    ADD COLUMN IF NOT EXISTS embedding_model_version VARCHAR(128) NOT NULL DEFAULT 'hash-embedding-v1';

CREATE INDEX IF NOT EXISTS idx_knowledge_document_embedding_version
    ON t_knowledge_document(canonical_book_id, chapter_index, embedding_model_version);
