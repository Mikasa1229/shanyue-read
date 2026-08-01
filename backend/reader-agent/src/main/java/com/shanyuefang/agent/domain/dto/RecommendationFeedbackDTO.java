package com.shanyuefang.agent.domain.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class RecommendationFeedbackDTO {
    @NotNull
    private Long canonicalBookId;
    @Pattern(regexp = "LIKE|DISMISS|CLICK|OPEN|ADD_TO_SHELF|COMPLETE")
    private String action;
}
