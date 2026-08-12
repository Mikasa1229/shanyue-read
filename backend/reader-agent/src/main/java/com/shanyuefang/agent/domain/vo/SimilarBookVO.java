package com.shanyuefang.agent.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/** Explainable similarity result derived only from indexed work profiles. */
@Data
@AllArgsConstructor
public class SimilarBookVO {
    private Long canonicalBookId;
    private double similarity;
    private List<String> sharedKeywords;
    private String explanation;
    private String title;
    private String author;
    private String coverUrl;
    private String summary;
}
