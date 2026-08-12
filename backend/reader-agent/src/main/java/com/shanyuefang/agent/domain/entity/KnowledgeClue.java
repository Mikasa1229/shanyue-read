package com.shanyuefang.agent.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** A spoiler-safe clue with a lifecycle that can be shown only within the user's reading boundary. */
@Data
@TableName("t_knowledge_clue")
public class KnowledgeClue {
    private Long id;
    private Long canonicalBookId;
    private Integer chapterIndex;
    private String signal;
    private String excerpt;
    private String contentHash;
    private String status;
    private Integer resolvedChapter;
    private String resolutionEvidence;
    private String sourceModelVersion;
    private String reviewStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
