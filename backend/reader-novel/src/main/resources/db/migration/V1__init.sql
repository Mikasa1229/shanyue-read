-- 善阅坊·小说服务 数据库初始化脚本
-- 数据库：db_novel（PostgreSQL 16）

CREATE TABLE IF NOT EXISTS t_novel (
  id              BIGINT        PRIMARY KEY,             -- 雪花算法 ID
  user_id         BIGINT        NOT NULL,                -- 作者用户 ID
  title           VARCHAR(255)  NOT NULL,                -- 书名
  author_name     VARCHAR(64),                           -- 作者笔名（冗余存储，无需关联查询）
  category        VARCHAR(64),                           -- 分类标签，如 玄幻/言情
  cover_url       VARCHAR(512),                          -- 封面图 URL
  summary         TEXT,                                  -- 简介
  status          SMALLINT      NOT NULL DEFAULT 1,      -- 1=连载中 2=已完结
  word_count      BIGINT        NOT NULL DEFAULT 0,      -- 总字数
  view_count      BIGINT        NOT NULL DEFAULT 0,      -- 浏览量
  like_count      BIGINT        NOT NULL DEFAULT 0,      -- 点赞数
  favorite_count  BIGINT        NOT NULL DEFAULT 0,      -- 收藏数
  created_at      TIMESTAMP     NOT NULL DEFAULT NOW(),
  updated_at      TIMESTAMP     NOT NULL DEFAULT NOW(),
  deleted         BOOLEAN       NOT NULL DEFAULT FALSE   -- 逻辑删除标志
);

CREATE INDEX idx_t_novel_user_id  ON t_novel(user_id);
CREATE INDEX idx_t_novel_category ON t_novel(category) WHERE deleted = FALSE;

COMMENT ON TABLE  t_novel                IS '小说表';
COMMENT ON COLUMN t_novel.user_id        IS '作者用户 ID';
COMMENT ON COLUMN t_novel.author_name    IS '作者笔名（冗余）';
COMMENT ON COLUMN t_novel.status         IS '1=连载中 2=已完结';
COMMENT ON COLUMN t_novel.deleted        IS '逻辑删除标志';
