ALTER TABLE t_user
    ADD COLUMN IF NOT EXISTS exp_total BIGINT NOT NULL DEFAULT 0;

COMMENT ON COLUMN t_user.exp_total IS '累计经验值（用于平台等级）';
