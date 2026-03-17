package com.shanyuefang.novel.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_book_source")
public class BookSource {

    @TableId(type = IdType.INPUT)
    private Long id;

    /** 书源名称 */
    private String sourceName;

    /** 书源 base URL（唯一键） */
    private String sourceUrl;

    /** 0=小说 */
    private Integer sourceType;

    /** 书源分组 */
    private String sourceGroup;

    /** 是否启用 */
    private Boolean enabled;

    /** legado 格式的完整 JSON 原文 */
    private String sourceJson;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
