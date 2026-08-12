package com.shanyuefang.agent.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** Versioned platform-owned model price; BYOK calls never use this record for billing. */
@Data
@TableName("t_agent_model_pricing")
public class AgentModelPricing {
    @TableId
    private Long id;
    private String provider;
    private String model;
    private Long inputCostMicrosPerThousand;
    private Long outputCostMicrosPerThousand;
    private String pricingVersion;
    private Boolean enabled;
    private Long updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
