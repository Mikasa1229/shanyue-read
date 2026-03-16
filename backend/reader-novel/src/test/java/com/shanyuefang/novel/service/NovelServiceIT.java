package com.shanyuefang.novel.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shanyuefang.novel.domain.dto.CreateNovelDTO;
import com.shanyuefang.novel.domain.entity.Novel;
import com.shanyuefang.novel.mapper.NovelMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

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
@DisplayName("NovelService 集成测试")
class NovelServiceIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @MockBean
    private ConnectionFactory connectionFactory;

    @MockBean
    private StringRedisTemplate redisTemplate;

    @Autowired
    private NovelService novelService;

    @Autowired
    private NovelMapper novelMapper;

    private static final Long TEST_USER_ID = 100001L;

    @BeforeEach
    void setUp() {
        novelMapper.delete(null);

        // stub StringRedisTemplate so service methods don't NPE on Redis calls
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);
    }

    @Test
    @DisplayName("创建小说 - 写入数据库")
    void createNovel_writesToDB() {
        CreateNovelDTO dto = new CreateNovelDTO();
        dto.setTitle("测试小说");
        dto.setAuthorName("测试作者");
        dto.setCategory("玄幻");
        dto.setSummary("这是一个测试小说");
        dto.setStatus(1);

        novelService.create(TEST_USER_ID, dto);

        long count = novelMapper.selectCount(
            new LambdaQueryWrapper<Novel>()
                .eq(Novel::getTitle, "测试小说")
                .eq(Novel::getUserId, TEST_USER_ID)
        );
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("查询不存在的小说 - 抛出异常")
    void getDetail_notFound_throws() {
        assertThatThrownBy(() -> novelService.getDetail(999999999L))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("软删除小说 - 记录标记为已删除")
    void deleteNovel_softDeletes() {
        CreateNovelDTO dto = new CreateNovelDTO();
        dto.setTitle("待删除小说");
        dto.setAuthorName("作者");
        dto.setCategory("都市");
        dto.setStatus(1);

        var vo = novelService.create(TEST_USER_ID, dto);
        Long novelId = vo.getId();

        novelService.delete(TEST_USER_ID, novelId);

        // MyBatis-Plus logic delete: selectById with deleted=false should return null
        Novel found = novelMapper.selectById(novelId);
        assertThat(found).isNull();
    }
}
