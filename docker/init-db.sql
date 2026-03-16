-- 善阅坊数据库初始化脚本
-- PostgreSQL 容器启动时自动执行（仅在 data 目录为空时运行一次）

-- ─────────────────────────────────────────────
--  建库
-- ─────────────────────────────────────────────
CREATE DATABASE db_user;
CREATE DATABASE db_novel;
CREATE DATABASE db_comment;
CREATE DATABASE db_interaction;
CREATE DATABASE db_checkin;

-- ─────────────────────────────────────────────
--  db_user
-- ─────────────────────────────────────────────
\c db_user;

CREATE TABLE IF NOT EXISTS t_user (
  id          BIGINT        PRIMARY KEY,
  username    VARCHAR(64)   NOT NULL,
  password    VARCHAR(255)  NOT NULL,
  nickname    VARCHAR(64),
  avatar      VARCHAR(512),
  status      SMALLINT      NOT NULL DEFAULT 1,       -- 1=正常 0=封禁
  created_at  TIMESTAMP     NOT NULL DEFAULT NOW(),
  updated_at  TIMESTAMP     NOT NULL DEFAULT NOW(),
  deleted     BOOLEAN       NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX uk_t_user_username
  ON t_user(username)
  WHERE deleted = FALSE;

-- ─────────────────────────────────────────────
--  db_novel
-- ─────────────────────────────────────────────
\c db_novel;

CREATE TABLE IF NOT EXISTS t_novel (
  id              BIGINT        PRIMARY KEY,
  user_id         BIGINT        NOT NULL,
  title           VARCHAR(255)  NOT NULL,
  author_name     VARCHAR(64),
  category        VARCHAR(64),
  cover_url       VARCHAR(512),
  summary         TEXT,
  status          SMALLINT      NOT NULL DEFAULT 1,   -- 1=连载 2=完结
  word_count      BIGINT        NOT NULL DEFAULT 0,
  view_count      BIGINT        NOT NULL DEFAULT 0,
  like_count      BIGINT        NOT NULL DEFAULT 0,
  favorite_count  BIGINT        NOT NULL DEFAULT 0,
  created_at      TIMESTAMP     NOT NULL DEFAULT NOW(),
  updated_at      TIMESTAMP     NOT NULL DEFAULT NOW(),
  deleted         BOOLEAN       NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_t_novel_user_id  ON t_novel(user_id);
CREATE INDEX idx_t_novel_category ON t_novel(category) WHERE deleted = FALSE;

-- ─────────────────────────────────────────────
--  db_comment
-- ─────────────────────────────────────────────
\c db_comment;

CREATE TABLE IF NOT EXISTS t_comment (
  id          BIGINT        PRIMARY KEY,
  novel_id    BIGINT        NOT NULL,
  user_id     BIGINT        NOT NULL,
  parent_id   BIGINT,                                 -- 直接父评论，根评论为 NULL
  root_id     BIGINT,                                 -- 楼层根评论，根评论为 NULL
  content     TEXT          NOT NULL,
  like_count  INT           NOT NULL DEFAULT 0,
  status      SMALLINT      NOT NULL DEFAULT 1,       -- 1=正常 0=审核中
  created_at  TIMESTAMP     NOT NULL DEFAULT NOW(),
  deleted     BOOLEAN       NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_t_comment_novel_root ON t_comment(novel_id, root_id) WHERE deleted = FALSE;
CREATE INDEX idx_t_comment_parent     ON t_comment(parent_id)         WHERE deleted = FALSE;

-- ─────────────────────────────────────────────
--  db_interaction
-- ─────────────────────────────────────────────
\c db_interaction;

CREATE TABLE IF NOT EXISTS t_interaction (
  id           BIGINT     PRIMARY KEY,
  user_id      BIGINT     NOT NULL,
  target_id    BIGINT     NOT NULL,
  target_type  SMALLINT   NOT NULL,                   -- 1=小说 2=评论
  action       SMALLINT   NOT NULL,                   -- 1=点赞 2=收藏
  created_at   TIMESTAMP  NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uk_t_interaction_unique
  ON t_interaction(user_id, target_id, target_type, action);

-- ─────────────────────────────────────────────
--  db_checkin
-- ─────────────────────────────────────────────
\c db_checkin;

CREATE TABLE IF NOT EXISTS t_checkin (
  id            BIGINT    PRIMARY KEY,
  user_id       BIGINT    NOT NULL,
  novel_id      BIGINT    NOT NULL,
  checkin_date  DATE      NOT NULL,
  note          TEXT,
  created_at    TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uk_t_checkin_unique
  ON t_checkin(user_id, novel_id, checkin_date);
