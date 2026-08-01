package com.shanyuefang.user.domain.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreditOperationDTO {
    @NotNull
    private Long userId;
    @Min(1)
    private int amount;
    @NotBlank
    private String requestId;
    private String reason;
}
