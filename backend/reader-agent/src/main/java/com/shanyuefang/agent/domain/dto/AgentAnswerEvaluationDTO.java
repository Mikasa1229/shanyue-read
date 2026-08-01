package com.shanyuefang.agent.domain.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** CI or an evaluator submits real model outputs plus their bounded evidence. */
@Data
public class AgentAnswerEvaluationDTO {
    @NotBlank @Size(max = 64) private String suiteName = "model-answer-quality";
    @NotBlank @Size(max = 64) private String model;
    @NotBlank @Size(max = 64) private String promptVersion;
    @NotEmpty @Valid private List<CaseInput> cases;

    @Data
    public static class CaseInput {
        @NotBlank @Size(max = 96) private String caseId;
        @NotBlank @Size(max = 32) private String category;
        @NotBlank @Size(max = 4000) private String prompt;
        @NotBlank @Size(max = 12000) private String answer;
        @NotNull private Integer readingBoundaryChapter;
        /** Required for evidence-based cases so submitted citations can be checked against indexed work data. */
        private Long canonicalBookId;
        private List<Integer> citationChapters = List.of();
        private List<Long> recommendationBookIds = List.of();
        private Boolean toolWritePerformed = false;
        private Boolean scopedToRequestingUser = true;
    }
}
