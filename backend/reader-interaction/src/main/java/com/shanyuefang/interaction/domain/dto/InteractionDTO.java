package com.shanyuefang.interaction.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 点赞 / 收藏请求 DTO
 */
@Data
public class InteractionDTO {

    @NotNull(message = "目标 ID 不能为空")
    private Long targetId;

    /**
     * 目标类型：1=小说 2=点评
     */
    @NotNull(message = "目标类型不能为空")
    private Integer targetType;
}
