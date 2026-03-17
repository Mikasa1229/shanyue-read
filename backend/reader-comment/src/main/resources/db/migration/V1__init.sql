-- 善阅坊·点评服务 数据库初始化脚本
-- 数据库：db_comment（PostgreSQL 16）

CREATE TABLE IF NOT EXISTS t_comment (
  id          BIGINT        PRIMARY KEY,              -- 雪花算法 ID
  novel_id    BIGINT        NOT NULL,                 -- 所属小说 ID
  user_id     BIGINT        NOT NULL,                 -- 发评用户 ID
  parent_id   BIGINT,                                 -- 直接父评论 ID，NULL 为根评论
  root_id     BIGINT,                                 -- 根评论 ID，NULL 表示本身是根
  content     TEXT          NOT NULL,                 -- 评论正文
  like_count  INT           NOT NULL DEFAULT 0,       -- 点赞数
  status      SMALLINT      NOT NULL DEFAULT 1,       -- 1=正常 0=审核中
  created_at  TIMESTAMP     NOT NULL DEFAULT NOW(),
  deleted     BOOLEAN       NOT NULL DEFAULT FALSE    -- 逻辑删除标志
);

-- 按小说查根评论（最常用查询）
CREATE INDEX idx_t_comment_novel_root ON t_comment(novel_id, root_id) WHERE deleted = FALSE;
-- 按根评论查回复
CREATE INDEX idx_t_comment_parent     ON t_comment(parent_id)         WHERE deleted = FALSE;

COMMENT ON TABLE  t_comment            IS '点评表';
COMMENT ON COLUMN t_comment.parent_id  IS '直接父评论 ID，NULL 为根评论';
COMMENT ON COLUMN t_comment.root_id    IS '根评论 ID，NULL 表示本身是根评论';
COMMENT ON COLUMN t_comment.status     IS '1=正常 0=审核中';
COMMENT ON COLUMN t_comment.deleted    IS '逻辑删除标志';
