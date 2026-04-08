package com.shanyuefang.novel.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AddToShelfDTO {
    private Long sourceId;
    private String sourceName;
    @NotBlank
    private String bookName;
    private String author;
    private String coverUrl;
    @NotBlank
    private String bookUrl;
}
