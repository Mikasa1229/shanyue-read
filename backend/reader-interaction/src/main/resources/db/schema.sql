-- 善阅坊·互动服务 数据库初始化脚本
-- 数据库：db_interaction（PostgreSQL 16）

CREATE TABLE IF NOT EXISTS t_interaction
(
    id          BIGINT      NOT NULL PRIMARY KEY,
    user_id     BIGINT      NOT NULL,
    target_id   BIGINT      NOT NULL,
    target_type SMALLINT    NOT NULL,              -- 1=小说 2=点评
    action      SMALLINT    NOT NULL,              -- 1=点赞 2=收藏
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_user_target_action
        UNIQUE (user_id, target_id, target_type, action)  -- 唯一键保证幂等
);

-- 按目标统计点赞/收藏数（高频）
CREATE INDEX IF NOT EXISTS idx_interaction_target
    ON t_interaction (target_id, target_type, action);

-- 按用户查自己的收藏列表
CREATE INDEX IF NOT EXISTS idx_interaction_user_fav
    ON t_interaction (user_id, action, created_at DESC)
    WHERE action = 2;

COMMENT ON TABLE  t_interaction             IS '点赞/收藏记录表';
COMMENT ON COLUMN t_interaction.target_type IS '1=小说 2=点评';
COMMENT ON COLUMN t_interaction.action      IS '1=点赞 2=收藏';
