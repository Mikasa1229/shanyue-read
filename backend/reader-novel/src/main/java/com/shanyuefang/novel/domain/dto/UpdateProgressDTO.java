package com.shanyuefang.novel.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateProgressDTO {
    @NotBlank
    private String bookUrl;
    @NotBlank
    private String chapterName;
    @NotBlank
    private String chapterUrl;
    private Integer chapterIndex;
    private Integer totalChapters;
}
