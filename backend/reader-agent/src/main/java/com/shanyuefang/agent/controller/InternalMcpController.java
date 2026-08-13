package com.shanyuefang.agent.controller;

import com.shanyuefang.agent.service.AgentInternalAccess;
import com.shanyuefang.agent.service.McpReadOnlyToolService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/** Private JSON-RPC 2.0 MCP transport. This route is intentionally absent from the gateway. */
@RestController
@RequestMapping("/internal/agent/mcp")
@RequiredArgsConstructor
public class InternalMcpController {
    private static final Map<String, ToolSpec> TOOLS = toolSpecs();
    private final AgentInternalAccess internalAccess;
    private final McpReadOnlyToolService toolService;

    @PostMapping
    public Map<String, Object> handle(@RequestHeader("X-Agent-Internal-Token") String token,
                                      @RequestHeader("X-User-Id") long userId,
                                      @RequestBody Map<String, Object> request) {
        internalAccess.require(token);
        Object requestId = request.get("id");
        boolean notification = !request.containsKey("id");
        if (userId <= 0) return notification ? null : error(requestId, -32602, "A requesting user identity is required");
        if (!"2.0".equals(String.valueOf(request.get("jsonrpc")))) return notification ? null : error(requestId, -32600, "JSON-RPC 2.0 is required");
        String method = String.valueOf(request.getOrDefault("method", ""));
        try {
            Map<String, Object> response = switch (method) {
                case "initialize" -> success(requestId, Map.of("protocolVersion", "2024-11-05", "serverInfo", Map.of("name", "reader-agent", "version", "1.0"), "capabilities", Map.of("tools", Map.of())));
                case "notifications/initialized" -> null;
                case "ping" -> success(requestId, Map.of());
                case "tools/list" -> success(requestId, Map.of("tools", tools()));
                case "tools/call" -> call(userId, request);
                default -> error(requestId, -32601, "Unsupported MCP method");
            };
            return notification ? null : response;
        } catch (Exception exception) {
            return notification ? null : error(requestId, -32000, exception.getMessage() == null ? "MCP tool failed" : exception.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> call(long userId, Map<String, Object> request) {
        Map<String, Object> params = request.get("params") instanceof Map<?, ?> raw ? (Map<String, Object>) raw : Map.of();
        String name = String.valueOf(params.getOrDefault("name", ""));
        Map<String, Object> arguments = params.get("arguments") instanceof Map<?, ?> raw ? (Map<String, Object>) raw : Map.of();
        validateArguments(name, arguments);
        Object data = toolService.call(userId, name, arguments);
        return success(request.get("id"), Map.of("content", List.of(Map.of("type", "text", "text", String.valueOf(data))), "structuredContent", Map.of("data", data), "isError", false));
    }
    private List<Map<String, Object>> tools() {
        return TOOLS.entrySet().stream().map(entry -> tool(entry.getKey(), entry.getValue())).toList();
    }
    private Map<String, Object> tool(String name, ToolSpec spec) {
        Map<String, Map<String, Object>> schemaProperties = new java.util.LinkedHashMap<>();
        spec.properties().forEach((key, type) -> schemaProperties.put(key,
                "string-array".equals(type) ? Map.of("type", "array", "items", Map.of("type", "string")) : Map.of("type", type)));
        return Map.of("name", name, "description", spec.description(), "inputSchema", Map.of("type", "object", "properties", schemaProperties,
                "required", spec.required(), "additionalProperties", false));
    }
    private void validateArguments(String name, Map<String, Object> arguments) {
        ToolSpec spec = TOOLS.get(name);
        if (spec == null) throw new IllegalArgumentException("MCP tool is not allowlisted");
        if (!spec.properties().keySet().containsAll(arguments.keySet())) throw new IllegalArgumentException("MCP tool arguments contain unsupported properties");
        for (String required : spec.required()) {
            if (!arguments.containsKey(required) || arguments.get(required) == null) throw new IllegalArgumentException("MCP tool argument is required: " + required);
        }
        for (Map.Entry<String, String> property : spec.properties().entrySet()) {
            Object value = arguments.get(property.getKey());
            if (value == null) continue;
            boolean valid = "string".equals(property.getValue()) ? value instanceof String
                    : "string-array".equals(property.getValue()) ? value instanceof List<?> values && values.stream().allMatch(String.class::isInstance)
                    : value instanceof Number && Math.floor(((Number) value).doubleValue()) == ((Number) value).doubleValue();
            if (!valid) throw new IllegalArgumentException("MCP tool argument has invalid type: " + property.getKey());
        }
    }
    private static Map<String, ToolSpec> toolSpecs() {
        Map<String, ToolSpec> specs = new LinkedHashMap<>();
        specs.put("bookshelf.list", new ToolSpec("Read the requesting user's bookshelf", Map.of(), List.of()));
        specs.put("book.search", new ToolSpec("Search canonical books. Use query for one target or queries for up to three independent targets.",
                Map.of("query", "string", "queries", "string-array"), List.of()));
        specs.put("book.availability", new ToolSpec("Verify exact platform availability for one to three already selected book titles.",
                Map.of("titles", "string-array"), List.of("titles")));
        specs.put("book.detail", new ToolSpec("Read canonical book metadata", Map.of("canonicalBookId", "integer"), List.of("canonicalBookId")));
        specs.put("reading.state", new ToolSpec("Read spoiler-bounded timeline", Map.of("canonicalBookId", "integer", "currentChapter", "integer"), List.of("canonicalBookId", "currentChapter")));
        specs.put("knowledge_graph.query", new ToolSpec("Read spoiler-bounded graph", Map.of("canonicalBookId", "integer", "currentChapter", "integer"), List.of("canonicalBookId", "currentChapter")));
        return Map.copyOf(specs);
    }
    private Map<String, Object> success(Object id, Object result) { return Map.of("jsonrpc", "2.0", "id", id == null ? "" : id, "result", result); }
    private Map<String, Object> error(Object id, int code, String message) { return Map.of("jsonrpc", "2.0", "id", id == null ? "" : id, "error", Map.of("code", code, "message", message)); }
    private record ToolSpec(String description, Map<String, String> properties, List<String> required) { }
}
