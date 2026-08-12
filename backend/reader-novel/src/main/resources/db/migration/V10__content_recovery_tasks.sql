CREATE TABLE IF NOT EXISTS t_book_content_recovery_task (
    id BIGINT PRIMARY KEY,
    canonical_book_id BIGINT NOT NULL REFERENCES t_canonical_book(id),
    start_chapter INTEGER NOT NULL,
    end_chapter INTEGER NOT NULL,
    status VARCHAR(24) NOT NULL,
    total_chapters INTEGER NOT NULL DEFAULT 0,
    completed_chapters INTEGER NOT NULL DEFAULT 0,
    failed_chapters INTEGER NOT NULL DEFAULT 0,
    error_message VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_book_content_recovery_range CHECK (start_chapter >= 0 AND end_chapter >= start_chapter)
);
CREATE INDEX IF NOT EXISTS idx_book_content_recovery_task_book_updated
    ON t_book_content_recovery_task(canonical_book_id, updated_at DESC);
