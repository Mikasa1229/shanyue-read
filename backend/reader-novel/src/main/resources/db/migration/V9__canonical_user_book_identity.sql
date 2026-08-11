-- A user owns one shelf/favorite projection per canonical work. Keep the most recently read
-- shelf row before enforcing this invariant; source_id and book_url remain the active mirror.
WITH ranked AS (
    SELECT id, ROW_NUMBER() OVER (
        PARTITION BY user_id, canonical_book_id
        ORDER BY last_read_at DESC NULLS LAST, created_at DESC, id DESC
    ) AS row_number
    FROM t_bookshelf_book
    WHERE canonical_book_id IS NOT NULL
)
DELETE FROM t_bookshelf_book WHERE id IN (SELECT id FROM ranked WHERE row_number > 1);

WITH ranked AS (
    SELECT id, ROW_NUMBER() OVER (
        PARTITION BY user_id, canonical_book_id
        ORDER BY created_at DESC, id DESC
    ) AS row_number
    FROM t_favorite_book
    WHERE canonical_book_id IS NOT NULL
)
DELETE FROM t_favorite_book WHERE id IN (SELECT id FROM ranked WHERE row_number > 1);

CREATE UNIQUE INDEX IF NOT EXISTS uk_bookshelf_user_canonical_book
    ON t_bookshelf_book(user_id, canonical_book_id) WHERE canonical_book_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uk_favorite_user_canonical_book
    ON t_favorite_book(user_id, canonical_book_id) WHERE canonical_book_id IS NOT NULL;
