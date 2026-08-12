package com.shanyuefang.agent.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("t_agent_model_route")
public class AgentModelRoute {
    private Long id; private String routeKey; private String model; private Boolean enabled;
    private Integer rolloutPercent; private Long updatedBy; private LocalDateTime createdAt; private LocalDateTime updatedAt;
}
