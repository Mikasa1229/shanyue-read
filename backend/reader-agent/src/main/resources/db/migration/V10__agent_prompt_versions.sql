CREATE TABLE IF NOT EXISTS t_agent_prompt_version (
    id BIGINT PRIMARY KEY,
    prompt_key VARCHAR(64) NOT NULL,
    version_no INTEGER NOT NULL,
    content TEXT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_agent_prompt_version UNIQUE(prompt_key, version_no)
);
CREATE INDEX IF NOT EXISTS idx_agent_prompt_version_active ON t_agent_prompt_version(prompt_key, active);
