CREATE TABLE IF NOT EXISTS t_interaction (
  id           BIGINT     PRIMARY KEY,
  user_id      BIGINT     NOT NULL,
  target_id    BIGINT     NOT NULL,
  target_type  SMALLINT   NOT NULL,
  action       SMALLINT   NOT NULL,
  created_at   TIMESTAMP  NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uk_t_interaction_unique ON t_interaction(user_id, target_id, target_type, action);
