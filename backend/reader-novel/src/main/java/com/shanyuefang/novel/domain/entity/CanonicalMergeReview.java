package com.shanyuefang.novel.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_canonical_merge_review")
public class CanonicalMergeReview {
    @TableId
    private Long id;
    private Long sourceCanonicalBookId;
    private Long candidateCanonicalBookId;
    private Double confidence;
    private String reason;
    private String status;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
