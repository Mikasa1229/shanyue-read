CREATE TABLE IF NOT EXISTS t_knowledge_entity_alias (
    id BIGINT PRIMARY KEY,
    canonical_book_id BIGINT NOT NULL,
    node_id BIGINT NOT NULL,
    alias VARCHAR(128) NOT NULL,
    node_type VARCHAR(32) NOT NULL,
    first_chapter INTEGER NOT NULL,
    evidence TEXT,
    confidence NUMERIC(4,3) NOT NULL DEFAULT 0.700,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_knowledge_entity_alias UNIQUE (canonical_book_id, alias, node_type)
);
CREATE INDEX IF NOT EXISTS idx_knowledge_entity_alias_lookup
    ON t_knowledge_entity_alias(canonical_book_id, alias, node_type, first_chapter);
