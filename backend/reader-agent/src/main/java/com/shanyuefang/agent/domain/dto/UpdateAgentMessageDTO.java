package com.shanyuefang.agent.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateAgentMessageDTO {
    @NotBlank
    @Size(max = 4000)
    private String content;
}
