-- 书源表：存储 legado 兼容格式的书源定义
-- 通过 source_json 保存完整 JSON，解析逻辑在应用层完成

CREATE TABLE IF NOT EXISTS t_book_source (
    id           BIGINT        PRIMARY KEY,              -- 雪花算法 ID
    source_name  VARCHAR(128)  NOT NULL,                 -- 书源名称（来自 bookSourceName）
    source_url   VARCHAR(512)  NOT NULL UNIQUE,          -- 书源根 URL（来自 bookSourceUrl），唯一键
    source_type  SMALLINT      NOT NULL DEFAULT 0,       -- 书源类型：0=小说 1=漫画 2=音频
    source_group VARCHAR(64),                            -- 书源分组标签
    enabled      BOOLEAN       NOT NULL DEFAULT TRUE,    -- 是否启用
    source_json  TEXT          NOT NULL,                 -- 完整 legado JSON 原文（保留以便重新解析）
    created_at   TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP     NOT NULL DEFAULT NOW()
);

-- 快速查询已启用书源
CREATE INDEX idx_t_book_source_enabled ON t_book_source(enabled) WHERE enabled = TRUE;

COMMENT ON TABLE  t_book_source              IS '书源表，兼容 legado 书源格式';
COMMENT ON COLUMN t_book_source.source_name  IS '书源名称，对应 legado bookSourceName';
COMMENT ON COLUMN t_book_source.source_url   IS '书源根 URL，对应 legado bookSourceUrl，全局唯一';
COMMENT ON COLUMN t_book_source.source_type  IS '书源类型：0=小说 1=漫画 2=音频';
COMMENT ON COLUMN t_book_source.source_group IS '书源分组标签';
COMMENT ON COLUMN t_book_source.enabled      IS '是否启用，禁用后不参与搜索';
COMMENT ON COLUMN t_book_source.source_json  IS '完整 legado JSON 原文，保留以便规则更新后重新解析';
