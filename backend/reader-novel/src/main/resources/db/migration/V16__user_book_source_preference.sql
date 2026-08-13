-- A reader can hide an unreliable source for themselves without changing the shared source catalog.
-- Before per-user preferences existed, the reader-facing toggle incorrectly wrote this flag.
-- Restore those historical personal toggles to the shared catalog before using the new table.
UPDATE t_book_source SET enabled = TRUE WHERE enabled = FALSE;

CREATE TABLE IF NOT EXISTS t_user_book_source_preference (
    user_id BIGINT NOT NULL,
    source_id BIGINT NOT NULL REFERENCES t_book_source(id) ON DELETE CASCADE,
    disabled BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, source_id)
);

CREATE INDEX IF NOT EXISTS idx_user_book_source_preference_disabled
    ON t_user_book_source_preference(user_id, disabled) WHERE disabled = TRUE;

COMMENT ON TABLE t_user_book_source_preference IS '用户个人书源偏好；禁用不影响其他用户或共享书源状态';
