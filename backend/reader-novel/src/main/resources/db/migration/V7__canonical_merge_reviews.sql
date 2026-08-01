CREATE TABLE IF NOT EXISTS t_canonical_merge_review (
    id BIGINT PRIMARY KEY,
    source_canonical_book_id BIGINT NOT NULL REFERENCES t_canonical_book(id),
    candidate_canonical_book_id BIGINT NOT NULL REFERENCES t_canonical_book(id),
    confidence NUMERIC(4,3) NOT NULL,
    reason VARCHAR(512),
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    reviewed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_canonical_merge_review UNIQUE (source_canonical_book_id, candidate_canonical_book_id)
);
CREATE INDEX IF NOT EXISTS idx_canonical_merge_review_status_created
    ON t_canonical_merge_review(status, created_at DESC);
