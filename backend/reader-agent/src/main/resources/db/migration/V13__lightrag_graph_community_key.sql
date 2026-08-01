ALTER TABLE t_lightrag_community DROP CONSTRAINT IF EXISTS uk_lightrag_community;
CREATE UNIQUE INDEX IF NOT EXISTS uk_lightrag_community_keyed
    ON t_lightrag_community (canonical_book_id, community_level, chapter_start, chapter_end, community_key);
