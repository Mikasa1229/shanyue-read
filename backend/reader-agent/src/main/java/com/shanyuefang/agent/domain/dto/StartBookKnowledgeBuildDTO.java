package com.shanyuefang.agent.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class StartBookKnowledgeBuildDTO {
    @NotBlank private String modelMode;
    private Long modelConfigId;
    /** Human-facing, one-based chapter range. Both boundaries are inclusive. */
    @Min(value = 1, message = "起始章节必须从第 1 章开始")
    private Integer startChapter;
    @Min(value = 1, message = "结束章节必须从第 1 章开始")
    private Integer endChapter;
    /** Public by default so the resulting book-level LightRAG index can be reused. */
    private Boolean sharePublic = true;
}
