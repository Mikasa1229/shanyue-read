package com.shanyuefang.user.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class LevelActionDTO {

    @NotBlank(message = "行为类型不能为空")
    private String actionType;

    @NotNull(message = "行为值不能为空")
    @Positive(message = "行为值必须大于 0")
    private Integer value;
}
