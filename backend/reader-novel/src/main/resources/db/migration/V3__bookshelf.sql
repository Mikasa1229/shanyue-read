-- 书架表：保存用户从书源搜索加入书架的书籍
CREATE TABLE IF NOT EXISTS t_bookshelf_book (
    id                  BIGINT          PRIMARY KEY,
    user_id             BIGINT          NOT NULL,
    source_id           BIGINT,
    source_name         VARCHAR(128),
    book_name           VARCHAR(256)    NOT NULL,
    author              VARCHAR(128),
    cover_url           VARCHAR(512),
    book_url            VARCHAR(512)    NOT NULL,
    last_chapter_name   VARCHAR(256),
    last_chapter_url    VARCHAR(512),
    last_read_at        TIMESTAMP,
    created_at          TIMESTAMP       NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_shelf_user_book UNIQUE (user_id, book_url)
);

CREATE INDEX idx_bookshelf_user_id ON t_bookshelf_book(user_id);

COMMENT ON TABLE  t_bookshelf_book                  IS '用户书架（书源书籍）';
COMMENT ON COLUMN t_bookshelf_book.source_id        IS '来源书源 ID';
COMMENT ON COLUMN t_bookshelf_book.book_url         IS '书源网站书籍 URL（唯一标识）';
COMMENT ON COLUMN t_bookshelf_book.last_chapter_name IS '最后阅读章节名';
COMMENT ON COLUMN t_bookshelf_book.last_chapter_url  IS '最后阅读章节 URL';
COMMENT ON COLUMN t_bookshelf_book.last_read_at      IS '最后阅读时间';
