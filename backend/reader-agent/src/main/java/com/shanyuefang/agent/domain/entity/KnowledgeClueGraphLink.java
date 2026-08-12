package com.shanyuefang.agent.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_knowledge_clue_graph_link")
public class KnowledgeClueGraphLink {
    private Long id;
    private Long canonicalBookId;
    private Long clueId;
    private Long nodeId;
    private String linkType;
    private Double confidence;
    private String evidence;
    private LocalDateTime createdAt;
}
