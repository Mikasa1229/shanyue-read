CREATE TABLE IF NOT EXISTS t_user_credit_account (
    user_id BIGINT PRIMARY KEY,
    available_credits INTEGER NOT NULL DEFAULT 0 CHECK (available_credits >= 0),
    frozen_credits INTEGER NOT NULL DEFAULT 0 CHECK (frozen_credits >= 0),
    version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS t_user_credit_ledger (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    operation VARCHAR(32) NOT NULL,
    amount INTEGER NOT NULL,
    request_id VARCHAR(64) NOT NULL,
    reason VARCHAR(128),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_credit_ledger_request UNIQUE (request_id)
);
CREATE INDEX IF NOT EXISTS idx_credit_ledger_user_created ON t_user_credit_ledger(user_id, created_at DESC);
