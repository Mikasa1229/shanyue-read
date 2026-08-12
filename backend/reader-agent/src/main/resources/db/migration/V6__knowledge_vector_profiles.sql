CREATE TABLE IF NOT EXISTS t_knowledge_vector_profile (
    id BIGINT PRIMARY KEY,
    profile_type VARCHAR(32) NOT NULL,
    subject_id BIGINT NOT NULL,
    canonical_book_id BIGINT,
    content TEXT NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    embedding_json TEXT NOT NULL,
    model_version VARCHAR(64) NOT NULL,
    indexed_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP,
    CONSTRAINT uk_knowledge_vector_profile UNIQUE (profile_type, subject_id)
);
CREATE INDEX IF NOT EXISTS idx_knowledge_vector_profile_book
    ON t_knowledge_vector_profile(profile_type, canonical_book_id, deleted_at);
