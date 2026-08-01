package com.shanyuefang.agent.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_knowledge_chunk")
public class KnowledgeChunk {
    private Long id;
    private Long documentId;
    private Long canonicalBookId;
    private Integer chapterIndex;
    private String content;
    private String keywords;
    private String embeddingJson;
    private String embeddingModelVersion;
    private LocalDateTime createdAt;
}
