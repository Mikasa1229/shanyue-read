-- Reader-facing character relations must describe stable social identities, not plot actions.
DELETE FROM t_knowledge_graph_edge
WHERE relation IN ('INTERACTS_WITH', 'SUPPORTS', 'OPPOSES', 'TRAVELS_WITH')
  AND source_node_id IN (SELECT id FROM t_knowledge_graph_node WHERE node_type = 'CHARACTER')
  AND target_node_id IN (SELECT id FROM t_knowledge_graph_node WHERE node_type = 'CHARACTER');

UPDATE t_knowledge_graph_edge SET relation = 'PARENT_OF'
WHERE relation = 'FAMILY_OF'
  AND evidence ~ '(父亲|母亲|爹|娘|父子|父女|母子|母女|儿子|女儿)';

DELETE FROM t_knowledge_graph_edge WHERE relation = 'FAMILY_OF';

-- A revelation mistakenly extracted as an open question is retained as a solved clue.
UPDATE t_knowledge_clue
SET status = 'RESOLVED', resolved_chapter = chapter_index,
    resolution_evidence = regexp_replace(excerpt, '^.*【原文依据】', ''), updated_at = NOW()
WHERE status <> 'RESOLVED'
  AND (signal ~ '(真实身份为|被父亲砸碎|明确揭示|真相揭晓)' OR excerpt ~ '^【当前未解原因】[^。]*(已经明确|明确说|已经揭示)');
