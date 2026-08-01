package com.shanyuefang.novel.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AddFavoriteDTO {
    private Long sourceId;
    private Long canonicalBookId;
    private String sourceName;
    @NotBlank
    private String bookName;
    private String author;
    private String coverUrl;
    @NotBlank
    private String bookUrl;
}
