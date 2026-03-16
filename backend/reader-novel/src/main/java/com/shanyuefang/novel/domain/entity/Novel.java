package com.shanyuefang.novel.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_novel")
public class Novel {

    @TableId(type = IdType.INPUT)
    private Long id;

    /** 发布者用户ID */
    private Long userId;

    private String title;

    /** 作者笔名（可与发布者不同） */
    private String authorName;

    private String category;

    private String coverUrl;

    private String summary;

    /** 1=连载中 2=已完结 */
    private Integer status;

    private Long wordCount;

    private Long viewCount;

    private Long likeCount;

    private Long favoriteCount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Boolean deleted;
}
