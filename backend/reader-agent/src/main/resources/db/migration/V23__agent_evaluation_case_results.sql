CREATE TABLE IF NOT EXISTS t_agent_evaluation_case_result (
    id BIGINT PRIMARY KEY,
    run_id BIGINT NOT NULL REFERENCES t_agent_evaluation_run(id) ON DELETE CASCADE,
    case_id VARCHAR(96) NOT NULL,
    category VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL,
    score INTEGER NOT NULL,
    evidence_json TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_agent_evaluation_case_result
    ON t_agent_evaluation_case_result(run_id, case_id);
CREATE INDEX IF NOT EXISTS idx_agent_evaluation_case_run
    ON t_agent_evaluation_case_result(run_id);
