CREATE TABLE IF NOT EXISTS t_knowledge_clue (
    id BIGINT PRIMARY KEY,
    canonical_book_id BIGINT NOT NULL,
    chapter_index INTEGER NOT NULL,
    signal VARCHAR(64),
    excerpt TEXT NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'OPEN',
    resolved_chapter INTEGER,
    resolution_evidence TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_knowledge_clue_source UNIQUE (canonical_book_id, content_hash)
);
CREATE INDEX IF NOT EXISTS idx_knowledge_clue_book_status
    ON t_knowledge_clue(canonical_book_id, chapter_index, status);
