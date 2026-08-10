package com.shanyuefang.agent.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class AgentReplyVO {
    private String requestId;
    private String content;
    private String mode;
    private boolean degraded;
    private List<CitationVO> citations;
    private List<BookReferenceVO> bookReferences;
}
