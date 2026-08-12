ALTER TABLE t_comment
    ADD COLUMN IF NOT EXISTS activity_type VARCHAR(48);

COMMENT ON COLUMN t_comment.activity_type IS 'Structured square feed activity type; null means a normal review or reply';
