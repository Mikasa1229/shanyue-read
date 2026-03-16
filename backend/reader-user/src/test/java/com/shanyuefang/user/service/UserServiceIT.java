package com.shanyuefang.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shanyuefang.user.domain.dto.RegisterDTO;
import com.shanyuefang.user.domain.entity.User;
import com.shanyuefang.user.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "spring.config.import=")
@ActiveProfiles("test")
@Testcontainers
@DisplayName("UserService 集成测试")
class UserServiceIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private UserService userService;

    @Autowired
    private UserMapper userMapper;

    @BeforeEach
    void cleanUp() {
        userMapper.delete(null);
    }

    @Test
    @DisplayName("注册成功 - 用户写入数据库")
    void register_success_persisted() {
        RegisterDTO dto = new RegisterDTO();
        dto.setUsername("testuser");
        dto.setPassword("password123");
        dto.setNickname("测试用户");

        userService.register(dto);

        long count = userMapper.selectCount(
            new LambdaQueryWrapper<User>()
                .eq(User::getUsername, "testuser")
        );
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("用户名重复注册抛出异常")
    void register_duplicateUsername_throws() {
        RegisterDTO dto = new RegisterDTO();
        dto.setUsername("dup_user");
        dto.setPassword("password123");
        dto.setNickname("重复");

        userService.register(dto);

        assertThatThrownBy(() -> userService.register(dto))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("注册后可通过用户名查到用户记录")
    void register_canQueryByUsername() {
        RegisterDTO dto = new RegisterDTO();
        dto.setUsername("queryuser");
        dto.setPassword("pass123456");
        dto.setNickname("查询用户");

        userService.register(dto);

        User found = userMapper.selectOne(
            new LambdaQueryWrapper<User>()
                .eq(User::getUsername, "queryuser")
        );
        assertThat(found).isNotNull();
        assertThat(found.getNickname()).isEqualTo("查询用户");
        // 密码应已加密，不应与原始密码相同
        assertThat(found.getPassword()).isNotEqualTo("pass123456");
    }
}
