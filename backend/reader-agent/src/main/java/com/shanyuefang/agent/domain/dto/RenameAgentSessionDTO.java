package com.shanyuefang.agent.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RenameAgentSessionDTO {
    @NotBlank(message = "会话标题不能为空")
    @Size(max = 80, message = "会话标题不能超过 80 个字符")
    private String title;
}
