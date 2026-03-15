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

    private Long userId;

    /** 直接父评论 ID，NULL 表示根评论 */
    private Long parentId;

    /** 根评论 ID，NULL 表示本身是根评论 */
    private Long rootId;

    private String content;

    private Integer likeCount;

    /** 状态：1正常 0审核中 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableLogic
    private Boolean deleted;
}
