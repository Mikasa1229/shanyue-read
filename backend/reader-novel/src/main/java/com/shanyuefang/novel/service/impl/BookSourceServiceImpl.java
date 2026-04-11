package com.shanyuefang.novel.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shanyuefang.common.exception.BusinessException;
import com.shanyuefang.common.result.ResultCode;
import com.shanyuefang.common.util.SnowflakeIdUtil;
import com.shanyuefang.novel.domain.entity.BookSource;
import com.shanyuefang.novel.domain.vo.BookChapterVO;
import com.shanyuefang.novel.domain.vo.BookSourceVO;
import com.shanyuefang.novel.domain.vo.SearchBookVO;
import com.shanyuefang.novel.engine.BookSourceModel;
import com.shanyuefang.novel.engine.HttpFetcher;
import com.shanyuefang.novel.engine.LegadoRuleEngine;
import com.shanyuefang.novel.mapper.BookSourceMapper;
import com.shanyuefang.novel.service.BookSourceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
                saved++;
            } catch (Exception e) {
                log.warn("书源导入跳过（解析失败）: {}", e.getMessage());
            }
        }
        log.info("书源导入完成，共保存 {} 条", saved);
        return saved;
    }

    // ─── 管理 ─────────────────────────────────────────────────

    @Override
    public Page<BookSourceVO> listSources(int page, int size) {
        Page<BookSource> rawPage = lambdaQuery()
                .orderByDesc(BookSource::getCreatedAt)
                .page(new Page<>(page, size));
        Page<BookSourceVO> result = new Page<>(rawPage.getCurrent(), rawPage.getSize(), rawPage.getTotal());
        result.setRecords(rawPage.getRecords().stream().map(this::toVO).toList());
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void toggleEnabled(Long id) {
        BookSource bs = getById(id);
        if (bs == null) throw new BusinessException(ResultCode.NOT_FOUND, "书源不存在");
        bs.setEnabled(!Boolean.TRUE.equals(bs.getEnabled()));
        updateById(bs);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteSource(Long id) {
        BookSource bs = getById(id);
        if (bs == null) throw new BusinessException(ResultCode.NOT_FOUND, "书源不存在");
        removeById(id);
    }

    // ─── 搜索 ─────────────────────────────────────────────────

    @Override
    public List<SearchBookVO> search(Long sourceId, String keyword, int page) {
        BookSource bs = getEnabledSource(sourceId);
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
            vo.setSourceId(sourceId);
            vo.setSourceName(bs.getSourceName());
            vo.setName(extractField(model.effectiveSearchName(), item));
            vo.setAuthor(extractField(model.effectiveSearchAuthor(), item));
            vo.setCoverUrl(extractField(model.effectiveSearchCover(), item));
            vo.setIntro(extractField(model.effectiveSearchIntro(), item));
            vo.setKind(extractField(model.effectiveSearchKind(), item));
            vo.setLastChapter(extractField(model.effectiveSearchLastChapter(), item));
            vo.setBookUrl(resolveUrl(extractField(model.effectiveSearchBookUrl(), item), model.getBookSourceUrl()));
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
            vo.setAuthor(extractField(info.getAuthor(), body));
            vo.setCoverUrl(resolveUrl(extractField(info.getCoverUrl(), body), model.getBookSourceUrl()));
            vo.setIntro(extractField(info.getIntro(), body));
            vo.setKind(extractField(info.getKind(), body));
            vo.setLastChapter(extractField(info.getLastChapter(), body));
            vo.setWordCount(extractField(info.getWordCount(), body));
        }

        // 兼容没有 ruleBookInfo 的书源，尽量用搜索规则做兜底
        vo.setName(firstNonBlank(vo.getName(), extractField(model.effectiveSearchName(), body), extractTitle(body)));
        vo.setAuthor(firstNonBlank(vo.getAuthor(), extractField(model.effectiveSearchAuthor(), body)));
        vo.setCoverUrl(firstNonBlank(vo.getCoverUrl(), resolveUrl(extractField(model.effectiveSearchCover(), body), model.getBookSourceUrl())));
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
                    vo.setCoverUrl(firstNonBlank(vo.getCoverUrl(), matched.getCoverUrl()));
                    vo.setAuthor(firstNonBlank(vo.getAuthor(), matched.getAuthor()));
                    vo.setKind(firstNonBlank(vo.getKind(), matched.getKind()));
                    vo.setLastChapter(firstNonBlank(vo.getLastChapter(), matched.getLastChapter()));
                }
            } catch (Exception e) {
                log.warn("书籍详情兜底搜索失败: sourceId={}, bookUrl={}, err={}", sourceId, bookUrl, e.getMessage());
            }
        }

        vo.setIntro(firstNonBlank(vo.getIntro(), extractMetaDescription(body)));

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
        // 清理多余空行
        return content.replaceAll("(\r?\n){3,}", "\n\n").trim();
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
    public List<SearchBookVO> aggregateSearch(String keyword, int page) {
        // 查所有启用的书源
        List<BookSource> sources = lambdaQuery()
                .eq(BookSource::getEnabled, true)
                .list();

        if (sources.isEmpty()) return List.of();

        // 并发调用每个书源搜索，单个书源超时 8 秒
        List<CompletableFuture<List<SearchBookVO>>> futures = sources.stream()
                .map(bs -> CompletableFuture.supplyAsync(() -> {
                    try {
                        return search(bs.getId(), keyword, page);
                    } catch (Exception e) {
                        log.warn("聚合搜索：书源 [{}] 搜索失败，跳过: {}", bs.getSourceName(), e.getMessage());
                        return List.<SearchBookVO>of();
                    }
                }))
                .toList();

        // 等待所有并发任务完成（最多 15 秒）
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(15, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("聚合搜索等待超时，已有结果正常返回");
        }

        // 收集所有结果
        return futures.stream()
                .filter(f -> f.isDone() && !f.isCompletedExceptionally())
                .flatMap(f -> {
                    try { return f.get().stream(); } catch (Exception ex) { return java.util.stream.Stream.of(); }
                })
                .collect(java.util.stream.Collectors.toList());
    }

    private BookSourceVO toVO(BookSource bs) {
        BookSourceVO vo = new BookSourceVO();
        BeanUtils.copyProperties(bs, vo);
        return vo;
    }
}
