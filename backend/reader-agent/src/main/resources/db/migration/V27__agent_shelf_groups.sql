CREATE TABLE IF NOT EXISTS t_agent_shelf_group (
    user_id BIGINT NOT NULL,
    canonical_book_id BIGINT NOT NULL,
    group_code VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, canonical_book_id)
);
