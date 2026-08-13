package com.shanyuefang.novel.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shanyuefang.common.exception.BusinessException;
import com.shanyuefang.common.result.ResultCode;
import com.shanyuefang.common.util.SnowflakeIdUtil;
import com.shanyuefang.novel.domain.entity.BookSource;
import com.shanyuefang.novel.domain.entity.BookSourceMapping;
import com.shanyuefang.novel.domain.entity.UserBookSourcePreference;
import com.shanyuefang.novel.domain.vo.BookChapterVO;
import com.shanyuefang.novel.domain.vo.BookSourceVO;
import com.shanyuefang.novel.domain.vo.SearchBookVO;
import com.shanyuefang.novel.domain.vo.AggregatedBookVO;
import com.shanyuefang.novel.domain.vo.BookSourceSummaryVO;
import com.shanyuefang.novel.engine.BookSourceModel;
import com.shanyuefang.novel.engine.HttpFetcher;
import com.shanyuefang.novel.engine.LegadoRuleEngine;
import com.shanyuefang.novel.mapper.BookSourceMapper;
import com.shanyuefang.novel.mapper.BookSourceMappingMapper;
import com.shanyuefang.novel.mapper.UserBookSourcePreferenceMapper;
import com.shanyuefang.novel.service.BookSourceService;
import com.shanyuefang.novel.service.CanonicalBookService;
import com.shanyuefang.novel.util.CoverSnapshotUtil;
import com.shanyuefang.novel.domain.dto.ResolveCanonicalBookDTO;
import com.shanyuefang.novel.messaging.KnowledgeIndexPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookSourceServiceImpl extends ServiceImpl<BookSourceMapper, BookSource>
        implements BookSourceService {

    private static final ObjectMapper OM = new ObjectMapper();
    private static final long CHAPTER_CACHE_TTL_MS = 5 * 60 * 1000L;
    private static final ConcurrentHashMap<String, CachedChapters> CHAPTER_CACHE = new ConcurrentHashMap<>();
    private static final Cache<String, List<SearchBookVO>> AGGREGATE_SOURCE_RESULTS_CACHE = Caffeine.newBuilder()
            .maximumSize(200).expireAfterWrite(Duration.ofSeconds(45)).build();
    private final CanonicalBookService canonicalBookService;
    private final CoverSnapshotUtil coverSnapshotUtil;
    private final KnowledgeIndexPublisher knowledgeIndexPublisher;
    private final BookSourceMappingMapper mappingMapper;
    private final UserBookSourcePreferenceMapper preferenceMapper;
    @Qualifier("bookSourceSearchExecutor")
    private final Executor bookSourceSearchExecutor;

    private record CachedChapters(long cacheAt, List<BookChapterVO> chapters) {}

    // ─── 导入 ────────────────────────────────────────────────

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int importFromUrl(String url) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .connectTimeout(Duration.ofSeconds(15))
                    .build();
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("User-Agent", "Mozilla/5.0")
                    .GET().build();
            String body = client.send(req, HttpResponse.BodyHandlers.ofString()).body();
            return importFromJson(body);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "拉取书源失败：" + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int importFromJson(String json) {
        List<JsonNode> nodes = parseJsonToList(json);
        if (nodes.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "JSON 解析失败或内容为空");
        }

        int saved = 0;
        for (JsonNode node : nodes) {
            try {
                BookSourceModel model = OM.treeToValue(node, BookSourceModel.class);
                if (!StringUtils.hasText(model.getBookSourceUrl())) continue;
                // 小说类型才处理（0=小说，null 也当小说）
                Integer type = model.getBookSourceType();
                if (type != null && type != 0) continue;

                // 无法搜索的书源跳过
                if (!StringUtils.hasText(model.getSearchUrl())) continue;

                BookSource bs = new BookSource();
                bs.setId(SnowflakeIdUtil.next());
                bs.setSourceName(model.getBookSourceName());
                bs.setSourceUrl(model.getBookSourceUrl());
                bs.setSourceType(type != null ? type : 0);
                bs.setSourceGroup(model.getBookSourceGroup());
                bs.setEnabled(Boolean.TRUE.equals(model.getEnabled()) || model.getEnabled() == null);
                bs.setSourceJson(OM.writeValueAsString(node));

                // 按 sourceUrl 去重（存在则更新）
                BookSource existing = lambdaQuery()
                        .eq(BookSource::getSourceUrl, bs.getSourceUrl())
                        .one();
                if (existing != null) {
                    bs.setId(existing.getId());
                    updateById(bs);
                } else {
                    save(bs);
                }
                // Rules can change independently of a book URL. Do not serve a directory parsed
                // with an older rule set while the updated source is already searchable.
                invalidateChapterCache(bs.getId());
                saved++;
            } catch (Exception e) {
                log.warn("书源导入跳过（解析失败）: {}", e.getMessage());
            }
        }
        AGGREGATE_SOURCE_RESULTS_CACHE.invalidateAll();
        log.info("书源导入完成，共保存 {} 条", saved);
        return saved;
    }

    private void invalidateChapterCache(Long sourceId) {
        if (sourceId == null) return;
        String prefix = sourceId + "|";
        CHAPTER_CACHE.keySet().removeIf(key -> key.startsWith(prefix));
    }

    // ─── 管理 ─────────────────────────────────────────────────

    @Override
    public Page<BookSourceVO> listSources(long userId, int page, int size) {
        Page<BookSource> rawPage = baseMapper.selectPageForUser(new Page<>(page, size), userId);
        Page<BookSourceVO> result = new Page<>(rawPage.getCurrent(), rawPage.getSize(), rawPage.getTotal());
        java.util.Set<Long> disabledIds = disabledSourceIds(userId);
        result.setRecords(rawPage.getRecords().stream().map(source -> {
            BookSourceVO vo = toVO(source);
            vo.setEnabled(Boolean.TRUE.equals(source.getEnabled()) && !disabledIds.contains(source.getId()));
            return vo;
        }).toList());
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void toggleEnabled(long userId, Long id) {
        BookSource bs = getById(id);
        if (bs == null) throw new BusinessException(ResultCode.NOT_FOUND, "书源不存在");
        UserBookSourcePreference preference = preferenceMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserBookSourcePreference>()
                        .eq(UserBookSourcePreference::getUserId, userId)
                        .eq(UserBookSourcePreference::getSourceId, id));
        if (preference == null) {
            preference = new UserBookSourcePreference();
            preference.setUserId(userId);
            preference.setSourceId(id);
            preference.setDisabled(true);
            preferenceMapper.insert(preference);
        } else {
            preferenceMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserBookSourcePreference>()
                    .eq(UserBookSourcePreference::getUserId, userId)
                    .eq(UserBookSourcePreference::getSourceId, id));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteSource(Long id) {
        BookSource bs = getById(id);
        if (bs == null) throw new BusinessException(ResultCode.NOT_FOUND, "书源不存在");
        removeById(id);
        AGGREGATE_SOURCE_RESULTS_CACHE.invalidateAll();
        List<Long> orphanedWorks = canonicalBookService.detachSource(id);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { orphanedWorks.forEach(knowledgeIndexPublisher::publishDelete); }
        });
    }

    // ─── 搜索 ─────────────────────────────────────────────────

    @Override
    public List<SearchBookVO> search(Long sourceId, String keyword, int page) {
        BookSource bs = getEnabledSource(sourceId);
        return searchSource(bs, keyword, page, true);
    }

    /**
     * Aggregation must not synchronously download every remote cover or create records for every hit.
     * Those costly enrichments still happen when a user opens a specific source result.
     */
    private List<SearchBookVO> searchSource(BookSource bs, String keyword, int page, boolean enrichResult) {
        BookSourceModel model = parseModel(bs);

        String rawSearchUrl = model.getSearchUrl();
        if (!StringUtils.hasText(rawSearchUrl)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "该书源不支持搜索");
        }
        // JavaScript 规则搜索暂不支持
        if (rawSearchUrl.trim().startsWith("@js:") || rawSearchUrl.contains("<js>")) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "该书源使用 JavaScript 规则，暂不支持服务端搜索");
        }

        String url = rawSearchUrl
                .replace("{{key}}", encodeUrl(keyword))
                .replace("{{page}}", String.valueOf(page));

        String body;
        try {
            body = HttpFetcher.fetch(url, model.getHeader(), model.getBookSourceUrl(), model.getBookSourceCharset());
        } catch (Exception e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "搜索请求失败：" + e.getMessage());
        }

        // 提取书籍列表（兼容新旧格式）
        String listRule = model.effectiveSearchList();
        if (!StringUtils.hasText(listRule)) {
            log.warn("书源 {} 没有 searchList/ruleSearch.bookList 规则", bs.getSourceName());
            return List.of();
        }

        List<String> items = LegadoRuleEngine.extractList(listRule, body);
        List<SearchBookVO> results = new ArrayList<>();
        for (String item : items) {
            SearchBookVO vo = new SearchBookVO();
            vo.setSourceId(bs.getId());
            vo.setSourceName(bs.getSourceName());
            vo.setName(extractField(model.effectiveSearchName(), item));
            vo.setAuthor(displayAuthor(extractField(model.effectiveSearchAuthor(), item)));
            String coverUrl = resolveUrl(extractField(model.effectiveSearchCover(), item), model.getBookSourceUrl());
            vo.setCoverUrl(enrichResult ? snapshotCover(coverUrl) : coverUrl);
            vo.setIntro(extractField(model.effectiveSearchIntro(), item));
            vo.setKind(extractField(model.effectiveSearchKind(), item));
            vo.setLastChapter(extractField(model.effectiveSearchLastChapter(), item));
            vo.setBookUrl(resolveUrl(extractField(model.effectiveSearchBookUrl(), item), model.getBookSourceUrl()));
            if (enrichResult) resolveCanonical(vo);
            results.add(vo);
        }
        return results;
    }

    @Override
    public SearchBookVO getBookDetail(Long sourceId, String bookUrl) {
        BookSource bs = getEnabledSource(sourceId);
        BookSourceModel model = parseModel(bs);

        String body;
        try {
            body = HttpFetcher.fetch(bookUrl, model.getHeader(), model.getBookSourceUrl(), model.getBookSourceCharset());
        } catch (Exception e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "获取书籍详情失败：" + e.getMessage());
        }

        SearchBookVO vo = new SearchBookVO();
        vo.setSourceId(sourceId);
        vo.setSourceName(bs.getSourceName());
        vo.setBookUrl(bookUrl);

        BookSourceModel.BookInfoRule info = model.getRuleBookInfo();
        if (info != null) {
            vo.setName(extractField(info.getName(), body));
            vo.setAuthor(displayAuthor(extractField(info.getAuthor(), body)));
            vo.setCoverUrl(snapshotCover(resolveUrl(extractField(info.getCoverUrl(), body), model.getBookSourceUrl())));
            vo.setIntro(extractField(info.getIntro(), body));
            vo.setKind(extractField(info.getKind(), body));
            vo.setLastChapter(extractField(info.getLastChapter(), body));
            vo.setWordCount(extractField(info.getWordCount(), body));
        }

        // 兼容没有 ruleBookInfo 的书源，尽量用搜索规则做兜底
        vo.setName(firstNonBlank(vo.getName(), extractField(model.effectiveSearchName(), body), extractTitle(body)));
        vo.setAuthor(firstNonBlank(vo.getAuthor(), extractField(model.effectiveSearchAuthor(), body)));
        vo.setCoverUrl(snapshotCover(firstNonBlank(vo.getCoverUrl(), resolveUrl(extractField(model.effectiveSearchCover(), body), model.getBookSourceUrl()))));
        vo.setIntro(firstNonBlank(vo.getIntro(), extractField(model.effectiveSearchIntro(), body), extractMetaDescription(body)));
        vo.setKind(firstNonBlank(vo.getKind(), extractField(model.effectiveSearchKind(), body)));
        vo.setLastChapter(firstNonBlank(vo.getLastChapter(), extractField(model.effectiveSearchLastChapter(), body)));

        // 兜底：若简介/封面仍为空，尝试按书名再走一遍搜索并匹配 bookUrl
        if ((!StringUtils.hasText(vo.getIntro()) || !StringUtils.hasText(vo.getCoverUrl())) && StringUtils.hasText(vo.getName())) {
            try {
                List<SearchBookVO> candidates = search(sourceId, vo.getName(), 1);
                SearchBookVO matched = candidates.stream()
                        .filter(c -> StringUtils.hasText(c.getBookUrl()) && c.getBookUrl().equals(bookUrl))
                        .findFirst()
                        .orElse(candidates.stream().findFirst().orElse(null));
                if (matched != null) {
                    vo.setIntro(firstNonBlank(vo.getIntro(), matched.getIntro()));
                    vo.setCoverUrl(snapshotCover(firstNonBlank(vo.getCoverUrl(), matched.getCoverUrl())));
                    vo.setAuthor(firstNonBlank(vo.getAuthor(), matched.getAuthor()));
                    vo.setKind(firstNonBlank(vo.getKind(), matched.getKind()));
                    vo.setLastChapter(firstNonBlank(vo.getLastChapter(), matched.getLastChapter()));
                }
            } catch (Exception e) {
                log.warn("书籍详情兜底搜索失败: sourceId={}, bookUrl={}, err={}", sourceId, bookUrl, e.getMessage());
            }
        }

        vo.setIntro(firstNonBlank(vo.getIntro(), extractMetaDescription(body)));
        resolveCanonical(vo);

        return vo;
    }

    // ─── 目录 ─────────────────────────────────────────────────

    @Override
    public List<BookChapterVO> getChapters(Long sourceId, String bookUrl) {
        String cacheKey = sourceId + "|" + bookUrl;
        CachedChapters cached = CHAPTER_CACHE.get(cacheKey);
        if (cached != null && System.currentTimeMillis() - cached.cacheAt() <= CHAPTER_CACHE_TTL_MS) {
            return cached.chapters();
        }

        BookSource bs = getEnabledSource(sourceId);
        BookSourceModel model = parseModel(bs);

        BookSourceModel.TocRule tocRule = model.getRuleToc();
        if (tocRule == null || !StringUtils.hasText(tocRule.getChapterList())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "该书源没有目录规则");
        }

        String body;
        try {
            body = HttpFetcher.fetch(bookUrl, model.getHeader(), model.getBookSourceUrl(), model.getBookSourceCharset());
        } catch (Exception e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "获取目录失败：" + e.getMessage());
        }

        // Some Legado sources expose a compact book page and put the real
        // chapter list behind ruleBookInfo.tocUrl. Follow that link before
        // applying ruleToc so those sources remain compatible.
        BookSourceModel.BookInfoRule infoRule = model.getRuleBookInfo();
        if (infoRule != null && StringUtils.hasText(infoRule.getTocUrl())) {
            String tocUrl = resolveUrl(extractField(infoRule.getTocUrl(), body), model.getBookSourceUrl());
            if (StringUtils.hasText(tocUrl) && !tocUrl.equals(bookUrl)) {
                try {
                    body = HttpFetcher.fetch(tocUrl, model.getHeader(), model.getBookSourceUrl(), model.getBookSourceCharset());
                } catch (Exception e) {
                    log.debug("书源 {} 的独立目录页获取失败: {}", bs.getSourceName(), e.getMessage());
                }
            }
        }

        List<String> items = LegadoRuleEngine.extractList(tocRule.getChapterList(), body);
        List<BookChapterVO> chapters = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            String item = items.get(i);
            BookChapterVO vo = new BookChapterVO();
            vo.setIndex(i);
            vo.setChapterName(extractField(tocRule.getChapterName(), item));
            vo.setChapterUrl(resolveUrl(extractField(tocRule.getChapterUrl(), item), model.getBookSourceUrl()));
            if (StringUtils.hasText(vo.getChapterUrl())) {
                chapters.add(vo);
            }
        }
        CHAPTER_CACHE.put(cacheKey, new CachedChapters(System.currentTimeMillis(), chapters));
        return chapters;
    }

    @Override
    public Map<String, Object> getChaptersPage(Long sourceId, String bookUrl, int offset, int limit) {
        int safeOffset = Math.max(0, offset);
        int safeLimit = Math.max(1, Math.min(100, limit));

        BookSource bs = getEnabledSource(sourceId);
        BookSourceModel model = parseModel(bs);

        BookSourceModel.TocRule tocRule = model.getRuleToc();
        if (tocRule == null || !StringUtils.hasText(tocRule.getChapterList())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "该书源没有目录规则");
        }

        List<BookChapterVO> records = new ArrayList<>();
        int globalIndex = 0;
        boolean hasMore = false;
        String currentUrl = bookUrl;

        while (StringUtils.hasText(currentUrl)) {
            String body;
            try {
                body = HttpFetcher.fetch(currentUrl, model.getHeader(), model.getBookSourceUrl(), model.getBookSourceCharset());
            } catch (Exception e) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "获取目录失败：" + e.getMessage());
            }

            List<String> items = LegadoRuleEngine.extractList(tocRule.getChapterList(), body);
            if (items == null) items = List.of();

            int i = 0;
            for (; i < items.size(); i++) {
                String item = items.get(i);
                if (globalIndex >= safeOffset && records.size() < safeLimit) {
                    BookChapterVO vo = new BookChapterVO();
                    vo.setIndex(globalIndex);
                    vo.setChapterName(extractField(tocRule.getChapterName(), item));
                    vo.setChapterUrl(resolveUrl(extractField(tocRule.getChapterUrl(), item), model.getBookSourceUrl()));
                    if (StringUtils.hasText(vo.getChapterUrl())) {
                        records.add(vo);
                    }
                }
                globalIndex++;

                if (records.size() >= safeLimit) {
                    hasMore = (i + 1) < items.size();
                    break;
                }
            }

            if (records.size() >= safeLimit) {
                if (hasMore) {
                    break;
                }
                String nextTocUrl = resolveUrl(extractField(tocRule.getNextTocUrl(), body), model.getBookSourceUrl());
                hasMore = StringUtils.hasText(nextTocUrl);
                break;
            }

            String nextTocUrl = resolveUrl(extractField(tocRule.getNextTocUrl(), body), model.getBookSourceUrl());
            if (!StringUtils.hasText(nextTocUrl) || nextTocUrl.equals(currentUrl)) {
                hasMore = false;
                break;
            }
            currentUrl = nextTocUrl;
        }

        return Map.of(
                "records", records,
                "offset", safeOffset,
                "limit", safeLimit,
                "hasMore", hasMore,
                "currentPage", safeOffset / safeLimit + 1
        );
    }

    // ─── 内容 ─────────────────────────────────────────────────

    @Override
    public String getContent(Long sourceId, String chapterUrl) {
        BookSource bs = getEnabledSource(sourceId);
        BookSourceModel model = parseModel(bs);

        BookSourceModel.ContentRule contentRule = model.getRuleContent();
        if (contentRule == null || !StringUtils.hasText(contentRule.getContent())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "该书源没有正文规则");
        }

        String body;
        try {
            body = HttpFetcher.fetch(chapterUrl, model.getHeader(), model.getBookSourceUrl(), model.getBookSourceCharset());
        } catch (Exception e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "获取章节内容失败：" + e.getMessage());
        }

        String content = LegadoRuleEngine.extractString(contentRule.getContent(), body);
        if (content == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "正文提取失败，可能该书源规则不被支持");
        }
        content = applyContentReplaceRegex(content, contentRule.getReplaceRegex());
        // 清理多余空行
        return content.replaceAll("(\r?\n){3,}", "\n\n").trim();
    }

    private String applyContentReplaceRegex(String content, String replaceRule) {
        if (!StringUtils.hasText(content) || !StringUtils.hasText(replaceRule)) return content;
        String rule = replaceRule.trim();
        if (!rule.startsWith("##")) return content;
        String expression = rule.substring(2);
        int delimiter = expression.indexOf("##");
        String pattern = delimiter >= 0 ? expression.substring(0, delimiter) : expression;
        String replacement = delimiter >= 0 ? expression.substring(delimiter + 2) : "";
        if (!StringUtils.hasText(pattern)) return content;
        try {
            return content.replaceAll(pattern, replacement);
        } catch (Exception e) {
            log.debug("书源正文清理规则不兼容: {}", replaceRule);
            return content;
        }
    }

    // ─── 调试 ─────────────────────────────────────────────────

    @Override
    public Map<String, Object> debugChapters(Long sourceId, String bookUrl) {
        BookSource bs = getById(sourceId);
        if (bs == null) return Map.of("error", "书源不存在");
        BookSourceModel model;
        try { model = OM.readValue(bs.getSourceJson(), BookSourceModel.class); }
        catch (Exception e) { return Map.of("error", "JSON解析失败: " + e.getMessage()); }

        BookSourceModel.TocRule tocRule = model.getRuleToc();
        if (tocRule == null) return Map.of("error", "无目录规则");

        String body;
        try { body = HttpFetcher.fetch(bookUrl, model.getHeader(), model.getBookSourceUrl()); }
        catch (Exception e) { return Map.of("error", "HTTP失败: " + e.getMessage()); }

        List<String> items = LegadoRuleEngine.extractList(tocRule.getChapterList(), body);
        String firstItem = items.isEmpty() ? null : items.get(0);
        String firstName = firstItem != null ? LegadoRuleEngine.extractString(tocRule.getChapterName(), firstItem) : null;
        String firstUrl  = firstItem != null ? LegadoRuleEngine.extractString(tocRule.getChapterUrl(), firstItem) : null;

        return Map.of(
            "enabled", bs.getEnabled(),
            "chapterListRule", tocRule.getChapterList(),
            "chapterNameRule", String.valueOf(tocRule.getChapterName()),
            "chapterUrlRule",  String.valueOf(tocRule.getChapterUrl()),
            "htmlLength", body.length(),
            "htmlSample", body.substring(0, Math.min(500, body.length())),
            "itemCount", items.size(),
            "firstItem", firstItem != null ? firstItem.substring(0, Math.min(200, firstItem.length())) : "null",
            "firstChapterName", String.valueOf(firstName),
            "firstChapterUrl",  String.valueOf(firstUrl)
        );
    }

    // ─── 测试书源 ─────────────────────────────────────────────

    @Override
    public Map<String, Object> testSource(Long sourceId) {
        BookSource bs = getById(sourceId);
        if (bs == null) throw new BusinessException(ResultCode.NOT_FOUND, "书源不存在");

        BookSourceModel model;
        try {
            model = OM.readValue(bs.getSourceJson(), BookSourceModel.class);
        } catch (Exception e) {
            return Map.of("accessible", false, "statusCode", 0, "responseMs", 0L,
                    "error", "书源 JSON 解析失败");
        }

        String targetUrl = StringUtils.hasText(model.getBookSourceUrl())
                ? model.getBookSourceUrl() : bs.getSourceUrl();

        long start = System.currentTimeMillis();
        try {
            HttpClient client = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .connectTimeout(Duration.ofSeconds(8))
                    .build();
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(targetUrl))
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<Void> resp = client.send(req, HttpResponse.BodyHandlers.discarding());
            long ms = System.currentTimeMillis() - start;
            int code = resp.statusCode();
            boolean ok = code >= 200 && code < 400;
            return Map.of("accessible", ok, "statusCode", code, "responseMs", ms);
        } catch (Exception e) {
            long ms = System.currentTimeMillis() - start;
            return Map.of("accessible", false, "statusCode", 0, "responseMs", ms,
                    "error", e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    // ─── 私有工具 ─────────────────────────────────────────────

    private BookSource getEnabledSource(Long id) {
        BookSource bs = getById(id);
        if (bs == null) throw new BusinessException(ResultCode.NOT_FOUND, "书源不存在");
        if (!Boolean.TRUE.equals(bs.getEnabled())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "书源已禁用");
        }
        return bs;
    }

    private BookSourceModel parseModel(BookSource bs) {
        try {
            return OM.readValue(bs.getSourceJson(), BookSourceModel.class);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "书源 JSON 解析失败");
        }
    }

    private List<JsonNode> parseJsonToList(String json) {
        try {
            JsonNode root = OM.readTree(json);
            if (root.isArray()) {
                List<JsonNode> list = new ArrayList<>();
                root.forEach(list::add);
                return list;
            } else if (root.isObject()) {
                return List.of(root);
            }
        } catch (Exception e) {
            log.warn("JSON 解析失败: {}", e.getMessage());
        }
        return List.of();
    }

    private String extractField(String rule, String item) {
        if (!StringUtils.hasText(rule) || !StringUtils.hasText(item)) return null;
        return LegadoRuleEngine.extractString(rule, item);
    }

    /** 将相对 URL 拼接为绝对 URL */
    private String resolveUrl(String url, String base) {
        if (!StringUtils.hasText(url)) return null;
        if (url.startsWith("http://") || url.startsWith("https://")) return url;
        if (!StringUtils.hasText(base)) return url;
        String b = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        return url.startsWith("/") ? b + url : b + "/" + url;
    }

    private String encodeUrl(String s) {
        try {
            return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return s;
        }
    }

    private String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String v : values) {
            if (StringUtils.hasText(v)) return v;
        }
        return null;
    }

    private void resolveCanonical(SearchBookVO book) {
        if (!StringUtils.hasText(book.getName()) || !StringUtils.hasText(book.getBookUrl()) || book.getSourceId() == null) return;
        ResolveCanonicalBookDTO dto = new ResolveCanonicalBookDTO();
        dto.setSourceId(book.getSourceId());
        dto.setBookUrl(book.getBookUrl());
        dto.setTitle(book.getName());
        dto.setAuthor(book.getAuthor());
        dto.setCoverUrl(book.getCoverUrl());
        dto.setSummary(book.getIntro());
        try {
            book.setCanonicalBookId(canonicalBookService.resolve(dto).getCanonicalBookId());
        } catch (Exception e) {
            log.warn("Canonical book resolution failed: sourceId={}, bookUrl={}", book.getSourceId(), book.getBookUrl(), e);
        }
    }

    private String snapshotCover(String coverUrl) {
        return coverSnapshotUtil.snapshot(coverUrl);
    }

    private String extractTitle(String html) {
        if (!StringUtils.hasText(html)) return null;
        Matcher matcher = Pattern.compile("<title>(.*?)</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(html);
        if (!matcher.find()) return null;
        String title = matcher.group(1);
        if (title == null) return null;
        return title.replaceAll("\\s+", " ").trim();
    }

    private String extractMetaDescription(String html) {
        if (!StringUtils.hasText(html)) return null;
        Pattern pattern = Pattern.compile(
                "<meta[^>]+(?:name|property)\\s*=\\s*['\"](?:description|og:description|twitter:description)['\"][^>]+content\\s*=\\s*['\"](.*?)['\"][^>]*>",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        );
        Matcher matcher = pattern.matcher(html);
        if (!matcher.find()) return null;
        String desc = matcher.group(1);
        if (!StringUtils.hasText(desc)) return null;
        return desc.replaceAll("<[^>]+>", "").replaceAll("\\s+", " ").trim();
    }

    @Override
    public List<SearchBookVO> aggregateSearch(long userId, String keyword, int page) {
        return deduplicateAggregateResults(filterDisabledSources(aggregateSourceResults(keyword, page), userId)).stream()
                .sorted(Comparator.<SearchBookVO>comparingInt(book -> searchRelevance(keyword, book.getName(), book.getAuthor()))
                        .thenComparing(Comparator.comparingInt(this::resultQuality).reversed())
                        .thenComparing(SearchBookVO::getName, Comparator.nullsLast(String::compareTo)))
                .toList();
    }

    @Override
    public List<AggregatedBookVO> aggregateCanonicalSearch(long userId, String keyword, int page) {
        List<SearchBookVO> sourceResults = filterDisabledSources(aggregateSourceResults(keyword, page), userId);
        Map<String, List<SearchBookVO>> grouped = sourceResults.stream()
                .filter(book -> book.getCanonicalBookId() != null)
                .collect(java.util.stream.Collectors.groupingBy(this::canonicalSearchIdentity,
                        LinkedHashMap::new, java.util.stream.Collectors.toList()));
        return grouped.values().stream().map(books -> aggregateBook(preferredCanonicalBookId(books), books))
                .sorted(Comparator.<AggregatedBookVO>comparingInt(book -> searchRelevance(keyword, book.getName(), book.getAuthor()))
                        .thenComparing(Comparator.comparingInt(AggregatedBookVO::getSourceCount).reversed())
                        .thenComparing(AggregatedBookVO::getName, Comparator.nullsLast(String::compareTo)))
                .toList();
    }

    @Override
    public List<AggregatedBookVO> canonicalBookSources(long userId, Long canonicalBookId) {
        if (canonicalBookId == null) return List.of();
        List<Long> equivalentIds = canonicalBookService.equivalentCanonicalBookIds(canonicalBookId);
        if (equivalentIds.isEmpty()) return List.of();
        List<BookSourceMapping> mappings = mappingMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BookSourceMapping>()
                .in(BookSourceMapping::getCanonicalBookId, equivalentIds));
        if (mappings.isEmpty()) return List.of();
        Map<Long, BookSource> sources = listByIds(mappings.stream().map(BookSourceMapping::getSourceId).distinct().toList())
                .stream().collect(java.util.stream.Collectors.toMap(BookSource::getId, source -> source));
        java.util.Set<Long> disabledIds = disabledSourceIds(userId);
        List<SearchBookVO> books = mappings.stream().filter(mapping -> {
            BookSource source = sources.get(mapping.getSourceId());
            return source != null && Boolean.TRUE.equals(source.getEnabled()) && !disabledIds.contains(mapping.getSourceId());
        }).map(mapping -> {
            SearchBookVO book = new SearchBookVO();
            book.setCanonicalBookId(canonicalBookId);
            book.setSourceId(mapping.getSourceId());
            book.setBookUrl(mapping.getSourceBookUrl());
            book.setName(mapping.getSourceTitle());
            book.setAuthor(displayAuthor(mapping.getSourceAuthor()));
            BookSource source = sources.get(mapping.getSourceId());
            book.setSourceName(source == null ? "已移除书源" : source.getSourceName());
            return book;
        }).toList();
        if (books.isEmpty()) return List.of();
        return List.of(aggregateBook(canonicalBookId, books));
    }

    private List<SearchBookVO> filterDisabledSources(List<SearchBookVO> books, long userId) {
        java.util.Set<Long> disabledIds = disabledSourceIds(userId);
        if (disabledIds.isEmpty()) return books;
        return books.stream().filter(book -> book.getSourceId() == null || !disabledIds.contains(book.getSourceId())).toList();
    }

    private java.util.Set<Long> disabledSourceIds(long userId) {
        if (userId <= 0) return java.util.Set.of();
        return preferenceMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserBookSourcePreference>()
                        .eq(UserBookSourcePreference::getUserId, userId)
                        .eq(UserBookSourcePreference::getDisabled, true))
                .stream().map(UserBookSourcePreference::getSourceId).collect(java.util.stream.Collectors.toSet());
    }

    private AggregatedBookVO aggregateBook(Long canonicalBookId, List<SearchBookVO> books) {
        SearchBookVO preferred = books.stream().max(Comparator.comparingInt(this::resultQuality)).orElseThrow();
        AggregatedBookVO result = new AggregatedBookVO();
        result.setCanonicalBookId(canonicalBookId);
        result.setName(firstPresent(books, SearchBookVO::getName, preferred.getName()));
        result.setAuthor(firstPresent(books, SearchBookVO::getAuthor, preferred.getAuthor()));
        result.setCoverUrl(firstPresent(books, SearchBookVO::getCoverUrl, preferred.getCoverUrl()));
        result.setIntro(firstPresent(books, SearchBookVO::getIntro, preferred.getIntro()));
        result.setKind(firstPresent(books, SearchBookVO::getKind, preferred.getKind()));
        result.setLastChapter(firstPresent(books, SearchBookVO::getLastChapter, preferred.getLastChapter()));
        List<BookSourceSummaryVO> sources = books.stream()
                .filter(book -> book.getSourceId() != null && StringUtils.hasText(book.getBookUrl()))
                .collect(java.util.stream.Collectors.toMap(book -> book.getSourceId() + "|" + book.getBookUrl(), book -> book,
                        (left, right) -> resultQuality(left) >= resultQuality(right) ? left : right, LinkedHashMap::new))
                .values().stream().sorted(Comparator.comparingInt(this::resultQuality).reversed())
                .map(this::sourceSummary).toList();
        result.setSources(sources);
        result.setSourceCount(sources.size());
        result.setPreferredSource(sources.isEmpty() ? null : sources.get(0));
        return result;
    }

    private BookSourceSummaryVO sourceSummary(SearchBookVO book) {
        BookSourceSummaryVO source = new BookSourceSummaryVO();
        source.setSourceId(book.getSourceId()); source.setSourceName(book.getSourceName()); source.setBookUrl(book.getBookUrl());
        source.setLastChapter(book.getLastChapter()); source.setAvailability("AVAILABLE");
        return source;
    }

    private String firstPresent(List<SearchBookVO> books, java.util.function.Function<SearchBookVO, String> getter, String fallback) {
        return books.stream().map(getter).filter(StringUtils::hasText).max(Comparator.comparingInt(String::length)).orElse(fallback);
    }

    private List<SearchBookVO> aggregateSourceResults(String keyword, int page) {
        String cacheKey = keyword == null ? "" : keyword.trim().replaceAll("\\s+", " ").toLowerCase(java.util.Locale.ROOT);
        cacheKey += "|" + Math.max(1, page);
        List<SearchBookVO> cached = AGGREGATE_SOURCE_RESULTS_CACHE.getIfPresent(cacheKey);
        if (cached != null) return cached;

        // 查所有启用的书源
        List<BookSource> sources = lambdaQuery()
                .eq(BookSource::getEnabled, true)
                .list();

        if (sources.isEmpty()) return List.of();

        long startedAt = System.nanoTime();
        // Use the dedicated I/O pool instead of the CPU-sized common pool. A slow source is capped
        // independently, so it cannot turn one aggregation into a multi-batch wait.
        List<CompletableFuture<List<SearchBookVO>>> futures = sources.stream()
                .map(bs -> searchSourceAsync(bs, keyword, page))
                .toList();

        // The response budget is deliberately below the front-end timeout. Timed-out sources may
        // finish in the background, but their late results never keep this request open.
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(8, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("聚合搜索达到响应预算，返回已完成书源的结果");
        }

        List<SearchBookVO> results = futures.stream()
                .filter(f -> f.isDone() && !f.isCompletedExceptionally())
                .flatMap(f -> {
                    try {
                    return f.get().stream();
                } catch (Exception ex) {
                    return java.util.stream.Stream.of();
                }
                })
                .collect(java.util.stream.Collectors.toList());
        resolveCanonicalIds(results);
        AGGREGATE_SOURCE_RESULTS_CACHE.put(cacheKey, results);
        log.info("聚合搜索完成: keyword={}, sources={}, results={}, elapsedMs={}", keyword, sources.size(), results.size(),
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt));
        return results;
    }

    private List<SearchBookVO> deduplicateAggregateResults(List<SearchBookVO> books) {
        Map<String, SearchBookVO> unique = new java.util.LinkedHashMap<>();
        Map<String, String> coverIdentities = new HashMap<>();
        for (SearchBookVO book : books) {
            if (book == null) continue;
            String key = aggregateIdentity(book);
            String coverIdentity = coverIdentity(book);
            if (!coverIdentity.isBlank()) key = coverIdentities.getOrDefault(coverIdentity, key);
            SearchBookVO existing = unique.get(key);
            if (existing == null || resultQuality(book) > resultQuality(existing)) unique.put(key, book);
            if (!coverIdentity.isBlank()) coverIdentities.put(coverIdentity, key);
        }
        return new ArrayList<>(unique.values());
    }

    private String aggregateIdentity(SearchBookVO book) {
        String title = normalizedBookField(book.getName());
        String author = normalizedAuthorField(book.getAuthor());
        if (!title.isBlank() && !"未知书名".equals(title)) return "title|" + title + "|" + author;
        String cover = normalizedBookField(book.getCoverUrl());
        String intro = normalizedBookField(book.getIntro());
        if (!cover.isBlank()) return "cover|" + cover + "|" + author;
        if (book.getSourceId() != null && StringUtils.hasText(book.getBookUrl())) return "source|" + book.getSourceId() + "|" + book.getBookUrl();
        return "fallback|" + author + "|" + intro + "|" + System.identityHashCode(book);
    }

    private String coverIdentity(SearchBookVO book) {
        String cover = normalizedBookField(book.getCoverUrl());
        if (cover.isBlank()) return "";
        return cover + "|" + normalizedAuthorField(book.getAuthor());
    }

    private int resultQuality(SearchBookVO book) {
        int score = 0;
        if (StringUtils.hasText(book.getName()) && !"未知书名".equals(book.getName().trim())) score += 8;
        if (StringUtils.hasText(book.getAuthor())) score += 2;
        if (StringUtils.hasText(book.getIntro())) score += 2;
        if (StringUtils.hasText(book.getCoverUrl())) score += 1;
        if (StringUtils.hasText(book.getBookUrl())) score += 1;
        return score;
    }

    /**
     * Sort search results by what the reader typed before using metadata completeness or source count.
     * This is deliberately based on fields returned by every source instead of source-specific rules.
     */
    static int searchRelevance(String keyword, String title, String author) {
        String query = normalizedSearchText(keyword);
        if (query.isEmpty()) return 0;
        String normalizedTitle = normalizedSearchText(title);
        if (normalizedTitle.equals(query)) return 0;
        if (normalizedTitle.startsWith(query)) return 100 + normalizedTitle.length() - query.length();
        int titleMatchIndex = normalizedTitle.indexOf(query);
        if (titleMatchIndex >= 0) return 200 + titleMatchIndex;

        String normalizedAuthor = normalizedSearchText(author);
        if (normalizedAuthor.equals(query)) return 300;
        if (normalizedAuthor.startsWith(query)) return 400 + normalizedAuthor.length() - query.length();
        int authorMatchIndex = normalizedAuthor.indexOf(query);
        if (authorMatchIndex >= 0) return 500 + authorMatchIndex;
        return 1_000;
    }

    private static String normalizedSearchText(String value) {
        if (value == null) return "";
        // Ignore display punctuation such as 《》 and spaces so the visible title matches reader intent.
        return value.replaceAll("[\\s\\p{P}\\p{S}]+", "").toLowerCase(java.util.Locale.ROOT);
    }

    private String normalizedBookField(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").trim().toLowerCase(java.util.Locale.ROOT);
    }

    private String canonicalSearchIdentity(SearchBookVO book) {
        return normalizedSearchText(book.getName()) + "|" + normalizedAuthorField(book.getAuthor());
    }

    private Long preferredCanonicalBookId(List<SearchBookVO> books) {
        return books.stream().map(SearchBookVO::getCanonicalBookId).filter(java.util.Objects::nonNull)
                .min(Long::compareTo).orElseThrow();
    }

    private static String displayAuthor(String value) {
        if (value == null) return null;
        return value.replaceFirst("^\\s*(?:作者|作\\s*者)\\s*[:：]?\\s*", "").trim();
    }

    static String normalizedAuthorField(String value) {
        return normalizedSearchText(displayAuthor(value));
    }

    private CompletableFuture<List<SearchBookVO>> searchSourceAsync(BookSource source, String keyword, int page) {
        try {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    return searchSource(source, keyword, page, false);
                } catch (Exception e) {
                    log.debug("聚合搜索跳过书源 [{}]: {}", source.getSourceName(), e.getMessage());
                    return List.<SearchBookVO>of();
                }
            }, bookSourceSearchExecutor).completeOnTimeout(List.<SearchBookVO>of(), 7, TimeUnit.SECONDS);
        } catch (RejectedExecutionException e) {
            log.warn("聚合搜索线程池繁忙，暂跳过书源 [{}]", source.getSourceName());
            return CompletableFuture.completedFuture(List.<SearchBookVO>of());
        }
    }

    private void resolveCanonicalIds(List<SearchBookVO> books) {
        if (books.isEmpty()) return;
        HashSet<Long> sourceIds = new HashSet<>();
        for (SearchBookVO book : books) if (book.getSourceId() != null) sourceIds.add(book.getSourceId());
        if (sourceIds.isEmpty()) return;
        Map<String, Long> knownIds = new HashMap<>();
        mappingMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BookSourceMapping>()
                        .in(BookSourceMapping::getSourceId, sourceIds))
                .forEach(mapping -> knownIds.put(mapping.getSourceId() + "|" + mapping.getSourceBookUrl(), mapping.getCanonicalBookId()));
        for (SearchBookVO book : books) {
            Long canonicalBookId = knownIds.get(book.getSourceId() + "|" + book.getBookUrl());
            if (canonicalBookId != null) book.setCanonicalBookId(canonicalBookId);
        }
        // Preserve the aggregate API contract for new works as well. The mapping batch above avoids
        // a per-hit lookup for known books; only genuinely new source entries need to be persisted.
        books.stream().filter(book -> book.getCanonicalBookId() == null).forEach(this::resolveCanonical);
    }

    private BookSourceVO toVO(BookSource bs) {
        BookSourceVO vo = new BookSourceVO();
        BeanUtils.copyProperties(bs, vo);
        return vo;
    }
}
