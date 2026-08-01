package com.shanyuefang.agent.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** Durable source of truth for optional Milvus profile projections. */
@Data
@TableName("t_knowledge_vector_profile")
public class KnowledgeVectorProfile {
    private Long id;
    private String profileType;
    private Long subjectId;
    private Long canonicalBookId;
    private String content;
    private String contentHash;
    private String embeddingJson;
    private String modelVersion;
    private LocalDateTime indexedAt;
    private LocalDateTime deletedAt;
}
