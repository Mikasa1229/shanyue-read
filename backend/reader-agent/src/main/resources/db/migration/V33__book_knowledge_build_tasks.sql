CREATE TABLE IF NOT EXISTS t_book_knowledge_space (
    canonical_book_id BIGINT PRIMARY KEY,
    status VARCHAR(24) NOT NULL DEFAULT 'NOT_BUILT',
    is_public BOOLEAN NOT NULL DEFAULT TRUE,
    owner_user_id BIGINT,
    model_mode VARCHAR(16),
    model_config_id BIGINT,
    total_chapters INTEGER NOT NULL DEFAULT 0,
    completed_chapters INTEGER NOT NULL DEFAULT 0,
    estimated_input_tokens BIGINT NOT NULL DEFAULT 0,
    estimated_output_tokens BIGINT NOT NULL DEFAULT 0,
    estimated_credits INTEGER NOT NULL DEFAULT 0,
    failure_message VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS t_book_knowledge_build_task (
    id BIGINT PRIMARY KEY,
    canonical_book_id BIGINT NOT NULL,
    requester_user_id BIGINT NOT NULL,
    model_mode VARCHAR(16) NOT NULL,
    model_config_id BIGINT,
    is_public BOOLEAN NOT NULL DEFAULT TRUE,
    status VARCHAR(24) NOT NULL,
    total_chapters INTEGER NOT NULL DEFAULT 0,
    completed_chapters INTEGER NOT NULL DEFAULT 0,
    estimated_input_tokens BIGINT NOT NULL DEFAULT 0,
    estimated_output_tokens BIGINT NOT NULL DEFAULT 0,
    estimated_credits INTEGER NOT NULL DEFAULT 0,
    charged_credits INTEGER NOT NULL DEFAULT 0,
    message VARCHAR(512),
    error_message VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_book_knowledge_build_task_user_updated ON t_book_knowledge_build_task(requester_user_id, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_book_knowledge_build_task_book_updated ON t_book_knowledge_build_task(canonical_book_id, updated_at DESC);
