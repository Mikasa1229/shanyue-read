package com.shanyuefang.agent.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_recommendation_exposure")
public class RecommendationExposure {
    @TableId
    private Long id;
    private Long userId;
    private String experimentKey;
    private String experimentVariant;
    private Integer recommendationCount;
    private LocalDateTime createdAt;
}
