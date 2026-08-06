package com.shanyuefang.agent.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("t_book_knowledge_space")
public class BookKnowledgeSpace {
    @TableId private Long canonicalBookId;
    private String status;
    private Boolean isPublic;
    private Long ownerUserId;
    private String modelMode;
    private Long modelConfigId;
    private Integer totalChapters;
    private Integer completedChapters;
    private Long estimatedInputTokens;
    private Long estimatedOutputTokens;
    private Integer estimatedCredits;
    private String failureMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
