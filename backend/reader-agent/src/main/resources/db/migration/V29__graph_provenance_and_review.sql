-- LightRAG claims remain explainable when graph extraction models change.
ALTER TABLE t_knowledge_graph_node
    ADD COLUMN IF NOT EXISTS source_model_version VARCHAR(128) NOT NULL DEFAULT 'rule-extractor-v1',
    ADD COLUMN IF NOT EXISTS review_status VARCHAR(24) NOT NULL DEFAULT 'APPROVED';

ALTER TABLE t_knowledge_graph_edge
    ADD COLUMN IF NOT EXISTS source_model_version VARCHAR(128) NOT NULL DEFAULT 'rule-extractor-v1',
    ADD COLUMN IF NOT EXISTS review_status VARCHAR(24) NOT NULL DEFAULT 'APPROVED';

ALTER TABLE t_knowledge_clue
    ADD COLUMN IF NOT EXISTS source_model_version VARCHAR(128) NOT NULL DEFAULT 'rule-extractor-v1',
    ADD COLUMN IF NOT EXISTS review_status VARCHAR(24) NOT NULL DEFAULT 'APPROVED';

CREATE INDEX IF NOT EXISTS idx_graph_edge_book_review_chapter
    ON t_knowledge_graph_edge(canonical_book_id, review_status, first_chapter);
