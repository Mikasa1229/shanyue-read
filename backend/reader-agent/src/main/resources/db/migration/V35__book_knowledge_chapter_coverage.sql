CREATE TABLE IF NOT EXISTS t_book_knowledge_chapter_coverage (
    canonical_book_id BIGINT NOT NULL,
    chapter_index INTEGER NOT NULL,
    completed_at TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (canonical_book_id, chapter_index)
);

INSERT INTO t_book_knowledge_chapter_coverage (canonical_book_id, chapter_index, completed_at)
SELECT task.canonical_book_id, chapter_index, COALESCE(task.completed_at, NOW())
FROM t_book_knowledge_build_task task
CROSS JOIN LATERAL generate_series(task.start_chapter - 1, task.end_chapter - 1) AS chapter_index
WHERE task.status = 'COMPLETED'
  AND task.start_chapter IS NOT NULL
  AND task.end_chapter IS NOT NULL
ON CONFLICT (canonical_book_id, chapter_index) DO NOTHING;
