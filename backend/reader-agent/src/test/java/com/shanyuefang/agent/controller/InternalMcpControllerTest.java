package com.shanyuefang.agent.controller;

import com.shanyuefang.agent.config.AgentProperties;
import com.shanyuefang.agent.service.AgentInternalAccess;
import com.shanyuefang.agent.service.McpReadOnlyToolService;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;

class InternalMcpControllerTest {
    private InternalMcpController controller() {
        AgentProperties properties = new AgentProperties();
        properties.setInternalToken("private-test-token");
        return new InternalMcpController(new AgentInternalAccess(properties), mock(McpReadOnlyToolService.class));
    }

    @Test
    void rejectsMcpCallsWithoutARequestingUserIdentity() {
        Map<String, Object> result = controller().handle("private-test-token", 0L, Map.of("jsonrpc", "2.0", "id", "request-1", "method", "tools/list"));
        Map<?, ?> error = (Map<?, ?>) result.get("error");
        assertEquals(-32602, error.get("code"));
    }

    @Test
    void validatesTheInternalTokenBeforeProcessingThePayload() {
        assertThrows(RuntimeException.class, () -> controller().handle("wrong-token", 42L, Map.of("jsonrpc", "2.0", "method", "tools/list")));
    }

    @Test
    void returnsJsonSchemaObjectPropertiesForToolDiscovery() {
        Map<String, Object> response = controller().handle("private-test-token", 42L, Map.of("jsonrpc", "2.0", "id", "request-2", "method", "tools/list"));
        Map<?, ?> result = (Map<?, ?>) response.get("result");
        List<?> tools = (List<?>) result.get("tools");
        Map<?, ?> search = (Map<?, ?>) tools.stream().map(Map.class::cast).filter(tool -> "book.search".equals(tool.get("name"))).findFirst().orElseThrow();
        Map<?, ?> schema = (Map<?, ?>) search.get("inputSchema");
        Map<?, ?> properties = (Map<?, ?>) schema.get("properties");
        assertEquals("string", ((Map<?, ?>) properties.get("query")).get("type"));
        assertEquals("array", ((Map<?, ?>) properties.get("queries")).get("type"));
        assertEquals(List.of(), schema.get("required"));
    }

    @Test
    void rejectsNonJsonRpcRequests() {
        Map<String, Object> result = controller().handle("private-test-token", 42L, Map.of("id", "request-3", "method", "tools/list"));
        assertEquals(-32600, ((Map<?, ?>) result.get("error")).get("code"));
    }

    @Test
    void supportsStandardMcpInitializationNotificationAndPing() {
        assertEquals(null, controller().handle("private-test-token", 42L, Map.of(
                "jsonrpc", "2.0", "method", "notifications/initialized")));
        Map<String, Object> ping = controller().handle("private-test-token", 42L, Map.of(
                "jsonrpc", "2.0", "id", "request-ping", "method", "ping"));
        assertEquals(Map.of(), ping.get("result"));
    }

    @Test
    void acceptsTheQueriesArrayAsTheStructuredSearchArgument() {
        AgentProperties properties = new AgentProperties();
        properties.setInternalToken("private-test-token");
        McpReadOnlyToolService toolService = mock(McpReadOnlyToolService.class);
        when(toolService.call(eq(42L), eq("book.search"), anyMap())).thenReturn(List.of());
        InternalMcpController controller = new InternalMcpController(new AgentInternalAccess(properties), toolService);
        Map<String, Object> result = controller.handle("private-test-token", 42L, Map.of(
                "jsonrpc", "2.0", "id", "request-4", "method", "tools/call",
                "params", Map.of("name", "book.search", "arguments", Map.of("queries", List.of("剑来", "诡秘之主")))));
        assertEquals(null, result.get("error"));
    }

    @Test
    void rejectsArgumentsOutsideDiscoveredSchema() {
        Map<String, Object> result = controller().handle("private-test-token", 42L, Map.of(
                "jsonrpc", "2.0", "id", "request-5", "method", "tools/call",
                "params", Map.of("name", "book.search", "arguments", Map.of("query", "mystery", "extra", true))));
        assertEquals(-32000, ((Map<?, ?>) result.get("error")).get("code"));
    }
}
