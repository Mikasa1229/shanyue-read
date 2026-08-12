package com.shanyuefang.agent.service;

import com.shanyuefang.agent.config.AgentProperties;
import com.shanyuefang.agent.domain.vo.KnowledgeGraphVO;
import com.shanyuefang.agent.feign.CanonicalBookFeignClient;
import com.shanyuefang.agent.feign.NovelShelfFeignClient;
import com.shanyuefang.common.exception.BusinessException;
import com.shanyuefang.common.result.R;
import com.shanyuefang.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** MCP-facing tools deliberately mirror the Agent's server-side, read-only business boundary. */
@Service
@RequiredArgsConstructor
public class McpReadOnlyToolService {
    private static final int GRAPH_EDGE_BUDGET = 36;
    private final NovelShelfFeignClient shelfClient;
    private final CanonicalBookFeignClient canonicalBookClient;
    private final KnowledgeService knowledgeService;
    private final AgentProperties properties;
    private final SpoilerBoundaryService spoilerBoundaryService;

    public Object call(long userId, String name, Map<String, Object> arguments) {
        return switch (name) {
            case "bookshelf.list" -> shelf(userId);
            case "book.search" -> search(string(arguments, "query"), strings(arguments, "queries"));
            case "book.detail" -> detail(longValue(arguments, "canonicalBookId"));
            case "reading.state" -> timeline(userId, longValue(arguments, "canonicalBookId"), intValue(arguments, "currentChapter"));
            case "knowledge_graph.query" -> graph(userId, longValue(arguments, "canonicalBookId"), intValue(arguments, "currentChapter"));
            default -> throw new BusinessException(ResultCode.PARAM_ERROR, "MCP 工具不在只读白名单中");
        };
    }

    private Object shelf(long userId) {
        R<List<Map<String, Object>>> result = shelfClient.list(properties.getInternalToken(), userId);
        return result == null || result.getData() == null ? List.of() : result.getData().stream().limit(12).toList();
    }
    private Object search(String query, List<String> queries) {
        List<String> targets = queries == null || queries.isEmpty() ? List.of(query) : queries.stream().filter(value -> !value.isBlank()).distinct().limit(3).toList();
        if (targets.isEmpty() || targets.stream().anyMatch(value -> value.length() > 160)) throw new BusinessException(ResultCode.PARAM_ERROR, "作品搜索条件无效");
        List<CompletableFuture<List<Map<String, Object>>>> futures = targets.stream()
                .map(target -> CompletableFuture.supplyAsync(() -> searchOne(target))).toList();
        try { CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).get(12, TimeUnit.SECONDS); }
        catch (Exception ignored) { /* Return results from queries that completed in the shared budget. */ }
        Map<String, Map<String, Object>> unique = new LinkedHashMap<>();
        for (CompletableFuture<List<Map<String, Object>>> future : futures) {
            List<Map<String, Object>> values = future.getNow(List.of());
            for (Map<String, Object> value : values) {
                String key = String.valueOf(value.get("canonicalBookId")) + "|" + String.valueOf(value.get("sourceId")) + "|" + String.valueOf(value.get("sourceBookUrl"));
                unique.putIfAbsent(key, value);
            }
        }
        return unique.values().stream().limit(8).toList();
    }
    private List<Map<String, Object>> searchOne(String query) {
        R<List<Map<String, Object>>> result = canonicalBookClient.search(properties.getInternalToken(), query, 6);
        return result == null || result.getData() == null ? List.of() : result.getData();
    }
    private Object detail(long bookId) {
        R<Map<String, Object>> result = canonicalBookClient.detail(properties.getInternalToken(), bookId);
        return result == null || result.getData() == null ? Map.of() : result.getData();
    }
    private Object timeline(long userId, long bookId, int currentChapter) {
        currentChapter = spoilerBoundaryService.clamp(userId, bookId, currentChapter);
        return Map.of("canonicalBookId", bookId, "currentChapter", currentChapter,
                "timeline", knowledgeService.timeline(bookId, currentChapter).stream().limit(10).toList());
    }
    private Object graph(long userId, long bookId, int currentChapter) {
        currentChapter = spoilerBoundaryService.clamp(userId, bookId, currentChapter);
        KnowledgeGraphVO graph = knowledgeService.graph(bookId, currentChapter);
        return Map.of("canonicalBookId", bookId, "currentChapter", currentChapter,
                "nodes", graph.getNodes().stream().limit(30).toList(), "edges", graph.getEdges().stream().limit(GRAPH_EDGE_BUDGET).toList());
    }
    private String string(Map<String, Object> values, String name) { Object value = values.get(name); return value == null ? "" : String.valueOf(value).trim(); }
    private List<String> strings(Map<String, Object> values, String name) {
        Object value = values.get(name);
        if (!(value instanceof List<?> raw)) return List.of();
        return raw.stream().filter(String.class::isInstance).map(String.class::cast).map(String::trim).filter(text -> !text.isBlank()).toList();
    }
    private long longValue(Map<String, Object> values, String name) { try { long value = Long.parseLong(String.valueOf(values.get(name))); if (value > 0) return value; } catch (Exception ignored) { } throw new BusinessException(ResultCode.PARAM_ERROR, "参数无效：" + name); }
    private int intValue(Map<String, Object> values, String name) { try { int value = Integer.parseInt(String.valueOf(values.get(name))); if (value >= 0 && value <= 1_000_000) return value; } catch (Exception ignored) { } throw new BusinessException(ResultCode.PARAM_ERROR, "参数无效：" + name); }
}
