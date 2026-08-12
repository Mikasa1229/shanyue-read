package com.shanyuefang.agent.service;

import com.shanyuefang.agent.config.AgentProperties;
import com.shanyuefang.common.exception.BusinessException;
import com.shanyuefang.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Protects direct service-only endpoints that are intentionally absent from the gateway route table. */
@Component
@RequiredArgsConstructor
public class AgentInternalAccess {
    private final AgentProperties properties;

    public void require(String token) {
        String expected = properties.getInternalToken();
        if (!StringUtils.hasText(expected) || !StringUtils.hasText(token)
                || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException(ResultCode.FORBIDDEN, "Invalid internal Agent credential");
        }
    }
}
