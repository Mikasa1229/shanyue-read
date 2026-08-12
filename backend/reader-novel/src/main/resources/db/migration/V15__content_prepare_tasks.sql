ALTER TABLE t_book_content_recovery_task
    ADD COLUMN IF NOT EXISTS task_type VARCHAR(24) NOT NULL DEFAULT 'RECOVERY',
    ADD COLUMN IF NOT EXISTS requester_user_id BIGINT,
    ADD COLUMN IF NOT EXISTS source_id BIGINT,
    ADD COLUMN IF NOT EXISTS source_book_url VARCHAR(2000);

ALTER TABLE t_book_content_recovery_task
    DROP CONSTRAINT IF EXISTS ck_book_content_recovery_task_type;

ALTER TABLE t_book_content_recovery_task
    ADD CONSTRAINT ck_book_content_recovery_task_type
        CHECK (task_type IN ('RECOVERY', 'PREFETCH'));

CREATE INDEX IF NOT EXISTS idx_book_content_recovery_task_requester_updated
    ON t_book_content_recovery_task(requester_user_id, updated_at DESC);
