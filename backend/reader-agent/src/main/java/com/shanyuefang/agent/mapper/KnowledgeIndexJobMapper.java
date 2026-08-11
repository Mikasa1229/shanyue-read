package com.shanyuefang.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shanyuefang.agent.domain.entity.KnowledgeIndexJob;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface KnowledgeIndexJobMapper extends BaseMapper<KnowledgeIndexJob> {
    @Update("UPDATE t_knowledge_index_job SET status = 'PROCESSING', updated_at = CURRENT_TIMESTAMP "
            + "WHERE id = #{jobId} AND status = 'PENDING'")
    int claimEmbeddingRebuild(@Param("jobId") long jobId);

    @Update("UPDATE t_knowledge_index_job SET status = 'PENDING', updated_at = CURRENT_TIMESTAMP " +
            "WHERE job_type = 'EMBEDDING_REBUILD' AND status = 'PROCESSING'")
    int recoverInterruptedEmbeddingRebuilds();
}
