package com.shanyuefang.agent.domain.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateSessionDTO {
    @Size(max = 128)
    private String title;
    @Size(max = 4000)
    private String context;
}
