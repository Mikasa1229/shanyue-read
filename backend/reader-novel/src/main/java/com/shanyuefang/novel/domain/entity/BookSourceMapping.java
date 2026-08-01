package com.shanyuefang.novel.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("t_book_source_mapping")
public class BookSourceMapping {
    @TableId
    private Long id;
    private Long canonicalBookId;
    private Long sourceId;
    private String sourceBookUrl;
    private String sourceTitle;
    private String sourceAuthor;
    private String contentVersion;
}
