CREATE TABLE IF NOT EXISTS t_knowledge_clue_graph_link (
    id BIGINT PRIMARY KEY,
    canonical_book_id BIGINT NOT NULL,
    clue_id BIGINT NOT NULL,
    node_id BIGINT NOT NULL,
    link_type VARCHAR(32) NOT NULL DEFAULT 'MENTIONS',
    confidence NUMERIC(4,3) NOT NULL DEFAULT 0.700,
    evidence TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_knowledge_clue_graph_link UNIQUE (clue_id, node_id, link_type)
);
CREATE INDEX IF NOT EXISTS idx_knowledge_clue_graph_link_book
    ON t_knowledge_clue_graph_link(canonical_book_id, clue_id);
