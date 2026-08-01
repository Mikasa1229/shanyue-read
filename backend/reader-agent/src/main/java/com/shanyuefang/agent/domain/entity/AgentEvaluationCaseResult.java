package com.shanyuefang.agent.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** Immutable evidence from a model-answer evaluation case. */
@Data
@TableName("t_agent_evaluation_case_result")
public class AgentEvaluationCaseResult {
    @TableId private Long id;
    private Long runId;
    private String caseId;
    private String category;
    private String status;
    private Integer score;
    private String evidenceJson;
    private LocalDateTime createdAt;
}
