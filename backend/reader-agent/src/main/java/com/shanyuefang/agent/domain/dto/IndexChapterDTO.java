package com.shanyuefang.agent.domain.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class IndexChapterDTO {
    @NotNull
    private Long canonicalBookId;
    @NotNull @Min(0)
    private Integer chapterIndex;
    @NotBlank @Size(max = 200000)
    private String content;
    @Size(max = 64)
    private String contentVersion = "unknown";
}
