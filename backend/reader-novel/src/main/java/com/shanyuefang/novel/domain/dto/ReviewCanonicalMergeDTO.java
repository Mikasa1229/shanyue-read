package com.shanyuefang.novel.domain.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ReviewCanonicalMergeDTO {
    @NotBlank
    @Pattern(regexp = "APPROVE|REJECT")
    private String action;
}
