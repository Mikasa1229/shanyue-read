package com.shanyuefang.agent.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_knowledge_graph_node")
public class KnowledgeGraphNode {
    private Long id;
    private Long canonicalBookId;
    private String name;
    private String nodeType;
    private String identityKey;
    private Integer firstChapter;
    private Integer lastChapter;
    private String evidence;
    private Double confidence;
    private String sourceModelVersion;
    private String reviewStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
