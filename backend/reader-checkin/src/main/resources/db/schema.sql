-- 打卡服务数据库初始化脚本
CREATE TABLE IF NOT EXISTS t_checkin
(
    id           BIGINT      NOT NULL,
    user_id      BIGINT      NOT NULL,
    novel_id     BIGINT,
    checkin_date DATE        NOT NULL,
    note         VARCHAR(500),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_checkin PRIMARY KEY (id),
    CONSTRAINT uk_user_date UNIQUE (user_id, checkin_date)
);

-- 按用户查询加速
CREATE INDEX IF NOT EXISTS idx_checkin_user_date ON t_checkin (user_id, checkin_date DESC);
