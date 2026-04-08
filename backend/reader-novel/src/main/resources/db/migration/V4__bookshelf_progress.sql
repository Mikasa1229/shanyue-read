-- 书架进度字段：记录阅读章节序号和总章节数（用于进度显示）
ALTER TABLE t_bookshelf_book
    ADD COLUMN IF NOT EXISTS last_chapter_index INT  DEFAULT 0,
    ADD COLUMN IF NOT EXISTS total_chapters     INT  DEFAULT 0;
