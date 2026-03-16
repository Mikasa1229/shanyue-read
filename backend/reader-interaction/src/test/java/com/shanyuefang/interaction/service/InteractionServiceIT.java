package com.shanyuefang.interaction.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shanyuefang.interaction.domain.entity.Interaction;
import com.shanyuefang.interaction.domain.vo.InteractionResultVO;
import com.shanyuefang.interaction.event.InteractionEventProducer;
import com.shanyuefang.interaction.mapper.InteractionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = "spring.config.import=")
@ActiveProfiles("test")
@Testcontainers
@DisplayName("InteractionService 集成测试")
class InteractionServiceIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @MockBean
    private ConnectionFactory connectionFactory;

    /** Mocks the actual bean the InteractionService injects */
    @MockBean(name = "redisTemplate")
    @SuppressWarnings("rawtypes")
    private RedisTemplate redisTemplate;

    @MockBean
    private InteractionEventProducer interactionEventProducer;

    @Autowired
    private InteractionService interactionService;

    @Autowired
    private InteractionMapper interactionMapper;

    private static final Long TEST_USER_ID   = 400001L;
    private static final Long TEST_TARGET_ID = 500001L;
    /** targetType = 1 (NOVEL) */
    private static final int  TARGET_TYPE    = 1;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        interactionMapper.delete(null);

        // stub RedisTemplate<String, Object> so service can proceed without a real Redis
        ValueOperations<String, Object> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        // Always return null from cache so service falls back to DB lookup
        when(valueOps.get(anyString())).thenReturn(null);
        doNothing().when(valueOps).set(anyString(), any(), anyLong(), any());
        when(redisTemplate.delete(anyString())).thenReturn(Boolean.TRUE);

        // stub event producer
        doNothing().when(interactionEventProducer).sendLikeEvent(anyLong(), anyInt(), anyLong(), any(Boolean.class));
        doNothing().when(interactionEventProducer).sendFavoriteEvent(anyLong(), anyLong(), any(Boolean.class));
    }

    @Test
    @DisplayName("toggleLike - 首次点赞创建记录")
    void toggleLike_firstTime_createsRecord() {
        InteractionResultVO result = interactionService.toggleLike(TEST_USER_ID, TEST_TARGET_ID, TARGET_TYPE);

        assertThat(result).isNotNull();
        assertThat(result.getActive()).isTrue();

        long count = interactionMapper.selectCount(
            new LambdaQueryWrapper<Interaction>()
                .eq(Interaction::getUserId,    TEST_USER_ID)
                .eq(Interaction::getTargetId,  TEST_TARGET_ID)
                .eq(Interaction::getTargetType, TARGET_TYPE)
                .eq(Interaction::getAction, 1) // ActionType.LIKE = 1
        );
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("toggleLike - 再次点赞取消，删除记录")
    void toggleLike_secondTime_deletesRecord() {
        // 第一次：点赞
        interactionService.toggleLike(TEST_USER_ID, TEST_TARGET_ID, TARGET_TYPE);

        // 确认已创建
        long countAfterFirst = interactionMapper.selectCount(
            new LambdaQueryWrapper<Interaction>()
                .eq(Interaction::getUserId,    TEST_USER_ID)
                .eq(Interaction::getTargetId,  TEST_TARGET_ID)
                .eq(Interaction::getTargetType, TARGET_TYPE)
                .eq(Interaction::getAction, 1)
        );
        assertThat(countAfterFirst).isEqualTo(1);

        // 第二次：取消点赞
        InteractionResultVO cancelResult = interactionService.toggleLike(TEST_USER_ID, TEST_TARGET_ID, TARGET_TYPE);
        assertThat(cancelResult.getActive()).isFalse();

        long countAfterSecond = interactionMapper.selectCount(
            new LambdaQueryWrapper<Interaction>()
                .eq(Interaction::getUserId,    TEST_USER_ID)
                .eq(Interaction::getTargetId,  TEST_TARGET_ID)
                .eq(Interaction::getTargetType, TARGET_TYPE)
                .eq(Interaction::getAction, 1)
        );
        assertThat(countAfterSecond).isEqualTo(0);
    }
}
