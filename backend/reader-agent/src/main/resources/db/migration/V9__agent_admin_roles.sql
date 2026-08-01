CREATE TABLE IF NOT EXISTS t_agent_admin_role (
    user_id BIGINT PRIMARY KEY,
    role_code VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_agent_admin_role_code ON t_agent_admin_role(role_code);
