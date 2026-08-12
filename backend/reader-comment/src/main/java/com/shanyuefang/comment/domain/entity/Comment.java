package com.shanyuefang.comment.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 点评实体
 */
@Data
@TableName("t_comment")
public class Comment {

    @TableId(type = IdType.INPUT)
    private Long id;

    private Long novelId;

    @TableField("activity_type")
    private String activityType;

    /** 书源书名（novelId 为 null 时使用）*/
    @TableField("book_title")
    private String bookTitle;

    private Long sourceId;

    private String bookUrl;

    private String bookAuthor;

    @TableField("book_cover_url")
    private String bookCoverUrl;

    private String bookIntro;

    private Long userId;

    /** 直接父评论 ID，NULL 表示根评论 */
    private Long parentId;

    /** 根评论 ID，NULL 表示本身是根评论 */
    private Long rootId;

    private String content;

    /** 评分（1-5，仅根点评） */
    private Integer score;

    private Integer likeCount;

    /** 状态：1正常 0审核中 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableLogic
    private Boolean deleted;
}
