CREATE TABLE IF NOT EXISTS t_agent_evaluation_run (
    id BIGINT PRIMARY KEY,
    initiated_by BIGINT NOT NULL,
    suite_name VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    total_cases INTEGER NOT NULL,
    passed_cases INTEGER NOT NULL,
    result_json TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_agent_evaluation_run_created
    ON t_agent_evaluation_run(created_at DESC);
