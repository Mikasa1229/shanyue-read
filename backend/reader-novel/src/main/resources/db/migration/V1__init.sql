CREATE TABLE IF NOT EXISTS t_novel (
  id              BIGINT        PRIMARY KEY,
  user_id         BIGINT        NOT NULL,
  title           VARCHAR(255)  NOT NULL,
  author_name     VARCHAR(64),
  category        VARCHAR(64),
  cover_url       VARCHAR(512),
  summary         TEXT,
  status          SMALLINT      NOT NULL DEFAULT 1,
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
