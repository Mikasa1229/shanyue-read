package com.shanyuefang.novel.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** Durable, bounded recovery of chapter evidence that was removed downstream. */
@Data
@TableName("t_book_content_recovery_task")
public class ContentRecoveryTask {
    private Long id;
    private Long canonicalBookId;
    private Integer startChapter;
    private Integer endChapter;
    private String status;
    private Integer totalChapters;
    private Integer completedChapters;
    private Integer failedChapters;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime updatedAt;
}
