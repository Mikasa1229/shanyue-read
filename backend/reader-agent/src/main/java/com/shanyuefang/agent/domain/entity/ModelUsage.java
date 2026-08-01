package com.shanyuefang.agent.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_model_usage")
public class ModelUsage {
    @TableId
    private Long id;
    private Long userId;
    private Long sessionId;
    private String provider;
    private String model;
    private String accessMode;
    private Integer inputTokens;
    private Integer outputTokens;
    private Integer systemTokens;
    private Integer historyTokens;
    private Integer graphTokens;
    private Integer communityTokens;
    private Integer evidenceTokens;
    private Integer toolTokens;
    private String tokenUsageSource;
    private Long platformCostMicros;
    private String status;
    private String requestId;
    private LocalDateTime createdAt;
}
