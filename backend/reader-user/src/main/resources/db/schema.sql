-- 善阅坊·用户服务 数据库初始化脚本
-- 数据库：db_user（PostgreSQL 16）

-- 用户表
CREATE TABLE IF NOT EXISTS t_user
(
    id          BIGINT       NOT NULL PRIMARY KEY,           -- 雪花算法 ID（应用层生成）
    username    VARCHAR(32)  NOT NULL,                       -- 登录用户名
    password    VARCHAR(128) NOT NULL,                       -- BCrypt 加密密码
    nickname    VARCHAR(64)  NOT NULL,                       -- 显示昵称
    avatar      VARCHAR(256),                                -- 头像（MinIO 路径）
    status      SMALLINT     NOT NULL DEFAULT 1,             -- 1=正常 0=封禁
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted     BOOLEAN      NOT NULL DEFAULT FALSE,         -- 逻辑删除
    CONSTRAINT uk_username UNIQUE (username)
);

CREATE INDEX IF NOT EXISTS idx_user_username ON t_user (username);
CREATE INDEX IF NOT EXISTS idx_user_status   ON t_user (status) WHERE deleted = FALSE;

COMMENT ON TABLE  t_user            IS '用户表';
COMMENT ON COLUMN t_user.id         IS '雪花算法主键';
COMMENT ON COLUMN t_user.username   IS '登录用户名，全局唯一';
COMMENT ON COLUMN t_user.password   IS 'BCrypt 加密密码';
COMMENT ON COLUMN t_user.status     IS '1=正常 0=封禁';
COMMENT ON COLUMN t_user.deleted    IS '逻辑删除标志';
