CREATE TABLE IF NOT EXISTS t_agent_model_pricing (
    id BIGINT PRIMARY KEY,
    provider VARCHAR(64) NOT NULL,
    model VARCHAR(128) NOT NULL,
    input_cost_micros_per_thousand BIGINT NOT NULL,
    output_cost_micros_per_thousand BIGINT NOT NULL,
    pricing_version VARCHAR(64) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    updated_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_agent_model_pricing UNIQUE (provider, model)
);
CREATE INDEX IF NOT EXISTS idx_agent_model_pricing_enabled
    ON t_agent_model_pricing(provider, model, enabled);
