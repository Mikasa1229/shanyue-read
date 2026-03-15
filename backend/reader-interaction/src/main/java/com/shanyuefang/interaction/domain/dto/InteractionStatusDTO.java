package com.shanyuefang.interaction.domain.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 批量查询互动状态请求 DTO
 */
@Data
public class InteractionStatusDTO {

    @NotEmpty(message = "目标 ID 列表不能为空")
    private List<Long> targetIds;

    /** 目标类型：1=小说 2=点评 */
    @NotNull(message = "目标类型不能为空")
    private Integer targetType;

    /** 行为：1=点赞 2=收藏 */
    @NotNull(message = "行为类型不能为空")
    private Integer action;
}
