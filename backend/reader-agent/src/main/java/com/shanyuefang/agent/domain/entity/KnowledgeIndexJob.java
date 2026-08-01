package com.shanyuefang.agent.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_knowledge_index_job")
public class KnowledgeIndexJob {
    @TableId
    private Long id;
    private Long canonicalBookId;
    private String jobType;
    private String status;
    private String payloadJson;
    private Integer retryCount;
    private String errorMessage;
    private String dedupeKey;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
