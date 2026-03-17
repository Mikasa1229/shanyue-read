-- 善阅坊·打卡服务 数据库初始化脚本
-- 数据库：db_checkin（PostgreSQL 16）

CREATE TABLE IF NOT EXISTS t_checkin (
  id            BIGINT    PRIMARY KEY,               -- 雪花算法 ID
  user_id       BIGINT    NOT NULL,                  -- 打卡用户 ID
  novel_id      BIGINT    NOT NULL,                  -- 打卡关联的小说 ID
  checkin_date  DATE      NOT NULL,                  -- 打卡日期
  note          TEXT,                                -- 打卡备注（可选）
  created_at    TIMESTAMP NOT NULL DEFAULT NOW()
);

-- 唯一键：同一用户同一小说每天只能打卡一次
CREATE UNIQUE INDEX uk_t_checkin_unique ON t_checkin(user_id, novel_id, checkin_date);
-- 按用户查打卡历史
CREATE INDEX idx_t_checkin_user_date ON t_checkin(user_id, checkin_date DESC);

COMMENT ON TABLE  t_checkin               IS '打卡记录表';
COMMENT ON COLUMN t_checkin.user_id       IS '打卡用户 ID';
COMMENT ON COLUMN t_checkin.novel_id      IS '打卡关联的小说 ID';
COMMENT ON COLUMN t_checkin.checkin_date  IS '打卡日期，与 user_id + novel_id 组成唯一键';
COMMENT ON COLUMN t_checkin.note          IS '打卡备注，可选';
