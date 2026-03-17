package com.shanyuefang.novel.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BookSourceVO {
    private Long id;
    private String sourceName;
    private String sourceUrl;
    private Integer sourceType;
    private String sourceGroup;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
