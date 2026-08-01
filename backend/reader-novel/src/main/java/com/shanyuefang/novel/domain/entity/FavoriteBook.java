package com.shanyuefang.novel.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("t_favorite_book")
public class FavoriteBook {

    @TableId(type = IdType.INPUT)
    private Long id;

    private Long userId;
    private Long sourceId;
    private Long canonicalBookId;
    private String sourceName;
    private String bookName;
    private String author;
    private String coverUrl;
    private String bookUrl;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
