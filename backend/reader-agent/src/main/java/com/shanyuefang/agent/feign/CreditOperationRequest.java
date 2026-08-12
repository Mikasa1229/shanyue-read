package com.shanyuefang.agent.feign;

import lombok.Data;

@Data
public class CreditOperationRequest {
    private Long userId;
    private int amount;
    private String requestId;
    private String reason;
}
