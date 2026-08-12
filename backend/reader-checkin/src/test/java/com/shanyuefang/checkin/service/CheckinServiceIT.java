package com.shanyuefang.checkin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shanyuefang.checkin.domain.dto.CheckinDTO;
import com.shanyuefang.checkin.domain.entity.Checkin;
import com.shanyuefang.checkin.mapper.CheckinMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = "spring.config.import=")
@ActiveProfiles("test")
@Testcontainers
@DisplayName("CheckinService 集成测试")
class CheckinServiceIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    /** Mocks the actual bean the CheckinService injects */
    @MockBean(name = "redisTemplate")
    @SuppressWarnings("rawtypes")
    private RedisTemplate redisTemplate;

    /** 集成测试只验证打卡落库，隔离 Rabbit 生产端依赖。 */
    @MockBean
    private ConnectionFactory rabbitConnectionFactory;

    @MockBean
    private RabbitTemplate checkinRabbitTemplate;

    @Autowired
    private CheckinService checkinService;

    @Autowired
    private CheckinMapper checkinMapper;

    private static final Long TEST_USER_ID  = 600001L;
    private static final Long TEST_NOVEL_ID = 700001L;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        checkinMapper.delete(null);

        // stub RedisTemplate<String, Object>: getBit returns false (not checked in)
        ValueOperations<String, Object> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.getBit(anyString(), anyLong())).thenReturn(Boolean.FALSE);
        when(valueOps.setBit(anyString(), anyLong(), any(Boolean.class))).thenReturn(Boolean.FALSE);
        when(redisTemplate.expire(anyString(), anyLong(), any())).thenReturn(Boolean.TRUE);
        when(redisTemplate.delete(anyString())).thenReturn(Boolean.TRUE);
    }

    @Test
    @DisplayName("打卡成功 - 写入数据库")
    void checkin_success_persistsToDB() {
        CheckinDTO dto = new CheckinDTO();
        dto.setNovelId(TEST_NOVEL_ID);
        dto.setNote("今日读了第一章");

        checkinService.checkin(TEST_USER_ID, dto);

        long count = checkinMapper.selectCount(
            new LambdaQueryWrapper<Checkin>()
                .eq(Checkin::getUserId,     TEST_USER_ID)
                .eq(Checkin::getNovelId,    TEST_NOVEL_ID)
                .eq(Checkin::getCheckinDate, LocalDate.now())
        );
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("重复打卡 - 抛出异常（DB 唯一键兜底）")
    void checkin_duplicate_throws() {
        CheckinDTO dto = new CheckinDTO();
        dto.setNovelId(TEST_NOVEL_ID);
        dto.setNote("第一次打卡");

        // 第一次：成功
        checkinService.checkin(TEST_USER_ID, dto);

        // 第二次：Redis mock 仍返回 false（模拟缓存未命中），唯一键由 DB 抛异常
        assertThatThrownBy(() -> checkinService.checkin(TEST_USER_ID, dto))
            .isInstanceOf(RuntimeException.class);

        // DB 中仍只有一条记录
        long count = checkinMapper.selectCount(
            new LambdaQueryWrapper<Checkin>()
                .eq(Checkin::getUserId,     TEST_USER_ID)
                .eq(Checkin::getNovelId,    TEST_NOVEL_ID)
                .eq(Checkin::getCheckinDate, LocalDate.now())
        );
        assertThat(count).isEqualTo(1);
    }
}
