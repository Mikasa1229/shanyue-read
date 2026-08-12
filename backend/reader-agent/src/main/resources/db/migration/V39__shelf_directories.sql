ALTER TABLE t_agent_shelf_group ADD COLUMN IF NOT EXISTS group_name VARCHAR(32);
UPDATE t_agent_shelf_group SET group_name = CASE group_code
    WHEN 'FOLLOWING' THEN '正在阅读' WHEN 'SHORT_SESSION' THEN '短篇作品'
    WHEN 'WEEKEND' THEN '周末书单' WHEN 'RESTART' THEN '准备重读'
    WHEN 'CLEANUP' THEN '待整理作品' ELSE '待整理作品' END
WHERE group_name IS NULL;
UPDATE t_agent_shelf_group SET group_code = 'DIRECTORY';
