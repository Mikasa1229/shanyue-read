package com.shanyuefang.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shanyuefang.agent.domain.dto.ChatMessageDTO;
import com.shanyuefang.agent.domain.vo.ClueVO;
import com.shanyuefang.agent.domain.vo.KnowledgeGraphVO;
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
        String normalized = request == null ? "" : request.toLowerCase(Locale.ROOT);
        List<String> context = new ArrayList<>();
        List<String> tools = new ArrayList<>();

        if (asksForShelf(normalized)) {
            tools.add("bookshelf.read");
            context.add(readShelf(userId));
        }
        if (dto.getCanonicalBookId() != null && asksForBookDetail(normalized)) {
            tools.add("book.detail.read");
            context.add(readBookDetail(dto.getCanonicalBookId()));
        } else if (asksForBookSearch(normalized)) {
            tools.add("book.search.read");
            context.add(searchBooks(request));
        }
        if (dto.getCanonicalBookId() != null && dto.getCurrentChapter() != null && asksForGraph(normalized)) {
            tools.add("knowledge_graph.read");
            context.add(readGraph(dto.getCanonicalBookId(), Math.max(0, dto.getCurrentChapter()), request));
        }
        if (dto.getCanonicalBookId() != null && dto.getCurrentChapter() != null && asksForReadingState(normalized)) {
            tools.add("reading_progress.read");
            List<String> timeline = knowledgeService.timeline(dto.getCanonicalBookId(), Math.max(0, dto.getCurrentChapter())).stream().limit(6).toList();
            context.add("READING_PROGRESS (request boundary Ch. " + (dto.getCurrentChapter() + 1) + "): " + String.join(" | ", timeline));
        }

        if (context.isEmpty()) return ToolResult.empty();
        return new ToolResult(String.join("\n", context), trace(tools, dto));
    }

    private String readShelf(long userId) {
        try {
            R<List<Map<String, Object>>> response = shelfClient.list(properties.getInternalToken(), userId);
            List<Map<String, Object>> books = response == null || response.getData() == null ? List.of() : response.getData();
            return "BOOKSHELF (only the requesting user's first 12 items): " + books.stream().limit(12)
                    .map(book -> String.valueOf(book.getOrDefault("bookName", "Untitled")) + " / "
                            + String.valueOf(book.getOrDefault("lastChapterName", "not started")))
                    .reduce((left, right) -> left + "; " + right).orElse("empty");
        } catch (Exception ignored) {
            return "BOOKSHELF: temporarily unavailable.";
        }
    }

    private String readGraph(long bookId, int currentChapter, String request) {
        KnowledgeGraphVO graph = knowledgeService.graph(bookId, currentChapter);
        String nodes = graph.getNodes().stream().limit(12).map(node -> node.getName() + "(" + node.getType() + ")").reduce((a, b) -> a + ", " + b).orElse("none");
        String edges = graph.getEdges().stream().limit(LOCAL_GRAPH_EDGE_BUDGET).map(edge -> edge.getSource() + "-" + edge.getRelation() + "->" + edge.getTarget()).reduce((a, b) -> a + "; " + b).orElse("none");
        List<ClueVO> clues = knowledgeService.clues(bookId, currentChapter).stream().limit(4).toList();
        String normalized = request.toLowerCase(Locale.ROOT);
        List<String> seeds = graph.getNodes().stream().map(node -> node.getName()).filter(name -> normalized.contains(name.toLowerCase(Locale.ROOT)))
                .limit(3).toList();
        if (seeds.isEmpty()) seeds = graph.getNodes().stream().limit(2).map(node -> node.getName()).toList();
        List<String> localEdges = graphKnowledgeStore.localNeighborhood(bookId, currentChapter, seeds, LOCAL_GRAPH_EDGE_BUDGET);
        return "KNOWLEDGE_GRAPH (visible through Ch. " + (currentChapter + 1) + "): nodes=" + nodes + "; edges=" + edges
                + "; bounded local expansion=" + String.join("; ", localEdges)
                + "; open clues=" + clues.stream().map(ClueVO::getExcerpt).reduce((a, b) -> a + " | " + b).orElse("none");
    }
    private String readBookDetail(long bookId) {
        try {
            R<Map<String, Object>> response = canonicalBookClient.detail(properties.getInternalToken(), bookId);
            Map<String, Object> book = response == null || response.getData() == null ? Map.of() : response.getData();
            return "BOOK_DETAIL: " + book.getOrDefault("title", "Untitled") + " / " + book.getOrDefault("author", "unknown")
                    + "; " + String.valueOf(book.getOrDefault("summary", "No verified summary."));
        } catch (Exception ignored) { return "BOOK_DETAIL: temporarily unavailable."; }
    }
    private String searchBooks(String request) {
        try {
            R<List<Map<String, Object>>> response = canonicalBookClient.search(properties.getInternalToken(), request.trim(), 6);
            List<Map<String, Object>> books = response == null || response.getData() == null ? List.of() : response.getData();
            return "BOOK_SEARCH: " + books.stream().map(book -> String.valueOf(book.getOrDefault("title", "Untitled"))
                    + " / " + String.valueOf(book.getOrDefault("author", "unknown"))).reduce((a, b) -> a + "; " + b).orElse("no indexed matches");
        } catch (Exception ignored) { return "BOOK_SEARCH: temporarily unavailable."; }
    }

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
    private boolean asksForBookSearch(String request) { return request.contains("find book") || request.contains("recommend book") || request.contains("找书") || request.contains("推荐书"); }

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

    public record ToolResult(String context, String traceJson) {
        public static ToolResult empty() { return new ToolResult("", null); }
        public boolean used() { return !context.isBlank(); }
    }
}
