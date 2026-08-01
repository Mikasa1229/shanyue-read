package com.shanyuefang.novel.service;

import com.shanyuefang.common.exception.BusinessException;
import com.shanyuefang.common.result.ResultCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Protects service-only canonical identity operations from direct callers. */
@Component
public class NovelInternalAccess {
    private final String internalToken;

    public NovelInternalAccess(@Value("${app.agent.internal-token:${AGENT_INTERNAL_TOKEN:}}") String internalToken) {
        this.internalToken = internalToken;
    }

    public void require(String token) {
        if (!StringUtils.hasText(internalToken) || !StringUtils.hasText(token)
                || !MessageDigest.isEqual(internalToken.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException(ResultCode.FORBIDDEN, "Invalid internal service credential");
        }
    }
}
