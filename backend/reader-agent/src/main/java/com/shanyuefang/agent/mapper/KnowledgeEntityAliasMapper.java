package com.shanyuefang.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shanyuefang.agent.domain.entity.KnowledgeEntityAlias;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface KnowledgeEntityAliasMapper extends BaseMapper<KnowledgeEntityAlias> {
    @Insert("""
            INSERT INTO t_knowledge_entity_alias
                (id, canonical_book_id, node_id, alias, node_type, first_chapter, evidence, confidence, created_at, updated_at)
            VALUES (#{id}, #{canonicalBookId}, #{nodeId}, #{alias}, #{nodeType}, #{firstChapter}, #{evidence}, #{confidence}, #{createdAt}, #{updatedAt})
            ON CONFLICT (canonical_book_id, node_id, alias, node_type) DO NOTHING
            """)
    int insertIfAbsent(KnowledgeEntityAlias alias);
}
