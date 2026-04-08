package com.shanyuefang.interaction.feign.vo;

import lombok.Data;

/** 从小说服务 Feign 接口获取的精简 VO */
@Data
public class NovelSimpleVO {

    private Long id;
    private String title;
    private String authorName;
    private String category;
    private String coverUrl;
    private Long viewCount;
}
