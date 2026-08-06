package com.shanyuefang.agent.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** A durable record that a chapter's graph extraction completed successfully. */
@Data
@TableName("t_book_knowledge_chapter_coverage")
public class BookKnowledgeChapterCoverage {
    // MyBatis-Plus requires an id even though the database uses a composite unique key.
    @TableId("canonical_book_id")
    private Long canonicalBookId;
    private Integer chapterIndex;
    private LocalDateTime completedAt;
}
