package com.shanyuefang.novel.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CanonicalMergeReviewVO {
    private Long id;
    private Long sourceCanonicalBookId;
    private Long candidateCanonicalBookId;
    private Double confidence;
    private String reason;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime reviewedAt;
}
