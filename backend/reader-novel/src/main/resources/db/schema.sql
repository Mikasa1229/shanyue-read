-- 小说服务数据库初始化脚本
CREATE TABLE IF NOT EXISTS t_novel
(
    id             BIGINT        NOT NULL,
    user_id        BIGINT        NOT NULL,
    title          VARCHAR(200)  NOT NULL,
    author_name    VARCHAR(100)  NOT NULL,
    category       VARCHAR(50),
    cover_url      VARCHAR(500),
    summary        TEXT,
    status         SMALLINT      NOT NULL DEFAULT 1,   -- 1=连载中 2=已完结
    word_count     BIGINT        NOT NULL DEFAULT 0,
    view_count     BIGINT        NOT NULL DEFAULT 0,
    like_count     BIGINT        NOT NULL DEFAULT 0,
    favorite_count BIGINT        NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    deleted        BOOLEAN       NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_novel PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_novel_category ON t_novel (category) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_novel_user    ON t_novel (user_id)   WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_novel_created ON t_novel (created_at DESC) WHERE deleted = FALSE;
