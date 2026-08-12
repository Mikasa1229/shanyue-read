ALTER TABLE t_book_knowledge_build_task
    ADD COLUMN IF NOT EXISTS start_chapter INTEGER,
    ADD COLUMN IF NOT EXISTS end_chapter INTEGER;

COMMENT ON COLUMN t_book_knowledge_build_task.start_chapter IS '知识图谱构建起始章节，用户输入的一基且包含边界';
COMMENT ON COLUMN t_book_knowledge_build_task.end_chapter IS '知识图谱构建结束章节，用户输入的一基且包含边界';
