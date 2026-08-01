package com.shanyuefang.agent.domain.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class AgentAdminRoleDTO {
    @NotNull
    private Long userId;
    @NotBlank
    @Pattern(regexp = "ADMIN|OPERATOR")
    private String roleCode;
}
