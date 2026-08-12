package com.shanyuefang.agent.domain.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AgentModelPricingDTO {
    @NotBlank @Size(max = 64) private String provider;
    @NotBlank @Size(max = 128) private String model;
    @Min(0) private Long inputCostMicrosPerThousand;
    @Min(0) private Long outputCostMicrosPerThousand;
    @NotBlank @Size(max = 64) private String pricingVersion;
    private Boolean enabled = true;
}
