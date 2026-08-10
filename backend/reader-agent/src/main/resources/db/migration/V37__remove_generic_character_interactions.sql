-- “发生互动”是旧版人物抽取的无语义兜底边，不能作为人物关系展示或参与 LightRAG 扩展。
DELETE FROM t_knowledge_graph_edge
WHERE relation = 'INTERACTS_WITH';
