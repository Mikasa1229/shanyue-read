package com.shanyuefang.novel.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class NovelVO {

    private Long id;
    private Long userId;
    private String title;
    private String authorName;
    private String category;
    private String coverUrl;
    private String summary;

    /** 1=连载中 2=已完结 */
    private Integer status;
    private String statusLabel;

    private Long wordCount;
    private Long viewCount;
    private Long likeCount;
    private Long favoriteCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
