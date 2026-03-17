-- 书源表：存储 legado 兼容格式的书源定义
CREATE TABLE IF NOT EXISTS t_book_source (
    id           BIGINT        PRIMARY KEY,
    source_name  VARCHAR(128)  NOT NULL,
    source_url   VARCHAR(512)  NOT NULL UNIQUE,  -- 书源 base URL，唯一键
    source_type  SMALLINT      NOT NULL DEFAULT 0, -- 0=小说
    source_group VARCHAR(64),
    enabled      BOOLEAN       NOT NULL DEFAULT TRUE,
    source_json  TEXT          NOT NULL,           -- 完整的 legado JSON 原文
    created_at   TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_t_book_source_enabled ON t_book_source(enabled) WHERE enabled = TRUE;
