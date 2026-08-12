-- Preserve every evidence-backed relation statement separately from the reader-facing graph edge.
CREATE TABLE IF NOT EXISTS t_knowledge_relation_assertion (
    id BIGINT PRIMARY KEY,
    canonical_book_id BIGINT NOT NULL,
    source_node_id BIGINT NOT NULL,
    target_node_id BIGINT NOT NULL,
    relation VARCHAR(64) NOT NULL,
    chapter_index INTEGER NOT NULL,
    evidence TEXT NOT NULL,
    evidence_hash VARCHAR(64) NOT NULL,
    confidence NUMERIC(4,3) NOT NULL DEFAULT 0.700,
    extraction_model_version VARCHAR(128) NOT NULL,
    verifier_version VARCHAR(128) NOT NULL,
    verification_status VARCHAR(24) NOT NULL DEFAULT 'VERIFIED',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_relation_assertion_evidence UNIQUE
        (canonical_book_id, source_node_id, target_node_id, relation, chapter_index, evidence_hash)
);
CREATE INDEX IF NOT EXISTS idx_relation_assertion_visible
    ON t_knowledge_relation_assertion(canonical_book_id, verification_status, chapter_index);
CREATE INDEX IF NOT EXISTS idx_relation_assertion_pair
    ON t_knowledge_relation_assertion(canonical_book_id, source_node_id, target_node_id, relation);
