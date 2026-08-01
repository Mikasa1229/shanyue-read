CREATE TABLE IF NOT EXISTS t_knowledge_chunk (
    id BIGINT PRIMARY KEY,
    document_id BIGINT NOT NULL,
    canonical_book_id BIGINT NOT NULL,
    chapter_index INTEGER NOT NULL,
    content TEXT NOT NULL,
    keywords TEXT,
    embedding_json TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_knowledge_chunk_book_chapter
    ON t_knowledge_chunk(canonical_book_id, chapter_index);

CREATE TABLE IF NOT EXISTS t_knowledge_graph_node (
    id BIGINT PRIMARY KEY,
    canonical_book_id BIGINT NOT NULL,
    name VARCHAR(128) NOT NULL,
    node_type VARCHAR(32) NOT NULL,
    first_chapter INTEGER NOT NULL,
    last_chapter INTEGER NOT NULL,
    evidence TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_graph_node_book_name_type UNIQUE (canonical_book_id, name, node_type)
);
CREATE INDEX IF NOT EXISTS idx_graph_node_book_chapter
    ON t_knowledge_graph_node(canonical_book_id, first_chapter);

CREATE TABLE IF NOT EXISTS t_knowledge_graph_edge (
    id BIGINT PRIMARY KEY,
    canonical_book_id BIGINT NOT NULL,
    source_node_id BIGINT NOT NULL,
    target_node_id BIGINT NOT NULL,
    relation VARCHAR(64) NOT NULL,
    first_chapter INTEGER NOT NULL,
    last_chapter INTEGER NOT NULL,
    evidence TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_graph_edge_book_nodes_relation UNIQUE (canonical_book_id, source_node_id, target_node_id, relation)
);
CREATE INDEX IF NOT EXISTS idx_graph_edge_book_chapter
    ON t_knowledge_graph_edge(canonical_book_id, first_chapter);
