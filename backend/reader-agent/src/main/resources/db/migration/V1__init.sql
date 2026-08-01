CREATE TABLE IF NOT EXISTS t_agent_session (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(128),
    context_json TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX IF NOT EXISTS idx_agent_session_user_updated ON t_agent_session(user_id, updated_at DESC);

CREATE TABLE IF NOT EXISTS t_agent_message (
    id BIGINT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    role VARCHAR(16) NOT NULL,
    content TEXT NOT NULL,
    citations_json TEXT,
    tool_trace_json TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX IF NOT EXISTS idx_agent_message_session_created ON t_agent_message(session_id, created_at);

CREATE TABLE IF NOT EXISTS t_user_model_config (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    provider VARCHAR(32) NOT NULL,
    model VARCHAR(128) NOT NULL,
    encrypted_api_key TEXT NOT NULL,
    key_hint VARCHAR(16) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_agent_user_provider_model UNIQUE (user_id, provider, model)
);

CREATE TABLE IF NOT EXISTS t_model_usage (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    session_id BIGINT,
    provider VARCHAR(32) NOT NULL,
    model VARCHAR(128) NOT NULL,
    access_mode VARCHAR(16) NOT NULL,
    input_tokens INTEGER NOT NULL DEFAULT 0,
    output_tokens INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL,
    request_id VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_model_usage_request UNIQUE (request_id)
);

CREATE TABLE IF NOT EXISTS t_knowledge_document (
    id BIGINT PRIMARY KEY,
    canonical_book_id BIGINT NOT NULL,
    source_id BIGINT,
    chapter_index INTEGER,
    content_hash VARCHAR(64) NOT NULL,
    content_version VARCHAR(64) NOT NULL,
    index_status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_knowledge_document_version UNIQUE (canonical_book_id, chapter_index, content_hash)
);

CREATE TABLE IF NOT EXISTS t_knowledge_index_job (
    id BIGINT PRIMARY KEY,
    canonical_book_id BIGINT NOT NULL,
    job_type VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    payload_json TEXT,
    retry_count INTEGER NOT NULL DEFAULT 0,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
