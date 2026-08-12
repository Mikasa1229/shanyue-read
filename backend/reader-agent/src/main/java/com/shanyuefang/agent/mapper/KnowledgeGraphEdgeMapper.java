package com.shanyuefang.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shanyuefang.agent.domain.entity.KnowledgeGraphEdge;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface KnowledgeGraphEdgeMapper extends BaseMapper<KnowledgeGraphEdge> {
    @Insert("""
            INSERT INTO t_knowledge_graph_edge
                (id, canonical_book_id, source_node_id, target_node_id, relation, first_chapter, last_chapter,
                 evidence, confidence, source_model_version, review_status, created_at, updated_at)
            VALUES (#{id}, #{canonicalBookId}, #{sourceNodeId}, #{targetNodeId}, #{relation}, #{firstChapter}, #{lastChapter},
                    #{evidence}, #{confidence}, #{sourceModelVersion}, #{reviewStatus}, #{createdAt}, #{updatedAt})
            ON CONFLICT (canonical_book_id, source_node_id, target_node_id, relation) DO NOTHING
            """)
    int insertIfAbsent(KnowledgeGraphEdge edge);
}
