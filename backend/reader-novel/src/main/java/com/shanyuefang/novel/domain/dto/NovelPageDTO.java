package com.shanyuefang.novel.domain.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class NovelPageDTO {

    /** 分类筛选，空表示全部 */
    private String category;

    /** 关键词搜索（匹配标题/作者名） */
    private String keyword;

    @Min(1)
    private int page = 1;

    @Min(1)
    @Max(50)
    private int size = 20;
}
