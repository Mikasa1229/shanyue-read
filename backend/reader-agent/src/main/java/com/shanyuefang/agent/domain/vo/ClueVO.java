package com.shanyuefang.agent.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class ClueVO {
    private Integer chapterIndex;
    private String excerpt;
    private String signal;
    private String status;
    private Integer resolvedChapter;
    private String resolutionEvidence;
    private List<ClueProgressVO> progress;

    public ClueVO(Integer chapterIndex, String excerpt, String signal, String status,
                  Integer resolvedChapter, String resolutionEvidence) {
        this(chapterIndex, excerpt, signal, status, resolvedChapter, resolutionEvidence, List.of());
    }
}
