package com.shanyuefang.comment.domain.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 点评分页查询参数
 */
@Data
public class CommentPageDTO {

    @Min(value = 1, message = "页码最小为 1")
    private Integer page = 1;

    @Min(value = 1, message = "每页最少 1 条")
    @Max(value = 50, message = "每页最多 50 条")
    private Integer size = 10;
}
