ALTER TABLE t_knowledge_index_job ADD COLUMN IF NOT EXISTS dedupe_key VARCHAR(128);
CREATE UNIQUE INDEX IF NOT EXISTS uk_knowledge_index_job_dedupe
    ON t_knowledge_index_job(dedupe_key);
CREATE INDEX IF NOT EXISTS idx_knowledge_index_job_status_updated
    ON t_knowledge_index_job(status, updated_at DESC);

CREATE TABLE IF NOT EXISTS t_agent_recommendation_feedback (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    canonical_book_id BIGINT NOT NULL,
    action VARCHAR(16) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_agent_recommendation_feedback UNIQUE (user_id, canonical_book_id)
);
CREATE INDEX IF NOT EXISTS idx_agent_recommendation_feedback_user
    ON t_agent_recommendation_feedback(user_id, updated_at DESC);
