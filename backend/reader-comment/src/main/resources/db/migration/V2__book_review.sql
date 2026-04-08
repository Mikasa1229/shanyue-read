-- 允许 novel_id 可为 null（支持书源书籍书评）
ALTER TABLE t_comment ALTER COLUMN novel_id DROP NOT NULL;
-- 添加书源书名字段（novel_id 为 null 时使用）
ALTER TABLE t_comment ADD COLUMN IF NOT EXISTS book_title VARCHAR(256);
