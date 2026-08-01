package com.shanyuefang.agent.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_knowledge_graph_edge")
public class KnowledgeGraphEdge {
    private Long id;
    private Long canonicalBookId;
    private Long sourceNodeId;
    private Long targetNodeId;
    private String relation;
    private Integer firstChapter;
    private Integer lastChapter;
    private String evidence;
    private Double confidence;
    private String sourceModelVersion;
    private String reviewStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
