package com.shanyuefang.novel.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class StartReadingSessionDTO {
    @NotBlank
    private String bookUrl;
}
