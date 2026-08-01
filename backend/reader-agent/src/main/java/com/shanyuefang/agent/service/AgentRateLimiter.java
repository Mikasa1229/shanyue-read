package com.shanyuefang.agent.service;

import com.shanyuefang.agent.config.AgentProperties;
import com.shanyuefang.common.exception.BusinessException;
import com.shanyuefang.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

/** Per-user fixed-window guard that protects both platform credits and BYOK proxy capacity. */
@Component
@RequiredArgsConstructor
public class AgentRateLimiter {
    private final StringRedisTemplate redisTemplate;
    private final AgentProperties properties;

    public void check(long userId) {
        check(userId, null);
    }

    public void check(long userId, String clientIp) {
        long window = Instant.now().getEpochSecond() / 60;
        try {
            enforceWindow("agent:rate:user:" + userId + ":" + window, properties.getMaxRequestsPerMinute(), "Too many Agent requests; please retry shortly");
            checkIp(clientIp);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception ignored) {
            // Redis is a protective layer, not a reason to make a reader-facing feature unavailable.
        }
    }

    public void checkIp(String clientIp) {
        if (clientIp == null || clientIp.isBlank() || "unknown".equals(clientIp)) return;
        long window = Instant.now().getEpochSecond() / 60;
        try {
            enforceWindow("agent:rate:ip:" + clientIp + ":" + window, properties.getMaxRequestsPerIpPerMinute(), "Too many Agent requests from this network; please retry shortly");
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception ignored) {
            // Redis is a protective layer, not a reason to make a reader-facing feature unavailable.
        }
    }

    public boolean acquireSession(long sessionId) {
        String key = "agent:session:inflight:" + sessionId;
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) redisTemplate.expire(key, Duration.ofMinutes(2));
            if (count != null && count > Math.max(1, properties.getMaxConcurrentRequestsPerSession())) {
                redisTemplate.opsForValue().decrement(key);
                return false;
            }
            return true;
        } catch (Exception ignored) {
            // Redis is unavailable: preserve the existing reader-facing availability behavior.
            return true;
        }
    }

    public void releaseSession(long sessionId) {
        try { redisTemplate.opsForValue().decrement("agent:session:inflight:" + sessionId); }
        catch (Exception ignored) { }
    }

    private void enforceWindow(String key, int limit, String message) {
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) redisTemplate.expire(key, Duration.ofMinutes(2));
        if (count != null && count > Math.max(1, limit)) {
            throw new BusinessException(ResultCode.FORBIDDEN, message);
        }
    }

    public void reservePlatformBudget(int estimatedTokens) {
        String key = "agent:platform:daily:" + LocalDate.now();
        String tokenKey = "agent:platform:daily:tokens:" + LocalDate.now();
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) redisTemplate.expire(key, Duration.ofDays(2));
            if (count != null && count > properties.getGlobalDailyPlatformRequests()) {
                // This failed reservation must not consume tomorrow's remaining budget window.
                redisTemplate.opsForValue().decrement(key);
                throw new BusinessException(ResultCode.FORBIDDEN, "Platform Agent trial budget is exhausted; use BYOK or retry after the next reward window");
            }
            Long tokens = redisTemplate.opsForValue().increment(tokenKey, Math.max(1, estimatedTokens));
            if (tokens != null && tokens == Math.max(1, estimatedTokens)) redisTemplate.expire(tokenKey, Duration.ofDays(2));
            if (tokens != null && tokens > properties.getGlobalDailyPlatformTokens()) {
                redisTemplate.opsForValue().decrement(tokenKey, Math.max(1, estimatedTokens));
                redisTemplate.opsForValue().decrement(key);
                throw new BusinessException(ResultCode.FORBIDDEN, "Platform Agent token budget is exhausted; use BYOK or retry later");
            }
        } catch (BusinessException exception) { throw exception; }
        catch (Exception ignored) { /* Keep the service available if Redis is unavailable. */ }
    }

    public void checkPlatformCircuit() {
        try {
            if (Boolean.TRUE.equals(redisTemplate.hasKey("agent:platform:circuit:open"))) {
                throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE, "Platform model is temporarily recovering; use BYOK or retry shortly");
            }
        } catch (BusinessException exception) { throw exception; }
        catch (Exception ignored) { /* Redis outage must not block model access. */ }
    }

    public void recordPlatformSuccess() {
        try { redisTemplate.delete("agent:platform:circuit:failures"); }
        catch (Exception ignored) { }
    }

    public void recordPlatformFailure() {
        try {
            String failuresKey = "agent:platform:circuit:failures";
            Long failures = redisTemplate.opsForValue().increment(failuresKey);
            if (failures != null && failures == 1L) redisTemplate.expire(failuresKey, Duration.ofMinutes(5));
            if (failures != null && failures >= properties.getPlatformCircuitFailureThreshold()) {
                redisTemplate.opsForValue().set("agent:platform:circuit:open", "1", Duration.ofSeconds(properties.getPlatformCircuitOpenSeconds()));
                redisTemplate.delete(failuresKey);
            }
        } catch (Exception ignored) { }
    }

    public void releasePlatformTokenBudget(int estimatedTokens) {
        try { redisTemplate.opsForValue().decrement("agent:platform:daily:tokens:" + LocalDate.now(), Math.max(1, estimatedTokens)); }
        catch (Exception ignored) { /* The daily budget will naturally expire. */ }
    }
}
