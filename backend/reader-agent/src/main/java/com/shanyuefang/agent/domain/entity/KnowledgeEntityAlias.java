package com.shanyuefang.agent.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_knowledge_entity_alias")
public class KnowledgeEntityAlias {
    private Long id;
    private Long canonicalBookId;
    private Long nodeId;
    private String alias;
    private String nodeType;
    private Integer firstChapter;
    private String evidence;
    private Double confidence;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
