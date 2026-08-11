package com.shanyuefang.novel.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateProgressDTO {
    private Long canonicalBookId;
    private Long sourceId;
    private String sourceName;
    @NotBlank
    private String bookUrl;
    @NotBlank
    private String chapterName;
    @NotBlank
    private String chapterUrl;
    private Integer chapterIndex;
    private Integer totalChapters;
}
