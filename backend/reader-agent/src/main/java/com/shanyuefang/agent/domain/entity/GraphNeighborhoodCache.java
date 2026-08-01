package com.shanyuefang.agent.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_graph_neighborhood_cache")
public class GraphNeighborhoodCache {
    private Long id;
    private Long canonicalBookId;
    private String cacheKey;
    private Integer currentChapter;
    private Integer maxEdges;
    private String edgesJson;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
