package com.shanyuefang.gateway.filter;

import cn.dev33.satoken.reactor.context.SaReactorSyncHolder;
import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shanyuefang.common.result.R;
import com.shanyuefang.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;

/**
 * 全局鉴权过滤器
 * - 白名单路径直接放行
 * - 其他路径校验 Sa-Token，通过后将 userId 注入下游请求 Header
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    private final ObjectMapper objectMapper;

    @Value("${app.gateway.agent-forward-token:}")
    private String agentForwardToken;

    /** 无需登录的公开路径前缀 */
    private static final List<String> WHITE_LIST = List.of(
            "/api/auth/login",
            "/api/auth/register"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        String method = exchange.getRequest().getMethod().name();

        // Discovery-locator routes must never make service-only interfaces public.
        if (path.startsWith("/internal/")) {
            exchange.getResponse().setStatusCode(HttpStatus.NOT_FOUND);
            return exchange.getResponse().setComplete();
        }

        // 公开路径放行
        if (isWhitelisted(path)) {
            return chain.filter(exchange);
        }

        // GET 请求的只读接口放行（小说列表、详情等）
        if ("GET".equals(method) && isPublicReadPath(path)) {
            return chain.filter(exchange);
        }

        // Sa-Token 校验：将当前 exchange 绑定到 ThreadLocal，供 StpUtil 读取 Token
        SaReactorSyncHolder.setContext(exchange);
        try {
            StpUtil.checkLogin();
            long userId = StpUtil.getLoginIdAsLong();

            // 将 userId 注入下游 Header
            ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                    // Never preserve a caller-provided identity header beside the trusted value.
                    .headers(headers -> {
                        headers.remove("X-User-Id");
                        // Downstream services receive a gateway-observed address, never a caller-supplied forwarding header.
                        headers.remove("X-Agent-Client-Ip");
                        headers.remove("X-Agent-Gateway-Token");
                        headers.set("X-User-Id", String.valueOf(userId));
                        String clientIp = exchange.getRequest().getRemoteAddress() == null
                                ? "unknown" : String.valueOf(exchange.getRequest().getRemoteAddress().getAddress().getHostAddress());
                        headers.set("X-Agent-Client-Ip", clientIp);
                        if (StringUtils.hasText(agentForwardToken)) headers.set("X-Agent-Gateway-Token", agentForwardToken);
                    })
                    .build();

            return chain.filter(exchange.mutate().request(mutatedRequest).build());
        } catch (Exception e) {
            log.warn("鉴权失败: path={}, error={}", path, e.getMessage());
            return unauthorized(exchange);
        } finally {
            SaReactorSyncHolder.clearContext();
        }
    }

    private boolean isWhitelisted(String path) {
        return WHITE_LIST.stream().anyMatch(path::startsWith);
    }

    /** 即便是 GET，这些路径也需要登录（会读取用户身份信息） */
    private static final List<String> AUTH_REQUIRED_GET = List.of(
            "/api/novels/my"
    );

    private boolean isPublicReadPath(String path) {
        if (AUTH_REQUIRED_GET.stream().anyMatch(path::startsWith)) return false;
        return path.startsWith("/api/novels")
                || path.startsWith("/api/comments")
                || path.startsWith("/api/book-sources")
                || path.startsWith("/api/reading/ranking");
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        R<Void> body = R.fail(ResultCode.UNAUTHORIZED);
        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(body);
        } catch (JsonProcessingException e) {
            bytes = "{\"code\":401,\"message\":\"未登录\"}".getBytes(StandardCharsets.UTF_8);
        }
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -100; // 最高优先级
    }
}
