package com.shanyuefang.novel.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ContentVersionStatusDTO {
    @NotNull
    private Long canonicalBookId;
    @NotNull
    private Integer chapterIndex;
    @NotBlank
    private String contentHash;
    @NotBlank
    private String indexStatus;
}
