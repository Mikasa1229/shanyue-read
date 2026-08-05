package com.shanyuefang.agent.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_knowledge_document")
public class KnowledgeDocument {
    private Long id;
    private Long canonicalBookId;
    private Long sourceId;
    private Integer chapterIndex;
    private String contentHash;
    private String sourceContentHash;
    private String canonicalContentHash;
    private Long semanticFingerprint;
    private Double contentQualityScore;
    private String normalizationVersion;
    private String contentVersion;
    private String embeddingModelVersion;
    private String indexStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
