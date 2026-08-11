package com.shanyuefang.agent.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** An auditable later-chapter development or final answer for one clue. */
@Data
@TableName("t_knowledge_clue_resolution")
public class KnowledgeClueResolution {
    private Long id;
    private Long canonicalBookId;
    private Long clueId;
    private Integer resolutionChapter;
    private String resolutionType;
    private String evidence;
    private String explanation;
    private Double confidence;
    private String sourceModelVersion;
    private String reviewStatus;
    private String contentHash;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
