package com.shanyuefang.agent.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ClueVO {
    private Integer chapterIndex;
    private String excerpt;
    private String signal;
    private String status;
    private Integer resolvedChapter;
    private String resolutionEvidence;
}
