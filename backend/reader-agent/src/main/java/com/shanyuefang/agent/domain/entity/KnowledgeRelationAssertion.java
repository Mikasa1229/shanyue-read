package com.shanyuefang.agent.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** One evidence-backed relation statement; graph edges are aggregated projections of these rows. */
@Data
@TableName("t_knowledge_relation_assertion")
public class KnowledgeRelationAssertion {
    private Long id;
    private Long canonicalBookId;
    private Long sourceNodeId;
    private Long targetNodeId;
    private String relation;
    private Integer chapterIndex;
    private String evidence;
    private String evidenceHash;
    private Double confidence;
    private String extractionModelVersion;
    private String verifierVersion;
    private String verificationStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
