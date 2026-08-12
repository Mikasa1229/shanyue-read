package com.shanyuefang.agent.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Reject direct browser-facing requests to the Agent service that bypass the trusted gateway. */
@Component
@RequiredArgsConstructor
public class AgentGatewayAccessFilter implements Filter {
    private final AgentProperties properties;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        if (!httpRequest.getRequestURI().startsWith("/api/agent/")) {
            chain.doFilter(request, response);
            return;
        }
        String expected = properties.getGatewayToken();
        String actual = httpRequest.getHeader("X-Agent-Gateway-Token");
        if (!StringUtils.hasText(expected) || !expected.equals(actual)) {
            HttpServletResponse httpResponse = (HttpServletResponse) response;
            httpResponse.setStatus(HttpStatus.NOT_FOUND.value());
            httpResponse.setContentType("application/json;charset=UTF-8");
            httpResponse.getOutputStream().write("{\"code\":404,\"message\":\"Not found\"}".getBytes(StandardCharsets.UTF_8));
            return;
        }
        chain.doFilter(request, response);
    }
}
