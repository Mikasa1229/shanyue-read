-- 为用户表新增个人简介字段
ALTER TABLE t_user ADD COLUMN IF NOT EXISTS bio VARCHAR(256);  -- 个人简介

COMMENT ON COLUMN t_user.bio IS '个人简介，最多 256 字符';
