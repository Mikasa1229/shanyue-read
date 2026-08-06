package com.shanyuefang.agent.service.impl;

import com.shanyuefang.agent.mapper.UserModelConfigMapper;
import com.shanyuefang.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentServiceImplTest {
    @Test
    void acceptsPublicOpenAiCompatibleEndpointsAndPreservesLegacyDefaults() {
        assertEquals("https://api.deepseek.com", AgentServiceImpl.normalizeBaseUrl(null, "deepseek"));
        assertEquals("https://api.openai.com", AgentServiceImpl.normalizeBaseUrl("", "openai"));
        assertEquals("https://api.deepseek.com", AgentServiceImpl.normalizeBaseUrl("https://api.deepseek.com/v1/", "openai"));
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

    @Test
    void removesOptionalV1SuffixBeforeSpringAiBuildsChatCompletionsPath() {
        assertEquals("https://api.xiaomimimo.com", AgentServiceImpl.chatCompletionsBaseUrl("https://api.xiaomimimo.com/v1"));
        assertEquals("https://api.deepseek.com", AgentServiceImpl.chatCompletionsBaseUrl("https://api.deepseek.com"));
    }

    @Test
    void deletesOnlyTheOwnedActiveModelConfiguration() {
        UserModelConfigMapper mapper = mock(UserModelConfigMapper.class);
        when(mapper.deleteOwnedConfig(7L, 11L)).thenReturn(1);

        serviceWith(mapper).deleteModelConfig(7L, 11L);

        verify(mapper).deleteOwnedConfig(7L, 11L);
    }

    @Test
    void rejectsDeleteWhenNoOwnedActiveConfigurationWasRemoved() {
        UserModelConfigMapper mapper = mock(UserModelConfigMapper.class);
        when(mapper.deleteOwnedConfig(7L, 11L)).thenReturn(0);

        assertThrows(BusinessException.class, () -> serviceWith(mapper).deleteModelConfig(7L, 11L));
    }

    private AgentServiceImpl serviceWith(UserModelConfigMapper mapper) {
        return new AgentServiceImpl(null, null, mapper, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null);
    }
}
