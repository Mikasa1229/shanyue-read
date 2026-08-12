package com.shanyuefang.agent.domain.dto;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
@Data public class RecommendationExperimentDTO { private Boolean enabled; @Min(0) @Max(100) private Integer treatmentPercent; }
