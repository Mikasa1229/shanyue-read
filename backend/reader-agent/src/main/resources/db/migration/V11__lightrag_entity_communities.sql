ALTER TABLE t_lightrag_community ADD COLUMN IF NOT EXISTS entity_summary TEXT NOT NULL DEFAULT '';
ALTER TABLE t_lightrag_community ADD COLUMN IF NOT EXISTS community_key VARCHAR(128) NOT NULL DEFAULT 'chapter-window';
CREATE INDEX IF NOT EXISTS idx_lightrag_community_key ON t_lightrag_community(canonical_book_id, community_key);
