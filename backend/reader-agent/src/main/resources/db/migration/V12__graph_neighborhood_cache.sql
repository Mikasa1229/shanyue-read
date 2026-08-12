CREATE TABLE IF NOT EXISTS t_graph_neighborhood_cache (
    id BIGINT PRIMARY KEY,
    canonical_book_id BIGINT NOT NULL,
    cache_key VARCHAR(128) NOT NULL,
    current_chapter INTEGER NOT NULL,
    max_edges INTEGER NOT NULL,
    edges_json TEXT NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (canonical_book_id, cache_key)
);

CREATE INDEX IF NOT EXISTS idx_graph_neighborhood_cache_expiry
    ON t_graph_neighborhood_cache (canonical_book_id, expires_at);
