package com.shanyuefang.novel.controller;

import com.shanyuefang.common.result.R;
import com.shanyuefang.novel.domain.dto.ResolveCanonicalBookDTO;
import com.shanyuefang.novel.domain.vo.CanonicalBookVO;
import com.shanyuefang.novel.domain.vo.CanonicalBookDetailVO;
import com.shanyuefang.novel.domain.vo.CanonicalMergeReviewVO;
import com.shanyuefang.novel.domain.dto.ReviewCanonicalMergeDTO;
import com.shanyuefang.novel.service.CanonicalBookService;
import com.shanyuefang.novel.service.NovelInternalAccess;
import com.shanyuefang.novel.mapper.CanonicalBookMapper;
import com.shanyuefang.novel.domain.entity.CanonicalBook;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/internal/books/canonical")
@RequiredArgsConstructor
public class InternalCanonicalBookController {
    private final CanonicalBookService canonicalBookService;
    private final NovelInternalAccess internalAccess;
    private final CanonicalBookMapper canonicalBookMapper;

    @PostMapping("/resolve")
    public R<CanonicalBookVO> resolve(@org.springframework.web.bind.annotation.RequestHeader("X-Agent-Internal-Token") String token,
                                      @Valid @RequestBody ResolveCanonicalBookDTO dto) {
        internalAccess.require(token);
        return R.ok(canonicalBookService.resolve(dto));
    }

    @GetMapping("/{canonicalBookId}")
    public R<CanonicalBookDetailVO> detail(@org.springframework.web.bind.annotation.RequestHeader("X-Agent-Internal-Token") String token,
                                            @PathVariable long canonicalBookId) {
        internalAccess.require(token);
        return R.ok(canonicalBookService.detail(canonicalBookId));
    }

    @GetMapping("/search")
    public R<List<Map<String, Object>>> search(@org.springframework.web.bind.annotation.RequestHeader("X-Agent-Internal-Token") String token,
                                                @RequestParam String keyword, @RequestParam(defaultValue = "6") int limit) {
        internalAccess.require(token);
        String value = keyword == null ? "" : keyword.trim();
        if (value.isBlank()) return R.ok(List.of());
        return R.ok(canonicalBookMapper.selectList(com.baomidou.mybatisplus.core.toolkit.Wrappers.<CanonicalBook>lambdaQuery()
                        .ne(CanonicalBook::getMergeStatus, "MERGED").and(query -> query.like(CanonicalBook::getTitle, value)
                                .or().like(CanonicalBook::getAuthor, value)).last("LIMIT " + Math.max(1, Math.min(limit, 12))))
                .stream().map(book -> Map.<String, Object>of("canonicalBookId", book.getId(), "title", book.getTitle(),
                        "author", book.getAuthor() == null ? "" : book.getAuthor(), "summary", book.getSummary() == null ? "" : book.getSummary())).toList());
    }

    @GetMapping("/merge-reviews")
    public R<List<CanonicalMergeReviewVO>> pendingReviews(@org.springframework.web.bind.annotation.RequestHeader("X-Agent-Internal-Token") String token,
                                                           @RequestParam(defaultValue = "30") int limit) {
        internalAccess.require(token);
        return R.ok(canonicalBookService.pendingReviews(limit));
    }

    @PostMapping("/merge-reviews/{reviewId}")
    public R<Void> review(@org.springframework.web.bind.annotation.RequestHeader("X-Agent-Internal-Token") String token,
                          @PathVariable long reviewId, @Valid @RequestBody ReviewCanonicalMergeDTO dto) {
        internalAccess.require(token);
        canonicalBookService.reviewMerge(reviewId, dto);
        return R.ok();
    }
}
