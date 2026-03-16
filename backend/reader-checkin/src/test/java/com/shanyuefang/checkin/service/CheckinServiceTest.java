package com.shanyuefang.checkin.service;

import com.shanyuefang.checkin.domain.dto.CheckinDTO;
import com.shanyuefang.checkin.domain.entity.Checkin;
import com.shanyuefang.checkin.domain.vo.StreakVO;
import com.shanyuefang.checkin.mapper.CheckinMapper;
import com.shanyuefang.checkin.service.impl.CheckinServiceImpl;
import com.shanyuefang.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("打卡服务单元测试")
class CheckinServiceTest {

    @Mock CheckinMapper checkinMapper;
    @Mock RedisTemplate<String, Object> redisTemplate;
    @Mock ValueOperations<String, Object> valueOps;

    CheckinServiceImpl checkinService;

    @BeforeEach
    void setUp() {
        checkinService = new CheckinServiceImpl(redisTemplate);
        ReflectionTestUtils.setField(checkinService, "baseMapper", checkinMapper);
        ReflectionTestUtils.setField(checkinService, "entityClass", Checkin.class);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    // ── checkin ───────────────────────────────────────────────

    @Test
    @DisplayName("打卡 - Redis Bitmap 显示今日已打卡时抛出 CONFLICT")
    void checkin_whenAlreadyCheckedInRedis_throwsConflict() {
        Long userId = 1L;
        CheckinDTO dto = new CheckinDTO();
        dto.setNovelId(100L);

        // Bitmap returns true → already checked in today
        when(valueOps.getBit(anyString(), anyLong())).thenReturn(true);

        assertThatThrownBy(() -> checkinService.checkin(userId, dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("今日已打卡");
    }

    @Test
    @DisplayName("打卡 - Redis 未命中时写入 DB、更新 Bitmap、清除 streak 缓存")
    void checkin_whenNotCheckedIn_successAndUpdatesBitmapAndEvictsStreak() {
        Long userId = 1L;
        CheckinDTO dto = new CheckinDTO();
        dto.setNovelId(100L);

        // Bitmap returns false → not yet checked in
        when(valueOps.getBit(anyString(), anyLong())).thenReturn(false);
        when(checkinMapper.insert(any(Checkin.class))).thenReturn(1);
        when(valueOps.setBit(anyString(), anyLong(), eq(true))).thenReturn(false);
        when(redisTemplate.expire(anyString(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(redisTemplate.delete(anyString())).thenReturn(true);

        assertThatNoException().isThrownBy(() -> checkinService.checkin(userId, dto));

        verify(checkinMapper).insert(argThat((Checkin c) -> Long.valueOf(1L).equals(c.getUserId())));
        verify(valueOps).setBit(anyString(), anyLong(), eq(true));
        verify(redisTemplate).delete(contains("streak:" + userId));
    }

    // ── getStreak ─────────────────────────────────────────────

    @Test
    @DisplayName("获取连续打卡 - Redis 缓存命中时直接返回，不查 DB")
    void getStreak_whenCacheHit_returnsCachedVO() {
        Long userId = 1L;
        StreakVO cached = new StreakVO();
        cached.setCurrentStreak(7);
        cached.setLongestStreak(14);
        cached.setTotalDays(30);
        cached.setCheckedToday(true);

        when(valueOps.get("checkin:streak:" + userId)).thenReturn(cached);

        StreakVO result = checkinService.getStreak(userId);

        assertThat(result).isSameAs(cached);
        assertThat(result.getCurrentStreak()).isEqualTo(7);
        verify(checkinMapper, never()).countTotalDays(any());
    }

    @Test
    @DisplayName("获取连续打卡 - 缓存未命中时查 DB 并回填缓存")
    void getStreak_whenCacheMiss_queriesDbAndCaches() {
        Long userId = 1L;

        when(valueOps.get("checkin:streak:" + userId)).thenReturn(null);
        // Today not checked in (bitmap returns null/false)
        when(valueOps.getBit(anyString(), anyLong())).thenReturn(false);
        when(checkinMapper.countTotalDays(userId)).thenReturn(10);
        when(checkinMapper.selectList(any())).thenReturn(java.util.Collections.emptyList());

        StreakVO result = checkinService.getStreak(userId);

        assertThat(result.getTotalDays()).isEqualTo(10);
        assertThat(result.getCurrentStreak()).isEqualTo(0);
        verify(valueOps).set(eq("checkin:streak:" + userId), any(StreakVO.class), anyLong(), eq(TimeUnit.SECONDS));
    }

    // ── getMonthCalendar ──────────────────────────────────────

    @Test
    @DisplayName("获取月历 - Bitmap 数据存在时直接返回，不查 DB")
    void getMonthCalendar_whenBitmapHasData_returnsWithoutDbQuery() {
        Long userId = 1L;

        // Simulate day 1 checked, others not
        when(valueOps.getBit(anyString(), anyLong()))
                .thenAnswer(inv -> {
                    long offset = (long) inv.getArgument(1);
                    return offset == 0L; // day 1 (offset 0) is checked
                });

        var result = checkinService.getMonthCalendar(userId, 2025, 1);

        assertThat(result.getYear()).isEqualTo(2025);
        assertThat(result.getMonth()).isEqualTo(1);
        assertThat(result.getCheckedDays()).contains(1);
        assertThat(result.getTotalDays()).isGreaterThanOrEqualTo(1);
        // DB should NOT be queried since bitmap has data
        verify(checkinMapper, never()).selectList(any());
    }
}
