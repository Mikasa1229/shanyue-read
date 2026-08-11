ALTER TABLE t_book_knowledge_build_task
    ADD COLUMN IF NOT EXISTS current_stage VARCHAR(48),
    ADD COLUMN IF NOT EXISTS stage_completed_units INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS stage_total_units INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS overall_progress INTEGER NOT NULL DEFAULT 0;

COMMENT ON COLUMN t_book_knowledge_build_task.current_stage IS '当前真实构建阶段，而非仅章节抽取阶段';
COMMENT ON COLUMN t_book_knowledge_build_task.stage_completed_units IS '当前阶段已完成工作单元';
COMMENT ON COLUMN t_book_knowledge_build_task.stage_total_units IS '当前阶段工作单元总数';
COMMENT ON COLUMN t_book_knowledge_build_task.overall_progress IS '按构建阶段加权计算的整体进度，完成前不得为100';
