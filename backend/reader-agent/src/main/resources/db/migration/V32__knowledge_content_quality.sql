ALTER TABLE t_knowledge_document ADD COLUMN IF NOT EXISTS source_content_hash VARCHAR(64);
ALTER TABLE t_knowledge_document ADD COLUMN IF NOT EXISTS canonical_content_hash VARCHAR(64);
ALTER TABLE t_knowledge_document ADD COLUMN IF NOT EXISTS semantic_fingerprint BIGINT;
ALTER TABLE t_knowledge_document ADD COLUMN IF NOT EXISTS content_quality_score NUMERIC(5,4);
ALTER TABLE t_knowledge_document ADD COLUMN IF NOT EXISTS normalization_version VARCHAR(64);
CREATE INDEX IF NOT EXISTS idx_knowledge_document_canonical_hash ON t_knowledge_document(canonical_book_id, chapter_index, canonical_content_hash);
