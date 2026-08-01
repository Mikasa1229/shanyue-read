package com.shanyuefang.gateway.filter;

import cn.dev33.satoken.reactor.context.SaReactorSyncHolder;
import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("网关鉴权过滤器单元测试")
class AuthGlobalFilterTest {

    AuthGlobalFilter filter;

    @BeforeEach
    void setUp() {
        filter = new AuthGlobalFilter(new ObjectMapper());
    }

    // ── 白名单路径放行 ──────────────────────────────────────────

    @Test
    @DisplayName("白名单 - /api/auth/login 直接放行")
    void whitelist_loginPath_passesThrough() {
        var exchange = exchangeFor("POST", "/api/auth/login");
        List<ServerWebExchange> captured = new ArrayList<>();
        GatewayFilterChain chain = ex -> { captured.add(ex); return Mono.empty(); };

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(captured).hasSize(1);
        assertThat(exchange.getResponse().getStatusCode()).isNotEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("白名单 - /api/auth/register 直接放行")
    void whitelist_registerPath_passesThrough() {
        var exchange = exchangeFor("POST", "/api/auth/register");
        List<ServerWebExchange> captured = new ArrayList<>();
        GatewayFilterChain chain = ex -> { captured.add(ex); return Mono.empty(); };

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(captured).hasSize(1);
    }

    @Test
    @DisplayName("内部服务路径不经网关暴露")
    void internalPath_returns404WithoutReachingAService() {
        var exchange = exchangeFor("POST", "/internal/agent/mcp");
        List<ServerWebExchange> captured = new ArrayList<>();

        StepVerifier.create(filter.filter(exchange, ex -> { captured.add(ex); return Mono.empty(); }))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(captured).isEmpty();
    }

    // ── GET 公开只读路径放行 ────────────────────────────────────

    @Test
    @DisplayName("公开只读 - GET /api/novels/1 直接放行")
    void publicRead_getNovelPath_passesThrough() {
        var exchange = exchangeFor("GET", "/api/novels/1");
        List<ServerWebExchange> captured = new ArrayList<>();
        GatewayFilterChain chain = ex -> { captured.add(ex); return Mono.empty(); };

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(captured).hasSize(1);
    }

    @Test
    @DisplayName("公开只读 - GET /api/comments 直接放行")
    void publicRead_getCommentsPath_passesThrough() {
        var exchange = exchangeFor("GET", "/api/comments");
        List<ServerWebExchange> captured = new ArrayList<>();
        GatewayFilterChain chain = ex -> { captured.add(ex); return Mono.empty(); };

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(captured).hasSize(1);
    }

    @Test
    @DisplayName("公开只读 - POST /api/novels 不放行（需鉴权）")
    void publicRead_postNovelPath_requiresAuth() {
        var exchange = exchangeFor("POST", "/api/novels");

        try (MockedStatic<SaReactorSyncHolder> holderMock = mockStatic(SaReactorSyncHolder.class);
             MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {

            holderMock.when(() -> SaReactorSyncHolder.setContext(any())).thenAnswer(inv -> null);
            holderMock.when(SaReactorSyncHolder::clearContext).thenAnswer(inv -> null);
            stpMock.when(StpUtil::checkLogin).thenThrow(new RuntimeException("未登录"));

            StepVerifier.create(filter.filter(exchange, ex -> Mono.empty()))
                    .verifyComplete();

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    // ── Token 校验失败 → 401 ────────────────────────────────────

    @Test
    @DisplayName("鉴权失败 - Token 无效时返回 401")
    void invalidToken_returns401() {
        var exchange = exchangeFor("POST", "/api/interactions/toggle");

        try (MockedStatic<SaReactorSyncHolder> holderMock = mockStatic(SaReactorSyncHolder.class);
             MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {

            holderMock.when(() -> SaReactorSyncHolder.setContext(any())).thenAnswer(inv -> null);
            holderMock.when(SaReactorSyncHolder::clearContext).thenAnswer(inv -> null);
            stpMock.when(StpUtil::checkLogin).thenThrow(new RuntimeException("Token 无效"));

            StepVerifier.create(filter.filter(exchange, ex -> Mono.empty()))
                    .verifyComplete();

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    // ── Token 校验成功 → 注入 X-User-Id ────────────────────────

    @Test
    @DisplayName("鉴权成功 - 有效 Token 时注入 X-User-Id 请求头")
    void validToken_injectsUserIdHeader() {
        var exchange = exchangeFor("POST", "/api/interactions/toggle");
        List<ServerWebExchange> captured = new ArrayList<>();
        GatewayFilterChain chain = ex -> { captured.add(ex); return Mono.empty(); };

        try (MockedStatic<SaReactorSyncHolder> holderMock = mockStatic(SaReactorSyncHolder.class);
             MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {

            holderMock.when(() -> SaReactorSyncHolder.setContext(any())).thenAnswer(inv -> null);
            holderMock.when(SaReactorSyncHolder::clearContext).thenAnswer(inv -> null);
            stpMock.when(StpUtil::checkLogin).thenAnswer(inv -> null);
            stpMock.when(StpUtil::getLoginIdAsLong).thenReturn(42L);

            StepVerifier.create(filter.filter(exchange, chain))
                    .verifyComplete();

            assertThat(captured).hasSize(1);
            assertThat(captured.get(0).getRequest().getHeaders().getFirst("X-User-Id")).isEqualTo("42");
        }
    }

    @Test
    @DisplayName("鉴权成功 - 覆盖客户端伪造的 X-User-Id")
    void validToken_replacesSpoofedUserIdHeader() {
        ReflectionTestUtils.setField(filter, "agentForwardToken", "gateway-secret");
        var request = MockServerHttpRequest.post("/api/agent/sessions")
                .remoteAddress(new java.net.InetSocketAddress("203.0.113.8", 443))
                .header("X-User-Id", "999999").header("X-Agent-Client-Ip", "127.0.0.1").build();
        var exchange = MockServerWebExchange.from(request);
        List<ServerWebExchange> captured = new ArrayList<>();

        try (MockedStatic<SaReactorSyncHolder> holderMock = mockStatic(SaReactorSyncHolder.class);
             MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
            holderMock.when(() -> SaReactorSyncHolder.setContext(any())).thenAnswer(inv -> null);
            holderMock.when(SaReactorSyncHolder::clearContext).thenAnswer(inv -> null);
            stpMock.when(StpUtil::checkLogin).thenAnswer(inv -> null);
            stpMock.when(StpUtil::getLoginIdAsLong).thenReturn(42L);

            StepVerifier.create(filter.filter(exchange, ex -> { captured.add(ex); return Mono.empty(); })).verifyComplete();

            assertThat(captured).hasSize(1);
            assertThat(captured.get(0).getRequest().getHeaders().get("X-User-Id")).containsExactly("42");
            assertThat(captured.get(0).getRequest().getHeaders().get("X-Agent-Client-Ip")).containsExactly("203.0.113.8");
            assertThat(captured.get(0).getRequest().getHeaders().get("X-Agent-Gateway-Token")).containsExactly("gateway-secret");
        }
    }

    @Test
    @DisplayName("过滤器优先级 - getOrder() 返回 -100")
    void filterOrder_isHighestPriority() {
        assertThat(filter.getOrder()).isEqualTo(-100);
    }

    // ── helper ────────────────────────────────────────────────

    private MockServerWebExchange exchangeFor(String method, String path) {
        MockServerHttpRequest request = switch (method.toUpperCase()) {
            case "POST"   -> MockServerHttpRequest.post(path).build();
            case "PUT"    -> MockServerHttpRequest.put(path).build();
            case "DELETE" -> MockServerHttpRequest.delete(path).build();
            default       -> MockServerHttpRequest.get(path).build();
        };
        return MockServerWebExchange.from(request);
    }
}
