ALTER TABLE t_comment
    ADD COLUMN IF NOT EXISTS score SMALLINT,
    ADD COLUMN IF NOT EXISTS source_id BIGINT,
    ADD COLUMN IF NOT EXISTS book_url VARCHAR(1024),
    ADD COLUMN IF NOT EXISTS book_author VARCHAR(128),
    ADD COLUMN IF NOT EXISTS book_cover_url VARCHAR(1024),
    ADD COLUMN IF NOT EXISTS book_intro TEXT;

COMMENT ON COLUMN t_comment.score IS '评分（1-5，仅根点评）';
COMMENT ON COLUMN t_comment.source_id IS '书源ID（书源书籍点评使用）';
COMMENT ON COLUMN t_comment.book_url IS '书源书籍URL';
COMMENT ON COLUMN t_comment.book_author IS '书源作者';
COMMENT ON COLUMN t_comment.book_cover_url IS '书源封面URL';
COMMENT ON COLUMN t_comment.book_intro IS '书源简介';
