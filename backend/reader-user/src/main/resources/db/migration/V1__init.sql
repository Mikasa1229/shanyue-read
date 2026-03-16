CREATE TABLE IF NOT EXISTS t_user (
  id          BIGINT        PRIMARY KEY,
  username    VARCHAR(64)   NOT NULL,
  password    VARCHAR(255)  NOT NULL,
  nickname    VARCHAR(64),
  avatar      VARCHAR(512),
  status      SMALLINT      NOT NULL DEFAULT 1,
  created_at  TIMESTAMP     NOT NULL DEFAULT NOW(),
  updated_at  TIMESTAMP     NOT NULL DEFAULT NOW(),
  deleted     BOOLEAN       NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX uk_t_user_username ON t_user(username) WHERE deleted = FALSE;
