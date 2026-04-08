CREATE TABLE t_favorite_book (
    id          BIGINT PRIMARY KEY,
    user_id     BIGINT NOT NULL,
    source_id   BIGINT,
    source_name VARCHAR(128),
    book_name   VARCHAR(256) NOT NULL,
    author      VARCHAR(128),
    cover_url   VARCHAR(512),
    book_url    VARCHAR(512) NOT NULL,
    created_at  TIMESTAMP DEFAULT NOW(),
    UNIQUE (user_id, book_url)
);
