CREATE TABLE IF NOT EXISTS t_lightrag_community (
    id BIGINT PRIMARY KEY,
    canonical_book_id BIGINT NOT NULL,
    community_level VARCHAR(16) NOT NULL,
    chapter_start INTEGER NOT NULL,
    chapter_end INTEGER NOT NULL,
    summary TEXT NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    embedding_json TEXT NOT NULL,
    model_version VARCHAR(64) NOT NULL,
    indexed_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP,
    CONSTRAINT uk_lightrag_community UNIQUE (canonical_book_id, community_level, chapter_start, chapter_end)
);
CREATE INDEX IF NOT EXISTS idx_lightrag_community_read_boundary
    ON t_lightrag_community(canonical_book_id, chapter_end, deleted_at);
