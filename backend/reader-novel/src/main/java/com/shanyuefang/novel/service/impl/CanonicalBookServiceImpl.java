package com.shanyuefang.novel.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.shanyuefang.common.util.SnowflakeIdUtil;
import com.shanyuefang.novel.domain.dto.ResolveCanonicalBookDTO;
import com.shanyuefang.novel.domain.entity.BookSourceMapping;
import com.shanyuefang.novel.domain.entity.CanonicalBook;
import com.shanyuefang.novel.domain.entity.CanonicalMergeReview;
import com.shanyuefang.novel.domain.dto.ReviewCanonicalMergeDTO;
import com.shanyuefang.novel.domain.vo.CanonicalBookVO;
import com.shanyuefang.novel.domain.vo.CanonicalMergeReviewVO;
import com.shanyuefang.novel.domain.vo.CanonicalBookDetailVO;
import com.shanyuefang.novel.mapper.BookSourceMappingMapper;
import com.shanyuefang.novel.mapper.CanonicalBookMapper;
import com.shanyuefang.novel.mapper.CanonicalMergeReviewMapper;
import com.shanyuefang.novel.mapper.BookshelfBookMapper;
import com.shanyuefang.novel.mapper.FavoriteBookMapper;
import com.shanyuefang.novel.mapper.BookContentVersionMapper;
import com.shanyuefang.novel.domain.entity.BookshelfBook;
import com.shanyuefang.novel.domain.entity.FavoriteBook;
import com.shanyuefang.novel.service.CanonicalBookService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

@Service
@RequiredArgsConstructor
public class CanonicalBookServiceImpl implements CanonicalBookService {
    private final CanonicalBookMapper canonicalBookMapper;
    private final BookSourceMappingMapper mappingMapper;
    private final CanonicalMergeReviewMapper reviewMapper;
    private final BookshelfBookMapper bookshelfBookMapper;
    private final FavoriteBookMapper favoriteBookMapper;
    private final BookContentVersionMapper contentVersionMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CanonicalBookVO resolve(ResolveCanonicalBookDTO dto) {
        BookSourceMapping mapping = mappingMapper.selectOne(Wrappers.<BookSourceMapping>lambdaQuery()
                .eq(BookSourceMapping::getSourceId, dto.getSourceId())
                .eq(BookSourceMapping::getSourceBookUrl, dto.getBookUrl()));
        if (mapping != null) {
            return new CanonicalBookVO(mapping.getCanonicalBookId(), false);
        }

        String normalizedTitle = normalize(dto.getTitle());
        String normalizedAuthor = normalize(dto.getAuthor());
        CanonicalBook candidate = canonicalBookMapper.selectList(Wrappers.<CanonicalBook>lambdaQuery()
                        .eq(CanonicalBook::getNormalizedTitle, normalizedTitle)
                        .ne(CanonicalBook::getMergeStatus, "MERGED"))
                .stream().max(java.util.Comparator.comparingDouble(book -> confidence(book, dto, normalizedAuthor))).orElse(null);
        double confidence = candidate == null ? 0D : confidence(candidate, dto, normalizedAuthor);
        CanonicalBook canonical = candidate;
        boolean created = false;
        if (canonical == null || confidence < 0.95D) {
            canonical = createCanonical(dto, normalizedTitle, normalizedAuthor,
                    candidate == null ? 1.0D : confidence, candidate == null ? "VERIFIED" : "PENDING_REVIEW");
            created = true;
            if (candidate != null && confidence >= 0.65D) createReview(canonical, candidate, confidence);
        }
        mapping = new BookSourceMapping();
        mapping.setId(SnowflakeIdUtil.next());
        mapping.setCanonicalBookId(canonical.getId());
        mapping.setSourceId(dto.getSourceId());
        mapping.setSourceBookUrl(dto.getBookUrl());
        mapping.setSourceTitle(dto.getTitle());
        mapping.setSourceAuthor(dto.getAuthor());
        mappingMapper.insert(mapping);
        return new CanonicalBookVO(canonical.getId(), created);
    }

    @Override
    public List<CanonicalMergeReviewVO> pendingReviews(int limit) {
        return reviewMapper.selectList(Wrappers.<CanonicalMergeReview>lambdaQuery().eq(CanonicalMergeReview::getStatus, "PENDING")
                        .orderByAsc(CanonicalMergeReview::getCreatedAt).last("LIMIT " + Math.max(1, Math.min(limit, 100))))
                .stream().map(this::toReviewVO).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reviewMerge(long reviewId, ReviewCanonicalMergeDTO dto) {
        CanonicalMergeReview review = reviewMapper.selectById(reviewId);
        if (review == null || !"PENDING".equals(review.getStatus())) throw new IllegalArgumentException("Merge review is not pending");
        CanonicalBook source = canonicalBookMapper.selectById(review.getSourceCanonicalBookId());
        if ("APPROVE".equals(dto.getAction())) {
            mappingMapper.update(null, Wrappers.<BookSourceMapping>lambdaUpdate()
                    .eq(BookSourceMapping::getCanonicalBookId, source.getId()).set(BookSourceMapping::getCanonicalBookId, review.getCandidateCanonicalBookId()));
            // Keep historical personal records navigable after two works are consolidated.
            bookshelfBookMapper.update(null, Wrappers.<BookshelfBook>lambdaUpdate()
                    .eq(BookshelfBook::getCanonicalBookId, source.getId()).set(BookshelfBook::getCanonicalBookId, review.getCandidateCanonicalBookId()));
            favoriteBookMapper.update(null, Wrappers.<FavoriteBook>lambdaUpdate()
                    .eq(FavoriteBook::getCanonicalBookId, source.getId()).set(FavoriteBook::getCanonicalBookId, review.getCandidateCanonicalBookId()));
            source.setMergeStatus("MERGED");
        } else source.setMergeStatus("VERIFIED");
        canonicalBookMapper.updateById(source);
        review.setStatus(dto.getAction().equals("APPROVE") ? "APPROVED" : "REJECTED");
        review.setReviewedAt(LocalDateTime.now()); review.setUpdatedAt(LocalDateTime.now()); reviewMapper.updateById(review);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<Long> detachSource(long sourceId) {
        List<Long> affected = mappingMapper.selectList(Wrappers.<BookSourceMapping>lambdaQuery()
                        .eq(BookSourceMapping::getSourceId, sourceId)).stream()
                .map(BookSourceMapping::getCanonicalBookId).distinct().toList();
        mappingMapper.delete(Wrappers.<BookSourceMapping>lambdaQuery().eq(BookSourceMapping::getSourceId, sourceId));
        List<Long> orphaned = new java.util.ArrayList<>();
        for (Long canonicalBookId : affected) {
            if (mappingMapper.selectCount(Wrappers.<BookSourceMapping>lambdaQuery()
                    .eq(BookSourceMapping::getCanonicalBookId, canonicalBookId)) == 0) {
                // The source-side version ledger has an FK to the work and must go before the canonical row.
                contentVersionMapper.delete(Wrappers.<com.shanyuefang.novel.domain.entity.BookContentVersion>lambdaQuery()
                        .eq(com.shanyuefang.novel.domain.entity.BookContentVersion::getCanonicalBookId, canonicalBookId));
                canonicalBookMapper.deleteById(canonicalBookId);
                // There is no readable canonical work left; do not retain a dangling personal projection.
                bookshelfBookMapper.update(null, Wrappers.<BookshelfBook>lambdaUpdate()
                        .eq(BookshelfBook::getCanonicalBookId, canonicalBookId).set(BookshelfBook::getCanonicalBookId, null));
                favoriteBookMapper.update(null, Wrappers.<FavoriteBook>lambdaUpdate()
                        .eq(FavoriteBook::getCanonicalBookId, canonicalBookId).set(FavoriteBook::getCanonicalBookId, null));
                orphaned.add(canonicalBookId);
            }
        }
        return orphaned;
    }

    @Override
    public CanonicalBookDetailVO detail(long canonicalBookId) {
        CanonicalBook book = canonicalBookMapper.selectById(canonicalBookId);
        if (book == null || "MERGED".equals(book.getMergeStatus())) return null;
        BookSourceMapping mapping = mappingMapper.selectOne(Wrappers.<BookSourceMapping>lambdaQuery()
                .eq(BookSourceMapping::getCanonicalBookId, canonicalBookId).last("LIMIT 1"));
        return new CanonicalBookDetailVO(book.getId(), book.getTitle(), book.getAuthor(), book.getCoverUrl(), book.getSummary(),
                mapping == null ? null : mapping.getSourceId(), mapping == null ? null : mapping.getSourceBookUrl());
    }

    private CanonicalBook createCanonical(ResolveCanonicalBookDTO dto, String title, String author, double confidence, String status) {
        CanonicalBook value = new CanonicalBook(); value.setId(SnowflakeIdUtil.next()); value.setNormalizedTitle(title);
        value.setNormalizedAuthor(author); value.setTitle(dto.getTitle().trim()); value.setAuthor(dto.getAuthor());
        value.setCoverUrl(dto.getCoverUrl()); value.setSummary(dto.getSummary()); value.setMergeConfidence(confidence); value.setMergeStatus(status);
        canonicalBookMapper.insert(value); return value;
    }

    private void createReview(CanonicalBook source, CanonicalBook candidate, double confidence) {
        CanonicalMergeReview review = new CanonicalMergeReview(); review.setId(SnowflakeIdUtil.next());
        review.setSourceCanonicalBookId(source.getId()); review.setCandidateCanonicalBookId(candidate.getId()); review.setConfidence(confidence);
        review.setReason("Title matches; automatic merge withheld because identity confidence is below the high-confidence threshold.");
        review.setStatus("PENDING"); review.setCreatedAt(LocalDateTime.now()); review.setUpdatedAt(LocalDateTime.now()); reviewMapper.insert(review);
    }

    private double confidence(CanonicalBook book, ResolveCanonicalBookDTO dto, String normalizedAuthor) {
        double value = 0.70D;
        if (!normalizedAuthor.isBlank() && normalizedAuthor.equals(book.getNormalizedAuthor())) value += 0.28D;
        if (summaryOverlap(book.getSummary(), dto.getSummary()) >= 0.30D) value += 0.02D;
        return value;
    }

    private double summaryOverlap(String left, String right) {
        if (left == null || right == null || left.isBlank() || right.isBlank()) return 0D;
        Set<String> leftTerms = terms(left); Set<String> rightTerms = terms(right); Set<String> shared = new HashSet<>(leftTerms); shared.retainAll(rightTerms);
        Set<String> union = new HashSet<>(leftTerms); union.addAll(rightTerms); return union.isEmpty() ? 0D : (double) shared.size() / union.size();
    }

    private Set<String> terms(String value) { Set<String> terms = new HashSet<>(); for (String term : value.toLowerCase(Locale.ROOT).split("[^\\p{IsHan}a-z0-9]+")) if (term.length() > 1) terms.add(term); return terms; }

    private CanonicalMergeReviewVO toReviewVO(CanonicalMergeReview value) { CanonicalMergeReviewVO vo = new CanonicalMergeReviewVO(); vo.setId(value.getId()); vo.setSourceCanonicalBookId(value.getSourceCanonicalBookId()); vo.setCandidateCanonicalBookId(value.getCandidateCanonicalBookId()); vo.setConfidence(value.getConfidence()); vo.setReason(value.getReason()); vo.setStatus(value.getStatus()); vo.setCreatedAt(value.getCreatedAt()); vo.setReviewedAt(value.getReviewedAt()); return vo; }

    private String normalize(String value) {
        if (value == null) return "";
        return value.toLowerCase(Locale.ROOT).replaceAll("[\\s\\p{Punct}]+", "").trim();
    }
}
