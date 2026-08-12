package com.shanyuefang.agent.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_agent_recommendation_feedback")
public class RecommendationFeedback {
    @TableId
    private Long id;
    private Long userId;
    private Long canonicalBookId;
    private String action;
    private String experimentVariant;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
