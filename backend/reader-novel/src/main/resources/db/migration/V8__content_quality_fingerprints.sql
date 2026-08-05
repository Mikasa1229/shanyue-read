ALTER TABLE t_book_content_version ADD COLUMN IF NOT EXISTS raw_content_hash VARCHAR(64);
ALTER TABLE t_book_content_version ADD COLUMN IF NOT EXISTS normalized_content_hash VARCHAR(64);
ALTER TABLE t_book_content_version ADD COLUMN IF NOT EXISTS semantic_fingerprint BIGINT;
ALTER TABLE t_book_content_version ADD COLUMN IF NOT EXISTS quality_score NUMERIC(5,4);
ALTER TABLE t_book_content_version ADD COLUMN IF NOT EXISTS normalization_version VARCHAR(64);
ALTER TABLE t_book_content_version ADD COLUMN IF NOT EXISTS reuse_decision VARCHAR(32);
ALTER TABLE t_book_content_version ADD COLUMN IF NOT EXISTS base_version_id BIGINT;
CREATE INDEX IF NOT EXISTS idx_book_content_normalized ON t_book_content_version(canonical_book_id, chapter_index, normalized_content_hash);
