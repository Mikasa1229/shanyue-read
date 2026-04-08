package com.shanyuefang.interaction.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 统一切换互动（点赞 / 收藏）请求 DTO
 */
@Data
public class ToggleInteractionDTO {

    @NotNull(message = "目标 ID 不能为空")
    private Long targetId;

    /** 目标类型：1=小说 2=点评 */
    @NotNull(message = "目标类型不能为空")
    private Integer targetType;

    /** 行为：1=点赞 2=收藏 */
    @NotNull(message = "行为类型不能为空")
    private Integer action;
}
