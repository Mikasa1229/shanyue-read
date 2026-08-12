package com.shanyuefang.agent.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class GraphClaimReviewDTO {
    @NotBlank
    private String claimType;
    @NotNull
    private Long claimId;
    @NotBlank
    private String reviewStatus;
}
