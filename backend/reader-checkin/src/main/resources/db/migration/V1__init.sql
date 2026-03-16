CREATE TABLE IF NOT EXISTS t_checkin (
  id            BIGINT    PRIMARY KEY,
  user_id       BIGINT    NOT NULL,
  novel_id      BIGINT    NOT NULL,
  checkin_date  DATE      NOT NULL,
  note          TEXT,
  created_at    TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uk_t_checkin_unique ON t_checkin(user_id, novel_id, checkin_date);
