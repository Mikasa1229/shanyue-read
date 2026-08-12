CREATE TABLE IF NOT EXISTS t_canonical_book (
    id BIGINT PRIMARY KEY,
    normalized_title VARCHAR(256) NOT NULL,
    normalized_author VARCHAR(128),
    title VARCHAR(256) NOT NULL,
    author VARCHAR(128),
    cover_url VARCHAR(512),
    summary TEXT,
    merge_confidence NUMERIC(4,3) NOT NULL DEFAULT 1.000,
    merge_status VARCHAR(16) NOT NULL DEFAULT 'VERIFIED',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_canonical_book_identity ON t_canonical_book(normalized_title, normalized_author);

CREATE TABLE IF NOT EXISTS t_book_source_mapping (
    id BIGINT PRIMARY KEY,
    canonical_book_id BIGINT NOT NULL REFERENCES t_canonical_book(id),
    source_id BIGINT NOT NULL,
    source_book_url VARCHAR(512) NOT NULL,
    source_title VARCHAR(256),
    source_author VARCHAR(128),
    content_version VARCHAR(64),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_book_source_mapping UNIQUE (source_id, source_book_url)
);
CREATE INDEX IF NOT EXISTS idx_book_source_mapping_canonical ON t_book_source_mapping(canonical_book_id);

CREATE TABLE IF NOT EXISTS t_book_content_version (
    id BIGINT PRIMARY KEY,
    canonical_book_id BIGINT NOT NULL REFERENCES t_canonical_book(id),
    source_id BIGINT,
    chapter_index INTEGER NOT NULL,
    chapter_url VARCHAR(512),
    content_hash VARCHAR(64) NOT NULL,
    fetched_at TIMESTAMP NOT NULL DEFAULT NOW(),
    index_status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    CONSTRAINT uk_book_content_version UNIQUE (canonical_book_id, chapter_index, content_hash)
);

ALTER TABLE t_bookshelf_book ADD COLUMN IF NOT EXISTS canonical_book_id BIGINT;
ALTER TABLE t_favorite_book ADD COLUMN IF NOT EXISTS canonical_book_id BIGINT;
CREATE INDEX IF NOT EXISTS idx_bookshelf_canonical_book ON t_bookshelf_book(canonical_book_id);
CREATE INDEX IF NOT EXISTS idx_favorite_canonical_book ON t_favorite_book(canonical_book_id);
