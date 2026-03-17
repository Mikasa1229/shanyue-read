-- 善阅坊·互动服务 数据库初始化脚本
-- 数据库：db_interaction（PostgreSQL 16）

CREATE TABLE IF NOT EXISTS t_interaction (
  id           BIGINT     PRIMARY KEY,               -- 雪花算法 ID
  user_id      BIGINT     NOT NULL,                  -- 操作用户 ID
  target_id    BIGINT     NOT NULL,                  -- 目标对象 ID（小说/点评）
  target_type  SMALLINT   NOT NULL,                  -- 目标类型：1=小说 2=点评
  action       SMALLINT   NOT NULL,                  -- 动作：1=点赞 2=收藏
  created_at   TIMESTAMP  NOT NULL DEFAULT NOW()
);

-- 唯一键保证幂等（同一用户对同一目标的同种动作只能存在一条）
CREATE UNIQUE INDEX uk_t_interaction_unique ON t_interaction(user_id, target_id, target_type, action);
-- 按目标统计点赞/收藏数
CREATE INDEX idx_t_interaction_target ON t_interaction(target_id, target_type, action);

COMMENT ON TABLE  t_interaction             IS '点赞/收藏记录表';
COMMENT ON COLUMN t_interaction.target_type IS '目标类型：1=小说 2=点评';
COMMENT ON COLUMN t_interaction.action      IS '动作：1=点赞 2=收藏';
