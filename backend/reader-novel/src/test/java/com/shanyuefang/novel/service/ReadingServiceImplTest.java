package com.shanyuefang.novel.service;

import com.shanyuefang.novel.domain.dto.ReadingHeartbeatDTO;
import com.shanyuefang.novel.domain.vo.ReadingSessionVO;
import com.shanyuefang.novel.feign.UserFeignClient;
import com.shanyuefang.novel.service.impl.ReadingServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReadingServiceImplTest {

    @Mock private StringRedisTemplate redis;
    @Mock private UserFeignClient userClient;
    @Mock private HashOperations<String, Object, Object> hashes;
    @Mock private ZSetOperations<String, String> rankings;
    @Mock private ValueOperations<String, String> values;

    @Test
    void heartbeat_usesAtomicDailyClaimAndOnlyRanksAcceptedSeconds() {
        ReadingServiceImpl service = new ReadingServiceImpl(redis, userClient);
        ReadingHeartbeatDTO dto = new ReadingHeartbeatDTO();
        dto.setSessionToken("session-token");
        dto.setPageVisible(true);
        when(redis.opsForHash()).thenReturn(hashes);
        when(hashes.get("reading:session:session-token", "userId")).thenReturn("8");
        when(hashes.get("reading:session:session-token", "lastHeartbeat")).thenReturn(String.valueOf(System.currentTimeMillis() - 30_000));
        when(hashes.get("reading:session:session-token", "qualifiedSeconds")).thenReturn("1790");
        when(redis.execute(any(DefaultRedisScript.class), anyList(), anyString(), anyString(), anyString())).thenReturn(10L);
        when(redis.opsForZSet()).thenReturn(rankings);
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), anyString(), any())).thenReturn(false);
        when(redis.getExpire(anyString(), any())).thenReturn(-1L);

        ReadingSessionVO result = service.heartbeat(8L, dto);

        assertThat(result.getQualifiedSeconds()).isEqualTo(1800L);
        verify(redis).execute(any(DefaultRedisScript.class), anyList(), eq("30"), eq("14400"), eq("172800"));
        verify(rankings).incrementScore(anyString(), eq("8"), eq(10D));
    }

    @Test
    void hiddenHeartbeatDoesNotClaimTimeOrChangeRanking() {
        ReadingServiceImpl service = new ReadingServiceImpl(redis, userClient);
        ReadingHeartbeatDTO dto = new ReadingHeartbeatDTO();
        dto.setSessionToken("session-token");
        dto.setPageVisible(false);
        when(redis.opsForHash()).thenReturn(hashes);
        when(hashes.get("reading:session:session-token", "userId")).thenReturn("8");
        when(hashes.get("reading:session:session-token", "lastHeartbeat")).thenReturn(String.valueOf(System.currentTimeMillis() - 30_000));
        when(hashes.get("reading:session:session-token", "qualifiedSeconds")).thenReturn("40");

        ReadingSessionVO result = service.heartbeat(8L, dto);

        assertThat(result.getQualifiedSeconds()).isEqualTo(40L);
        verify(redis, never()).execute(any(DefaultRedisScript.class), anyList(), anyString(), anyString(), anyString());
        verify(redis, never()).opsForZSet();
    }
}
