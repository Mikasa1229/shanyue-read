-- 善阅坊·点评服务 数据库初始化脚本
-- 数据库：db_comment（PostgreSQL 16）

CREATE TABLE IF NOT EXISTS t_comment
(
    id          BIGINT        NOT NULL PRIMARY KEY,
    novel_id    BIGINT        NOT NULL,
    user_id     BIGINT        NOT NULL,
    parent_id   BIGINT,                                      -- 直接父评论，NULL 为根评论
    root_id     BIGINT,                                      -- 根评论 ID，NULL 表示本身是根
    content     VARCHAR(1000) NOT NULL,
    like_count  INT           NOT NULL DEFAULT 0,
    status      SMALLINT      NOT NULL DEFAULT 1,            -- 1=正常 0=审核中
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    deleted     BOOLEAN       NOT NULL DEFAULT FALSE
);

-- 按小说查根评论（最常用查询）
CREATE INDEX IF NOT EXISTS idx_comment_novel_root
    ON t_comment (novel_id, root_id, status, created_at DESC)
    WHERE deleted = FALSE;

-- 按根评论查回复
CREATE INDEX IF NOT EXISTS idx_comment_root_id
    ON t_comment (root_id, created_at ASC)
    WHERE deleted = FALSE;

-- 按用户查自己的评论
CREATE INDEX IF NOT EXISTS idx_comment_user_id
    ON t_comment (user_id)
    WHERE deleted = FALSE;

COMMENT ON TABLE  t_comment            IS '点评表';
COMMENT ON COLUMN t_comment.parent_id  IS '直接父评论 ID，NULL 为根评论';
COMMENT ON COLUMN t_comment.root_id    IS '根评论 ID，NULL 表示本身是根评论';
COMMENT ON COLUMN t_comment.status     IS '1=正常 0=审核中';
