package com.shanyuefang.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shanyuefang.agent.domain.dto.ChatMessageDTO;
import com.shanyuefang.agent.domain.vo.ClueVO;
import com.shanyuefang.agent.domain.vo.KnowledgeGraphVO;
import com.shanyuefang.agent.domain.vo.BookReferenceVO;
import com.shanyuefang.agent.feign.NovelShelfFeignClient;
import com.shanyuefang.agent.feign.CanonicalBookFeignClient;
import com.shanyuefang.agent.config.AgentProperties;
import com.shanyuefang.common.result.R;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Controlled compatibility layer for Spring AI 0.8.x: only a fixed allowlist of
 * read-only tools may enrich a prompt. Results are bounded and persisted as an audit trace.
 */
@Service
@RequiredArgsConstructor
public class AgentReadOnlyToolService {
    private static final int LOCAL_GRAPH_EDGE_BUDGET = 36;
    private final KnowledgeService knowledgeService;
    private final GraphKnowledgeStore graphKnowledgeStore;
    private final NovelShelfFeignClient shelfClient;
    private final CanonicalBookFeignClient canonicalBookClient;
    private final AgentProperties properties;
    private final ObjectMapper objectMapper;

    public ToolResult execute(long userId, ChatMessageDTO dto, String request) {
        return execute(userId, dto, request, request, true);
    }

    public ToolResult execute(long userId, ChatMessageDTO dto, String request, String bookSearchRequest) {
        return execute(userId, dto, request, bookSearchRequest, true);
    }

    public ToolResult execute(long userId, ChatMessageDTO dto, String request, String bookSearchRequest,
                              boolean prefetchBookSearch) {
        return execute(userId, dto, request, List.of(bookSearchRequest), prefetchBookSearch);
    }

    public ToolResult execute(long userId, ChatMessageDTO dto, String request, List<String> bookSearchRequests,
                              boolean prefetchBookSearch) {
        String normalized = request == null ? "" : request.toLowerCase(Locale.ROOT);
        List<String> context = new ArrayList<>();
        List<String> tools = new ArrayList<>();

        if (asksForShelf(normalized)) {
            tools.add("bookshelf.read");
            context.add(readShelf(userId));
        }
        List<BookReferenceVO> bookReferences = new ArrayList<>();
        if (dto.getCanonicalBookId() != null && asksForBookDetail(normalized)) {
            tools.add("book.detail.read");
            context.add(readBookDetail(dto.getCanonicalBookId()));
        } else if (prefetchBookSearch && asksForBookSearch(normalized)) {
            tools.add("book.search.read");
            SearchResult searchResult = searchBooks(bookSearchRequests);
            context.add(searchResult.context());
            bookReferences.addAll(searchResult.bookReferences());
        }
        if (dto.getCanonicalBookId() != null && dto.getCurrentChapter() != null && asksForGraph(normalized)) {
            tools.add("knowledge_graph.read");
            context.add(readGraph(dto.getCanonicalBookId(), Math.max(0, dto.getCurrentChapter()), request));
        }
        if (dto.getCanonicalBookId() != null && dto.getCurrentChapter() != null && asksForReadingState(normalized)) {
            tools.add("reading_progress.read");
            List<String> timeline = knowledgeService.timeline(dto.getCanonicalBookId(), Math.max(0, dto.getCurrentChapter())).stream().limit(6).toList();
            context.add("阅读进度（本次请求边界为第 " + (dto.getCurrentChapter() + 1) + " 章）：" + String.join(" | ", timeline));
        }

        if (context.isEmpty()) return ToolResult.empty();
        return new ToolResult(String.join("\n", context), trace(tools, dto), bookReferences);
    }

    private String readShelf(long userId) {
        try {
            R<List<Map<String, Object>>> response = shelfClient.list(properties.getInternalToken(), userId);
            List<Map<String, Object>> books = response == null || response.getData() == null ? List.of() : response.getData();
            return "书架（只展示当前用户的前 12 本书）：" + books.stream().limit(12)
                    .map(book -> String.valueOf(book.getOrDefault("bookName", "未命名")) + " / "
                            + String.valueOf(book.getOrDefault("lastChapterName", "尚未开始阅读")))
                    .reduce((left, right) -> left + "；" + right).orElse("书架为空");
        } catch (Exception ignored) {
            return "书架：暂时不可用。";
        }
    }

    private String readGraph(long bookId, int currentChapter, String request) {
        KnowledgeGraphVO graph = knowledgeService.graph(bookId, currentChapter);
        String nodes = graph.getNodes().stream().limit(12).map(node -> node.getName() + "（" + node.getType() + "）").reduce((a, b) -> a + "、" + b).orElse("无");
        String edges = graph.getEdges().stream().limit(LOCAL_GRAPH_EDGE_BUDGET).map(edge -> edge.getSource() + "-" + edge.getRelation() + "->" + edge.getTarget()).reduce((a, b) -> a + "；" + b).orElse("无");
        List<ClueVO> clues = knowledgeService.clues(bookId, currentChapter).stream().limit(12).toList();
        String normalized = request.toLowerCase(Locale.ROOT);
        List<String> seeds = graph.getNodes().stream().map(node -> node.getName()).filter(name -> normalized.contains(name.toLowerCase(Locale.ROOT)))
                .limit(3).toList();
        if (seeds.isEmpty()) seeds = graph.getNodes().stream().limit(2).map(node -> node.getName()).toList();
        List<String> localEdges = graphKnowledgeStore.localNeighborhood(bookId, currentChapter, seeds, LOCAL_GRAPH_EDGE_BUDGET);
        return "人物关系图（可见范围截至第 " + (currentChapter + 1) + " 章）：节点=" + nodes + "；关系=" + edges
                + "；有界局部扩展=" + String.join("；", localEdges)
                + "；未闭合线索=" + clues.stream().map(ClueVO::getExcerpt).reduce((a, b) -> a + " | " + b).orElse("无");
    }
    private String readBookDetail(long bookId) {
        try {
            R<Map<String, Object>> response = canonicalBookClient.detail(properties.getInternalToken(), bookId);
            Map<String, Object> book = response == null || response.getData() == null ? Map.of() : response.getData();
            return "作品详情：" + book.getOrDefault("title", "未命名") + " / " + book.getOrDefault("author", "未知作者")
                    + "；" + String.valueOf(book.getOrDefault("summary", "暂无已验证简介。"));
        } catch (Exception ignored) { return "作品详情：暂时不可用。"; }
    }
    private SearchResult searchBooks(List<String> requests) {
        List<String> queries = requests == null ? List.of() : requests.stream()
                .filter(query -> query != null && !query.isBlank()).distinct().limit(3).toList();
        if (queries.isEmpty()) return new SearchResult("平台书源搜索没有找到可直接阅读的作品。", List.of());
        List<CompletableFuture<SearchResult>> futures = queries.stream()
                .map(query -> CompletableFuture.supplyAsync(() -> searchBooks(query))).toList();
        try { CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).get(12, TimeUnit.SECONDS); }
        catch (Exception ignored) { /* Keep completed queries; one slow book source must not block the batch. */ }
        List<BookReferenceVO> references = new ArrayList<>();
        for (CompletableFuture<SearchResult> future : futures) {
            SearchResult result = future.getNow(null);
            if (result != null) references.addAll(result.bookReferences());
        }
        Map<String, BookReferenceVO> unique = new LinkedHashMap<>();
        for (BookReferenceVO reference : references) {
            if (reference.getCanonicalBookId() != null) unique.putIfAbsent(String.valueOf(reference.getCanonicalBookId()), reference);
        }
        List<BookReferenceVO> merged = unique.values().stream().limit(8).toList();
        String context = merged.isEmpty() ? "平台书源搜索没有找到可直接阅读的作品。"
                : "平台书源已验证候选（只能从下列作品中推荐；不得补充列表外作品）：\n" + merged.stream()
                .map(book -> "- 《" + book.getTitle() + "》 / " + book.getAuthor()
                        + (book.getSummary() == null || book.getSummary().isBlank() ? "" : " / " + book.getSummary()))
                .reduce((a, b) -> a + "\n" + b).orElse("");
        return new SearchResult(context, merged);
    }

    private SearchResult searchBooks(String request) {
        try {
            R<List<Map<String, Object>>> response = canonicalBookClient.search(properties.getInternalToken(), request.trim(), 12);
            List<Map<String, Object>> books = response == null || response.getData() == null ? List.of() : response.getData();
            List<BookReferenceVO> references = books.stream().map(this::toBookReference)
                    .filter(reference -> reference.getCanonicalBookId() != null && reference.getSourceId() != null
                            && reference.getSourceBookUrl() != null && !reference.getSourceBookUrl().isBlank())
                    .limit(8).toList();
            String context = references.isEmpty() ? "平台书源搜索没有找到可直接阅读的作品。"
                    : "平台书源已验证候选（只能从下列作品中推荐；不得补充列表外作品）：\n" + references.stream()
                    .map(book -> "- 《" + book.getTitle() + "》 / " + book.getAuthor()
                            + (book.getSummary() == null || book.getSummary().isBlank() ? "" : " / " + book.getSummary()))
                    .reduce((a, b) -> a + "\n" + b).orElse("");
            return new SearchResult(context, references);
        } catch (Exception ignored) { return new SearchResult("平台书源搜索暂时不可用。不得凭模型记忆补充作品。", List.of()); }
    }

    private BookReferenceVO toBookReference(Map<String, Object> book) {
        return new BookReferenceVO(longOrNull(book.get("canonicalBookId")), string(book.get("title")),
                string(book.get("author")), string(book.get("coverUrl")), longOrNull(book.get("sourceId")),
                string(book.get("sourceBookUrl")), string(book.get("summary")));
    }

    private Long longOrNull(Object value) { try { return value == null ? null : Long.valueOf(String.valueOf(value)); } catch (Exception ignored) { return null; } }
    private String string(Object value) { return value == null ? "" : String.valueOf(value); }

    private boolean asksForShelf(String request) {
        return request.contains("bookshelf") || request.contains("my books") || request.contains("书架") || request.contains("在读");
    }

    private boolean asksForGraph(String request) {
        return request.contains("relationship") || request.contains("character") || request.contains("graph") || request.contains("关系") || request.contains("人物") || request.contains("线索");
    }

    private boolean asksForReadingState(String request) {
        return request.contains("recap") || request.contains("progress") || request.contains("timeline") || request.contains("回顾") || request.contains("进度") || request.contains("剧情");
    }
    private boolean asksForBookDetail(String request) { return request.contains("book detail") || request.contains("about this book") || request.contains("这本书") || request.contains("作品简介"); }
    public static boolean asksForBookSearch(String request) {
        return request.contains("find book") || request.contains("recommend book") || request.contains("recommendation")
                || request.contains("找书") || request.contains("搜书") || request.contains("搜索")
                || request.contains("推荐") || request.contains("书源") || request.contains("有什么书")
                || request.contains("看什么") || request.contains("读什么")
                || request.contains("换一本") || request.contains("换个") || request.contains("直接推荐")
                || request.contains("热门") || request.contains("点击") || request.contains("确定是")
                || request.contains("引用") || request.contains("链接") || request.contains("可读");
    }

    private String trace(List<String> tools, ChatMessageDTO dto) {
        try {
            Map<String, Object> trace = new LinkedHashMap<>();
            trace.put("tools", tools);
            trace.put("canonicalBookId", dto.getCanonicalBookId());
            trace.put("currentChapter", dto.getCurrentChapter());
            trace.put("readOnly", true);
            return objectMapper.writeValueAsString(trace);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not serialize tool audit trace", exception);
        }
    }

    public record ToolResult(String context, String traceJson, List<BookReferenceVO> bookReferences) {
        public static ToolResult empty() { return new ToolResult("", null, List.of()); }
        public boolean used() { return !context.isBlank(); }
    }
    private record SearchResult(String context, List<BookReferenceVO> bookReferences) { }
}
