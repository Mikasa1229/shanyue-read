package com.shanyuefang.agent.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class GraphReviewClaimVO {
    private String claimType;
    private Long id;
    private String label;
    private Integer firstChapter;
    private String evidence;
    private Double confidence;
    private String sourceModelVersion;
    private String reviewStatus;
}
