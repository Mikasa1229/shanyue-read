package com.shanyuefang.agent.domain.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class SaveShelfGroupDTO {
    @NotNull private Long canonicalBookId;
    @Pattern(regexp = "FOLLOWING|SHORT_SESSION|WEEKEND|RESTART|CLEANUP|AUTO")
    private String groupCode;
}
