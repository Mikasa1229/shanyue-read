package com.shanyuefang.agent.domain.entity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;
@Data @TableName("t_recommendation_experiment") public class RecommendationExperiment { private Long id; private String experimentKey; private Boolean enabled; private Integer treatmentPercent; private LocalDateTime createdAt; private LocalDateTime updatedAt; }
