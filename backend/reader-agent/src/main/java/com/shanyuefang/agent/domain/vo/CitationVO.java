package com.shanyuefang.agent.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A reading-safe source excerpt shown alongside an Agent answer. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CitationVO {
    private Long canonicalBookId;
    private Integer chapterIndex;
    private String excerpt;
}
