package com.shanyuefang.novel.domain.vo;

import lombok.Data;

/** 供 Feign / 内部接口返回的精简 VO */
@Data
public class NovelSimpleVO {

    private Long id;
    private String title;
    private String authorName;
    private String category;
    private String coverUrl;
    private Long viewCount;
}
