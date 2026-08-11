-- A clue can advance multiple times before its final answer; retain every evidence-backed milestone.
CREATE TABLE IF NOT EXISTS t_knowledge_clue_resolution (
    id BIGINT PRIMARY KEY,
    canonical_book_id BIGINT NOT NULL,
    clue_id BIGINT NOT NULL,
    resolution_chapter INTEGER NOT NULL,
    resolution_type VARCHAR(24) NOT NULL,
    evidence TEXT NOT NULL,
    explanation TEXT,
    confidence NUMERIC(4,3) NOT NULL DEFAULT 0.700,
    source_model_version VARCHAR(128) NOT NULL DEFAULT 'clue-lifecycle-v3',
    review_status VARCHAR(24) NOT NULL DEFAULT 'APPROVED',
    content_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_knowledge_clue_resolution_type CHECK (resolution_type IN ('PARTIAL', 'FINAL')),
    CONSTRAINT uk_knowledge_clue_resolution_evidence UNIQUE (clue_id, content_hash)
);

CREATE INDEX IF NOT EXISTS idx_knowledge_clue_resolution_visibility
    ON t_knowledge_clue_resolution(canonical_book_id, clue_id, resolution_chapter);

-- Preserve legacy final answers as the first auditable lifecycle milestone during upgrade.
INSERT INTO t_knowledge_clue_resolution (
    id, canonical_book_id, clue_id, resolution_chapter, resolution_type, evidence, explanation,
    confidence, source_model_version, review_status, content_hash, created_at, updated_at
)
SELECT
    id, canonical_book_id, id, resolved_chapter, 'FINAL', resolution_evidence,
    '由旧版线索生命周期迁移', 0.700, COALESCE(source_model_version, 'legacy-clue-lifecycle'),
    COALESCE(review_status, 'APPROVED'), md5(resolution_evidence), NOW(), NOW()
FROM t_knowledge_clue
WHERE resolved_chapter IS NOT NULL AND resolution_evidence IS NOT NULL AND length(trim(resolution_evidence)) > 0
ON CONFLICT (clue_id, content_hash) DO NOTHING;
