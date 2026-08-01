package com.shanyuefang.novel.service;

import com.shanyuefang.novel.domain.dto.ResolveCanonicalBookDTO;
import com.shanyuefang.novel.domain.vo.CanonicalBookVO;
import com.shanyuefang.novel.domain.vo.CanonicalMergeReviewVO;
import com.shanyuefang.novel.domain.vo.CanonicalBookDetailVO;
import com.shanyuefang.novel.domain.dto.ReviewCanonicalMergeDTO;

import java.util.List;

public interface CanonicalBookService {
    CanonicalBookVO resolve(ResolveCanonicalBookDTO dto);
    List<CanonicalMergeReviewVO> pendingReviews(int limit);
    void reviewMerge(long reviewId, ReviewCanonicalMergeDTO dto);
    List<Long> detachSource(long sourceId);
    CanonicalBookDetailVO detail(long canonicalBookId);
}
