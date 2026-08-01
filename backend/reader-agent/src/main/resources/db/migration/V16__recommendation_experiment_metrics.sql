ALTER TABLE t_agent_recommendation_feedback
    ADD COLUMN IF NOT EXISTS experiment_variant VARCHAR(16) NOT NULL DEFAULT 'BASELINE';

CREATE TABLE IF NOT EXISTS t_recommendation_exposure (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    experiment_key VARCHAR(64) NOT NULL,
    experiment_variant VARCHAR(16) NOT NULL,
    recommendation_count INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_recommendation_exposure_experiment_time
    ON t_recommendation_exposure(experiment_key, experiment_variant, created_at DESC);
