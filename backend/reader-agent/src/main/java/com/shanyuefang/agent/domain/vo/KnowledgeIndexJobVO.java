package com.shanyuefang.agent.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class KnowledgeIndexJobVO {
    private Long id;
    private Long canonicalBookId;
    private String jobType;
    private String status;
    private Integer retryCount;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
