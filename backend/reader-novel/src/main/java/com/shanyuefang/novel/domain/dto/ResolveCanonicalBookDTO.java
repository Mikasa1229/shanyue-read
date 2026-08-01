package com.shanyuefang.novel.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ResolveCanonicalBookDTO {
    @NotNull
    private Long sourceId;
    @NotBlank
    private String bookUrl;
    @NotBlank
    private String title;
    private String author;
    private String coverUrl;
    private String summary;
}
