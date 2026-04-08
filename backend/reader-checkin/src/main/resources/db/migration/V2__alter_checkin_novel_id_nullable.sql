-- 打卡服务：允许不绑定具体小说的每日打卡
-- novel_id 改为可空，唯一约束改为 (user_id, checkin_date)

ALTER TABLE t_checkin ALTER COLUMN novel_id DROP NOT NULL;

COMMENT ON COLUMN t_checkin.novel_id IS '关联小说 ID（可选），为空表示全局每日打卡';

-- 删除旧唯一索引（含 novel_id，不适合全局打卡）
DROP INDEX IF EXISTS uk_t_checkin_unique;

-- 新唯一索引：每个用户每天只能打卡一次
CREATE UNIQUE INDEX uk_t_checkin_user_date ON t_checkin(user_id, checkin_date);
