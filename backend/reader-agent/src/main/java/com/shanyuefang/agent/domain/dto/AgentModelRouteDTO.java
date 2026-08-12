package com.shanyuefang.agent.domain.dto;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
@Data public class AgentModelRouteDTO {
    @NotBlank @Size(max = 32) private String routeKey;
    @NotBlank @Size(max = 128) private String model;
    private Boolean enabled = true;
    @Min(0) @Max(100) private Integer rolloutPercent = 100;
}
