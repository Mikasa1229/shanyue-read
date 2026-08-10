package com.shanyuefang.agent.domain.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SaveShelfGroupDTO {
    @NotNull private Long canonicalBookId;
    @Size(min = 1, max = 32)
    private String groupName;
}
