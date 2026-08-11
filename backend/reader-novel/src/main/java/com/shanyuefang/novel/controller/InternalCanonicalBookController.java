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
import com.shanyuefang.novel.domain.vo.SearchBookVO;
import com.shanyuefang.novel.service.BookSourceService;
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
    private final BookSourceService bookSourceService;
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
        int boundedLimit = Math.max(1, Math.min(limit, 12));
        List<String> terms = searchTerms(value);
        List<String> exclusions = exclusionTerms(value);
        List<CanonicalBook> catalog = canonicalBookMapper.selectList(com.baomidou.mybatisplus.core.toolkit.Wrappers.<CanonicalBook>lambdaQuery()
                .ne(CanonicalBook::getMergeStatus, "MERGED").last("LIMIT 500"));
        List<Map<String, Object>> matched = readableSearchResults(catalog.stream()
                .filter(book -> !isExcluded(book, exclusions))
                .map(book -> Map.entry(book, relevance(book, value, terms)))
                .filter(entry -> entry.getValue() > 0)
                .sorted(Map.Entry.<CanonicalBook, Integer>comparingByValue().reversed())
                .map(Map.Entry::getKey), boundedLimit);
        // A function call must be able to discover works that have not entered the local
        // canonical catalog yet. Source search resolves every result to a canonical work.
        if (matched.isEmpty()) {
            matched = sourceDiscoveryResults(value, exclusions, boundedLimit);
        }
        return R.ok(matched);
    }

    private List<Map<String, Object>> sourceDiscoveryResults(String query, List<String> exclusions, int limit) {
        String keyword = discoveryKeyword(query);
        if (keyword.isBlank()) return List.of();
        return bookSourceService.aggregateSearch(keyword, 1).stream()
                .filter(book -> book.getCanonicalBookId() != null && book.getSourceId() != null
                        && book.getBookUrl() != null && !book.getBookUrl().isBlank())
                .filter(book -> !isExcluded(book.getName(), book.getAuthor(), exclusions))
                .collect(java.util.stream.Collectors.toMap(
                        book -> book.getCanonicalBookId() + "|" + book.getSourceId() + "|" + book.getBookUrl(),
                        this::sourceResult, (left, right) -> left, java.util.LinkedHashMap::new))
                .values().stream().limit(limit).toList();
    }

    private Map<String, Object> sourceResult(SearchBookVO book) {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("canonicalBookId", book.getCanonicalBookId());
        result.put("title", book.getName());
        result.put("author", book.getAuthor() == null ? "" : book.getAuthor());
        result.put("coverUrl", book.getCoverUrl() == null ? "" : book.getCoverUrl());
        result.put("summary", book.getIntro() == null ? "" : book.getIntro());
        result.put("sourceId", book.getSourceId());
        result.put("sourceBookUrl", book.getBookUrl());
        return result;
    }

    static String discoveryKeyword(String request) {
        if (request == null) return "";
        String normalized = request.replaceAll("本会话最近约束[:：].*$", "")
                .replaceAll("(?:请|帮我|直接|推荐|搜索|搜书|找书|一本|几本|适合|今晚|读的|想读|想看|作品|小说|书源|里面|平台|可读|不要|不看|排除|别推荐|从)", " ")
                .replaceAll("[《》\"'，。；！？,:：;]", " ").replaceAll("\\s+", " ").trim();
        return normalized.length() > 40 ? normalized.substring(0, 40) : normalized;
    }

    private List<Map<String, Object>> readableSearchResults(java.util.stream.Stream<CanonicalBook> books, int limit) {
        return books.limit(80)
                .map(book -> canonicalBookService.detail(book.getId()))
                .filter(java.util.Objects::nonNull)
                .filter(book -> book.getSourceId() != null && book.getSourceBookUrl() != null && !book.getSourceBookUrl().isBlank())
                .map(book -> {
                    Map<String, Object> result = new java.util.LinkedHashMap<>();
                    result.put("canonicalBookId", book.getCanonicalBookId());
                    result.put("title", book.getTitle());
                    result.put("author", book.getAuthor() == null ? "" : book.getAuthor());
                    result.put("coverUrl", book.getCoverUrl() == null ? "" : book.getCoverUrl());
                    result.put("summary", book.getSummary() == null ? "" : book.getSummary());
                    result.put("sourceId", book.getSourceId());
                    result.put("sourceBookUrl", book.getSourceBookUrl());
                    return result;
                }).limit(limit).toList();
    }

    private List<String> searchTerms(String request) {
        String normalized = request.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("(请|帮我|一下|一本|几本|一点|比较|好看|有名|小说|网文|作品|书源|里面|能够|可以|搜索|搜到|推荐|想看|我要|要看|完结|短篇|长篇|换一本|换个|直接|热门|点击|引用|链接)", " ");
        return java.util.Arrays.stream(normalized.split("[^\\p{IsHan}a-z0-9]+"))
                .map(String::trim).filter(term -> term.length() >= 2).distinct().limit(8).toList();
    }

    static List<String> exclusionTerms(String request) {
        if (request == null || request.isBlank()) return List.of();
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(?:不要|不看|排除|别推荐)(?:《)?([^》，。；！？\\n]{1,24})(?:》)?")
                .matcher(request.toLowerCase(java.util.Locale.ROOT));
        List<String> values = new java.util.ArrayList<>();
        while (matcher.find()) {
            String value = matcher.group(1).replaceAll("(?:相关的?|这本|这类|作品|小说|书)$", "").trim();
            if (!value.isBlank() && !value.contains("书架")) values.add(value);
        }
        return values.stream().distinct().limit(8).toList();
    }

    private boolean isExcluded(CanonicalBook book, List<String> exclusions) {
        return isExcluded(book.getTitle(), book.getAuthor(), exclusions);
    }

    private boolean isExcluded(String bookTitle, String bookAuthor, List<String> exclusions) {
        if (exclusions.isEmpty()) return false;
        String title = bookTitle == null ? "" : bookTitle.toLowerCase(java.util.Locale.ROOT);
        String author = bookAuthor == null ? "" : bookAuthor.toLowerCase(java.util.Locale.ROOT);
        return exclusions.stream().anyMatch(value -> title.contains(value) || author.contains(value));
    }

    private int relevance(CanonicalBook book, String raw, List<String> terms) {
        String title = book.getTitle() == null ? "" : book.getTitle().toLowerCase(java.util.Locale.ROOT);
        String author = book.getAuthor() == null ? "" : book.getAuthor().toLowerCase(java.util.Locale.ROOT);
        String summary = book.getSummary() == null ? "" : book.getSummary().toLowerCase(java.util.Locale.ROOT);
        String normalizedRaw = raw.toLowerCase(java.util.Locale.ROOT).trim();
        int score = title.equals(normalizedRaw) ? 100 : title.contains(normalizedRaw) ? 50 : author.contains(normalizedRaw) ? 35 : 0;
        for (String term : terms) {
            if (title.contains(term)) score += 14;
            if (author.contains(term)) score += 8;
            if (summary.contains(term)) score += 3;
        }
        // A generic recommendation still needs real readable candidates instead of an empty tool result.
        return score == 0 && terms.isEmpty() ? 1 : score;
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
