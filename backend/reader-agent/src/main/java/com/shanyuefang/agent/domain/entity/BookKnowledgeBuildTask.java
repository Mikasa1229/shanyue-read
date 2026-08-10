package com.shanyuefang.agent.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("t_book_knowledge_build_task")
public class BookKnowledgeBuildTask {
    @TableId @JsonSerialize(using = ToStringSerializer.class) private Long id;
    @JsonSerialize(using = ToStringSerializer.class) private Long canonicalBookId;
    /** Populated for task-list responses; the task table only stores the canonical work id. */
    @TableField(exist = false) private String bookTitle;
    @JsonSerialize(using = ToStringSerializer.class) private Long requesterUserId;
    private String modelMode;
    @JsonSerialize(using = ToStringSerializer.class) private Long modelConfigId;
    private Boolean isPublic;
    private String status;
    /** Inclusive, one-based chapter boundaries selected by the reader. */
    private Integer startChapter;
    private Integer endChapter;
    private Integer totalChapters;
    private Integer completedChapters;
    private Long estimatedInputTokens;
    private Long estimatedOutputTokens;
    private Integer estimatedCredits;
    private Integer chargedCredits;
    private String message;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime updatedAt;
}
