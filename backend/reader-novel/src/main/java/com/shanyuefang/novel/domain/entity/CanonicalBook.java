package com.shanyuefang.novel.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("t_canonical_book")
public class CanonicalBook {
    @TableId
    private Long id;
    private String normalizedTitle;
    private String normalizedAuthor;
    private String title;
    private String author;
    private String coverUrl;
    private String summary;
    private Double mergeConfidence;
    private String mergeStatus;
}
