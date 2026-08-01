package com.shanyuefang.agent.service.impl;

import com.shanyuefang.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentServiceImplTest {
    @Test
    void preservesBackwardCompatibleProviderDefaultsForOlderByokConfigurations() {
        assertEquals("https://api.deepseek.com", AgentServiceImpl.normalizeBaseUrl(null, "deepseek"));
        assertEquals("https://api.openai.com", AgentServiceImpl.normalizeBaseUrl("", "openai"));
        assertEquals("https://example.test/v1", AgentServiceImpl.normalizeBaseUrl("https://example.test/v1/", "openai", "example.test"));
    }

    @Test
    void rejectsUntrustedOrUnsafeByokEndpoints() {
        assertThrows(BusinessException.class, () -> AgentServiceImpl.normalizeBaseUrl("file:///local-model", "openai"));
        assertThrows(BusinessException.class, () -> AgentServiceImpl.normalizeBaseUrl("http://api.openai.com", "openai"));
        assertThrows(BusinessException.class, () -> AgentServiceImpl.normalizeBaseUrl("https://127.0.0.1/v1", "openai"));
        assertThrows(BusinessException.class, () -> AgentServiceImpl.normalizeBaseUrl("https://127.0.0.1/v1", "openai", "127.0.0.1"));
        assertThrows(BusinessException.class, () -> AgentServiceImpl.normalizeBaseUrl("https://example.test/v1", "openai"));
        assertThrows(BusinessException.class, () -> AgentServiceImpl.normalizeBaseUrl("https://api.openai.com/?target=internal", "openai"));
    }
}
