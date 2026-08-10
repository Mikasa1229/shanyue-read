package com.shanyuefang.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shanyuefang.agent.domain.entity.KnowledgeRelationAssertion;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface KnowledgeRelationAssertionMapper extends BaseMapper<KnowledgeRelationAssertion> {
    @Insert("""
            INSERT INTO t_knowledge_relation_assertion
                (id, canonical_book_id, source_node_id, target_node_id, relation, chapter_index, evidence, evidence_hash,
                 confidence, extraction_model_version, verifier_version, verification_status, created_at, updated_at)
            VALUES (#{id}, #{canonicalBookId}, #{sourceNodeId}, #{targetNodeId}, #{relation}, #{chapterIndex}, #{evidence}, #{evidenceHash},
                    #{confidence}, #{extractionModelVersion}, #{verifierVersion}, #{verificationStatus}, #{createdAt}, #{updatedAt})
            ON CONFLICT (canonical_book_id, source_node_id, target_node_id, relation, chapter_index, evidence_hash) DO NOTHING
            """)
    int insertIfAbsent(KnowledgeRelationAssertion assertion);
}
