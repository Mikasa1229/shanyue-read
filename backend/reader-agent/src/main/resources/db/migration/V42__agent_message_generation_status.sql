ALTER TABLE t_agent_message
    ADD COLUMN IF NOT EXISTS generation_status VARCHAR(16) NOT NULL DEFAULT 'COMPLETED';

CREATE INDEX IF NOT EXISTS idx_agent_message_session_generation
    ON t_agent_message(session_id, generation_status);
