package com.shanyuefang.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shanyuefang.agent.domain.entity.KnowledgeGraphNode;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface KnowledgeGraphNodeMapper extends BaseMapper<KnowledgeGraphNode> {
    /**
     * Chapter events can arrive concurrently for the same work. Let PostgreSQL
     * arbitrate the identity key instead of turning a harmless race into a DLQ.
     */
    @Insert("""
            INSERT INTO t_knowledge_graph_node
                (id, canonical_book_id, name, node_type, identity_key, first_chapter, last_chapter,
                 evidence, confidence, source_model_version, review_status, created_at, updated_at)
            VALUES (#{id}, #{canonicalBookId}, #{name}, #{nodeType}, #{identityKey}, #{firstChapter}, #{lastChapter},
                    #{evidence}, #{confidence}, #{sourceModelVersion}, #{reviewStatus}, #{createdAt}, #{updatedAt})
            ON CONFLICT (canonical_book_id, identity_key) DO NOTHING
            """)
    int insertIfAbsent(KnowledgeGraphNode node);
}
