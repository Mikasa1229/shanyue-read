package com.shanyuefang.interaction.service;

import com.shanyuefang.common.exception.BusinessException;
import com.shanyuefang.interaction.domain.dto.InteractionStatusDTO;
import com.shanyuefang.interaction.domain.entity.Interaction;
import com.shanyuefang.interaction.domain.vo.InteractionResultVO;
import com.shanyuefang.interaction.event.InteractionEventProducer;
import com.shanyuefang.interaction.mapper.InteractionMapper;
import com.shanyuefang.interaction.service.impl.InteractionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("互动服务单元测试")
class InteractionServiceTest {

    @Mock InteractionMapper interactionMapper;
    @Mock RedisTemplate<String, Object> redisTemplate;
    @Mock ValueOperations<String, Object> valueOps;
    @Mock InteractionEventProducer eventProducer;

    InteractionServiceImpl interactionService;

    @BeforeEach
    void setUp() {
        interactionService = new InteractionServiceImpl(redisTemplate, eventProducer);
        ReflectionTestUtils.setField(interactionService, "baseMapper", interactionMapper);
        ReflectionTestUtils.setField(interactionService, "entityClass", Interaction.class);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    // ── validateTargetType ────────────────────────────────────

    @Test
    @DisplayName("点赞 - 目标类型无效时抛出参数错误")
    void toggleLike_whenInvalidTargetType_throwsParamError() {
        assertThatThrownBy(() -> interactionService.toggleLike(1L, 100L, 99))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("目标类型不合法");
    }

    // ── toggleLike - 取消场景（Redis 缓存命中 active="1"）──────

    @Test
    @DisplayName("点赞 - 已点赞时取消点赞（Redis 缓存命中）")
    void toggleLike_whenAlreadyLiked_cancelsLike() {
        Long userId = 1L, targetId = 100L;
        int targetType = 1; // NOVEL

        // Redis returns "1" → already active
        when(valueOps.get(anyString())).thenReturn("1");
        when(interactionMapper.delete(any())).thenReturn(1);
        when(interactionMapper.countByTarget(targetId, targetType, 1)).thenReturn(9);
        when(redisTemplate.delete(anyString())).thenReturn(true);

        InteractionResultVO result = interactionService.toggleLike(userId, targetId, targetType);

        assertThat(result.getActive()).isFalse();
        assertThat(result.getCount()).isEqualTo(9);
        verify(interactionMapper).delete(any());
        verify(eventProducer).sendLikeEvent(targetId, targetType, userId, false);
    }

    // ── toggleLike - 新增场景（Redis 缓存命中 active="0"）──────

    @Test
    @DisplayName("点赞 - 未点赞时添加点赞（Redis 缓存命中 inactive）")
    void toggleLike_whenNotLiked_addsLike() {
        Long userId = 1L, targetId = 100L;
        int targetType = 1; // NOVEL

        // Redis returns "0" → not active
        when(valueOps.get(anyString())).thenReturn("0");
        when(interactionMapper.insert(any(Interaction.class))).thenReturn(1);
        when(interactionMapper.countByTarget(targetId, targetType, 1)).thenReturn(11);

        InteractionResultVO result = interactionService.toggleLike(userId, targetId, targetType);

        assertThat(result.getActive()).isTrue();
        assertThat(result.getCount()).isEqualTo(11);
        verify(interactionMapper).insert(any(Interaction.class));
        verify(valueOps).set(anyString(), eq("1"), eq(60L), eq(TimeUnit.MINUTES));
        verify(eventProducer).sendLikeEvent(targetId, targetType, userId, true);
    }

    // ── toggleFavorite ────────────────────────────────────────

    @Test
    @DisplayName("收藏 - 已收藏时取消收藏")
    void toggleFavorite_whenAlreadyFavorited_cancelsFavorite() {
        Long userId = 2L, targetId = 200L;

        when(valueOps.get(anyString())).thenReturn("1");
        when(interactionMapper.delete(any())).thenReturn(1);
        when(interactionMapper.countByTarget(targetId, 1, 2)).thenReturn(4);
        when(redisTemplate.delete(anyString())).thenReturn(true);

        InteractionResultVO result = interactionService.toggleFavorite(userId, targetId);

        assertThat(result.getActive()).isFalse();
        verify(eventProducer).sendFavoriteEvent(targetId, userId, false);
    }

    @Test
    @DisplayName("收藏 - 未收藏时添加收藏")
    void toggleFavorite_whenNotFavorited_addsFavorite() {
        Long userId = 2L, targetId = 200L;

        when(valueOps.get(anyString())).thenReturn("0");
        when(interactionMapper.insert(any(Interaction.class))).thenReturn(1);
        when(interactionMapper.countByTarget(targetId, 1, 2)).thenReturn(6);

        InteractionResultVO result = interactionService.toggleFavorite(userId, targetId);

        assertThat(result.getActive()).isTrue();
        assertThat(result.getCount()).isEqualTo(6);
        verify(eventProducer).sendFavoriteEvent(targetId, userId, true);
    }

    // ── batchQueryStatus ──────────────────────────────────────

    @Test
    @DisplayName("批量查询互动状态 - Redis 缓存命中时直接返回，不查 DB")
    void batchQueryStatus_whenAllCached_returnsFromCache() {
        InteractionStatusDTO dto = new InteractionStatusDTO();
        dto.setTargetIds(List.of(1L, 2L, 3L));
        dto.setTargetType(1);
        dto.setAction(1);

        when(valueOps.get(anyString()))
                .thenReturn("1")  // targetId=1 → liked
                .thenReturn("0")  // targetId=2 → not liked
                .thenReturn("1"); // targetId=3 → liked

        Map<Long, Boolean> result = interactionService.batchQueryStatus(1L, dto);

        assertThat(result).containsEntry(1L, true)
                          .containsEntry(2L, false)
                          .containsEntry(3L, true);
        verify(interactionMapper, never()).selectCount(any());
    }

    @Test
    @DisplayName("批量查询 - 空列表时返回空 Map")
    void batchQueryStatus_whenEmptyList_returnsEmptyMap() {
        InteractionStatusDTO dto = new InteractionStatusDTO();
        dto.setTargetIds(List.of());
        dto.setTargetType(1);
        dto.setAction(1);

        Map<Long, Boolean> result = interactionService.batchQueryStatus(1L, dto);

        assertThat(result).isEmpty();
    }
}
