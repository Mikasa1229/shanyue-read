package com.shanyuefang.novel.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_book_content_version")
public class BookContentVersion {
    private Long id;
    private Long canonicalBookId;
    private Long sourceId;
    private Integer chapterIndex;
    private String chapterUrl;
    private String contentHash;
    private LocalDateTime fetchedAt;
    private String indexStatus;
}
