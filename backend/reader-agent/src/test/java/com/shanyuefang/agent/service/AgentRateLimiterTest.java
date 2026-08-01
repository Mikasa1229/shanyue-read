package com.shanyuefang.agent.service;

import com.shanyuefang.agent.config.AgentProperties;
import com.shanyuefang.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentRateLimiterTest {
    @Test
    void rollsBackRequestReservationWhenTheDailyRequestBudgetIsExceeded() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked") ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.increment(anyString())).thenReturn(4L);
        AgentProperties properties = new AgentProperties(); properties.setGlobalDailyPlatformRequests(3);

        assertThrows(BusinessException.class, () -> new AgentRateLimiter(redis, properties).reservePlatformBudget(10));
        verify(values).decrement(org.mockito.ArgumentMatchers.startsWith("agent:platform:daily:"));
    }

    @Test
    void rollsBackBothReservationsWhenTheDailyTokenBudgetIsExceeded() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked") ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.increment(anyString())).thenReturn(1L);
        when(values.increment(org.mockito.ArgumentMatchers.startsWith("agent:platform:daily:tokens:"), anyLong())).thenReturn(101L);
        AgentProperties properties = new AgentProperties(); properties.setGlobalDailyPlatformRequests(3); properties.setGlobalDailyPlatformTokens(100);

        assertThrows(BusinessException.class, () -> new AgentRateLimiter(redis, properties).reservePlatformBudget(10));
        verify(values).decrement(org.mockito.ArgumentMatchers.startsWith("agent:platform:daily:tokens:"), org.mockito.ArgumentMatchers.eq(10L));
        verify(values).decrement(org.mockito.ArgumentMatchers.startsWith("agent:platform:daily:"));
    }

    @Test
    void rejectsASecondConcurrentGenerationForTheSameConversation() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked") ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.increment(org.mockito.ArgumentMatchers.startsWith("agent:session:inflight:"))).thenReturn(1L, 2L);
        AgentProperties properties = new AgentProperties();

        AgentRateLimiter limiter = new AgentRateLimiter(redis, properties);
        assertTrue(limiter.acquireSession(12L));
        assertFalse(limiter.acquireSession(12L));
        verify(values).decrement("agent:session:inflight:12");
    }

    @Test
    void rejectsIpBurstIndependentlyOfUserRateLimit() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked") ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.increment(org.mockito.ArgumentMatchers.startsWith("agent:rate:user:"))).thenReturn(1L);
        when(values.increment(org.mockito.ArgumentMatchers.startsWith("agent:rate:ip:"))).thenReturn(2L);
        AgentProperties properties = new AgentProperties(); properties.setMaxRequestsPerIpPerMinute(1);

        assertThrows(BusinessException.class, () -> new AgentRateLimiter(redis, properties).checkIp("203.0.113.5"));
    }
}
