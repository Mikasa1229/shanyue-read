package com.shanyuefang.novel.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ReadingHeartbeatDTO {
    @NotBlank
    private String sessionToken;
    private boolean pageVisible;
}
