ALTER TABLE t_knowledge_graph_node ADD COLUMN IF NOT EXISTS identity_key VARCHAR(256);
UPDATE t_knowledge_graph_node
SET identity_key = LOWER(node_type || ':' || name)
WHERE identity_key IS NULL;
ALTER TABLE t_knowledge_graph_node ALTER COLUMN identity_key SET NOT NULL;
ALTER TABLE t_knowledge_graph_node DROP CONSTRAINT IF EXISTS uk_graph_node_book_name_type;
ALTER TABLE t_knowledge_graph_node ADD CONSTRAINT uk_graph_node_book_identity_key UNIQUE (canonical_book_id, identity_key);
ALTER TABLE t_knowledge_entity_alias DROP CONSTRAINT IF EXISTS uk_knowledge_entity_alias;
ALTER TABLE t_knowledge_entity_alias ADD CONSTRAINT uk_knowledge_entity_alias_node UNIQUE (canonical_book_id, node_id, alias, node_type);
