CREATE TABLE IF NOT EXISTS t_comment (
  id          BIGINT        PRIMARY KEY,
  novel_id    BIGINT        NOT NULL,
  user_id     BIGINT        NOT NULL,
  parent_id   BIGINT,
  root_id     BIGINT,
  content     TEXT          NOT NULL,
  like_count  INT           NOT NULL DEFAULT 0,
  status      SMALLINT      NOT NULL DEFAULT 1,
  created_at  TIMESTAMP     NOT NULL DEFAULT NOW(),
  deleted     BOOLEAN       NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_t_comment_novel_root ON t_comment(novel_id, root_id) WHERE deleted = FALSE;
CREATE INDEX idx_t_comment_parent     ON t_comment(parent_id)         WHERE deleted = FALSE;
