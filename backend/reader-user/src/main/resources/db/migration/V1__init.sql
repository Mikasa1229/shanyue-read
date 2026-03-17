-- 善阅坊·用户服务 数据库初始化脚本
-- 数据库：db_user（PostgreSQL 16）

CREATE TABLE IF NOT EXISTS t_user (
  id          BIGINT        PRIMARY KEY,              -- 雪花算法 ID（应用层生成）
  username    VARCHAR(64)   NOT NULL,                 -- 登录用户名，全局唯一
  password    VARCHAR(255)  NOT NULL,                 -- BCrypt 加密密码
  nickname    VARCHAR(64),                            -- 显示昵称
  avatar      VARCHAR(512),                           -- 头像地址（MinIO 路径）
  status      SMALLINT      NOT NULL DEFAULT 1,       -- 1=正常 0=封禁
  created_at  TIMESTAMP     NOT NULL DEFAULT NOW(),
  updated_at  TIMESTAMP     NOT NULL DEFAULT NOW(),
  deleted     BOOLEAN       NOT NULL DEFAULT FALSE    -- 逻辑删除标志
);

CREATE UNIQUE INDEX uk_t_user_username ON t_user(username) WHERE deleted = FALSE;

COMMENT ON TABLE  t_user             IS '用户表';
COMMENT ON COLUMN t_user.id          IS '雪花算法主键';
COMMENT ON COLUMN t_user.username    IS '登录用户名，全局唯一';
COMMENT ON COLUMN t_user.password    IS 'BCrypt 加密密码';
COMMENT ON COLUMN t_user.status      IS '1=正常 0=封禁';
COMMENT ON COLUMN t_user.deleted     IS '逻辑删除标志';
