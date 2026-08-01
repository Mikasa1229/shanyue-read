package com.shanyuefang.agent.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_agent_evaluation_run")
public class AgentEvaluationRun {
    @TableId
    private Long id;
    private Long initiatedBy;
    private String suiteName;
    private String status;
    private Integer totalCases;
    private Integer passedCases;
    private String resultJson;
    private LocalDateTime createdAt;
}
