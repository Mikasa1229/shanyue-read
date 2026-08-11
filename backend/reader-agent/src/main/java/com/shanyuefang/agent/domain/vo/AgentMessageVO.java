package com.shanyuefang.agent.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class AgentMessageVO {
    private Long id;
    private String role;
    private String content;
    private List<CitationVO> citations;
    private List<BookReferenceVO> bookReferences;
    private String generationStatus;
    private LocalDateTime createdAt;
}
