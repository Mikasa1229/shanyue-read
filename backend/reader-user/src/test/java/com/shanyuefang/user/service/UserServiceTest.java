package com.shanyuefang.user.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shanyuefang.common.exception.BusinessException;
import com.shanyuefang.common.result.ResultCode;
import com.shanyuefang.user.domain.dto.LoginDTO;
import com.shanyuefang.user.domain.dto.RegisterDTO;
import com.shanyuefang.user.domain.dto.UpdatePasswordDTO;
import com.shanyuefang.user.domain.dto.UpdateUserDTO;
import com.shanyuefang.user.domain.entity.User;
import com.shanyuefang.user.domain.vo.LoginVO;
import com.shanyuefang.user.domain.vo.UserVO;
import com.shanyuefang.user.mapper.UserMapper;
import com.shanyuefang.user.service.impl.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("用户服务单元测试")
class UserServiceTest {

    @Mock UserMapper userMapper;
    @Mock RedisTemplate<String, Object> redisTemplate;
    @Mock ValueOperations<String, Object> valueOps;

    UserServiceImpl userService;

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    @BeforeEach
    void setUp() {
        userService = new UserServiceImpl(redisTemplate);
        ReflectionTestUtils.setField(userService, "baseMapper", userMapper);
        ReflectionTestUtils.setField(userService, "entityClass", User.class);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    // ── register ────────────────────────────────────────────

    @Test
    @DisplayName("注册 - 用户名已存在时抛出 CONFLICT 异常")
    void register_whenUsernameExists_throwsConflict() {
        RegisterDTO dto = new RegisterDTO();
        dto.setUsername("alice");
        dto.setPassword("pass123");

        when(userMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> userService.register(dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("用户名已被注册");

        verify(userMapper, never()).insert(any(User.class));
    }

    @Test
    @DisplayName("注册 - 新用户名时保存成功")
    void register_whenNewUsername_savesUser() {
        RegisterDTO dto = new RegisterDTO();
        dto.setUsername("bob");
        dto.setPassword("pass123");
        dto.setNickname("Bob");

        when(userMapper.selectCount(any())).thenReturn(0L);
        when(userMapper.insert(any(User.class))).thenReturn(1);

        assertThatNoException().isThrownBy(() -> userService.register(dto));
        verify(userMapper).insert(argThat((User u) -> "bob".equals(u.getUsername())));
    }

    // ── login ────────────────────────────────────────────────

    @Test
    @DisplayName("登录 - 用户不存在时抛出参数错误")
    void login_whenUserNotFound_throwsParamError() {
        LoginDTO dto = new LoginDTO();
        dto.setUsername("nobody");
        dto.setPassword("pass");

        when(userMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> userService.login(dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("用户名或密码错误");
    }

    @Test
    @DisplayName("登录 - 密码错误时抛出参数错误（防枚举攻击）")
    void login_whenWrongPassword_throwsParamError() {
        LoginDTO dto = new LoginDTO();
        dto.setUsername("alice");
        dto.setPassword("wrongpass");

        User user = mockUser(1L, "alice", ENCODER.encode("correct"), 1);
        when(userMapper.selectOne(any())).thenReturn(user);

        assertThatThrownBy(() -> userService.login(dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("用户名或密码错误");
    }

    @Test
    @DisplayName("登录 - 账号被封禁时抛出 FORBIDDEN")
    void login_whenAccountBanned_throwsForbidden() {
        LoginDTO dto = new LoginDTO();
        dto.setUsername("banned");
        dto.setPassword("pass");

        User user = mockUser(2L, "banned", ENCODER.encode("pass"), 0);
        when(userMapper.selectOne(any())).thenReturn(user);

        assertThatThrownBy(() -> userService.login(dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("封禁");
    }

    @Test
    @DisplayName("登录 - 凭证正确时返回 Token 和用户信息")
    void login_whenValidCredentials_returnsLoginVO() {
        LoginDTO dto = new LoginDTO();
        dto.setUsername("alice");
        dto.setPassword("pass");

        User user = mockUser(1L, "alice", ENCODER.encode("pass"), 1);
        when(userMapper.selectOne(any())).thenReturn(user);

        try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
            stpMock.when(() -> StpUtil.login(anyLong())).thenAnswer(inv -> null);
            stpMock.when(StpUtil::getTokenValue).thenReturn("token-abc");

            LoginVO result = userService.login(dto);

            assertThat(result.getToken()).isEqualTo("token-abc");
            assertThat(result.getUserInfo().getUsername()).isEqualTo("alice");
        }
    }

    // ── getCurrentUser ────────────────────────────────────────

    @Test
    @DisplayName("获取当前用户 - Redis 命中时直接返回缓存，不查 DB")
    void getCurrentUser_whenCacheHit_returnsCachedVO() {
        Long userId = 1L;
        UserVO cached = new UserVO();
        cached.setId(userId);

        when(valueOps.get("user:info:" + userId)).thenReturn(cached);

        UserVO result = userService.getCurrentUser(userId);

        assertThat(result).isSameAs(cached);
        verify(userMapper, never()).selectById(any());
    }

    @Test
    @DisplayName("获取当前用户 - 缓存未命中时查 DB 并回填缓存")
    void getCurrentUser_whenCacheMiss_queriesDbAndCaches() {
        Long userId = 1L;
        User user = mockUser(userId, "alice", "hash", 1);

        when(valueOps.get("user:info:" + userId)).thenReturn(null);
        when(userMapper.selectById(userId)).thenReturn(user);

        UserVO result = userService.getCurrentUser(userId);

        assertThat(result.getId()).isEqualTo(userId);
        verify(valueOps).set(eq("user:info:" + userId), any(), anyLong(), any(TimeUnit.class));
    }

    @Test
    @DisplayName("获取当前用户 - 用户不存在时抛出 NOT_FOUND")
    void getCurrentUser_whenUserNotFound_throwsNotFound() {
        when(valueOps.get(any())).thenReturn(null);
        when(userMapper.selectById(any())).thenReturn(null);

        assertThatThrownBy(() -> userService.getCurrentUser(99L))
                .isInstanceOf(BusinessException.class);
    }

    // ── updateUser ────────────────────────────────────────────

    @Test
    @DisplayName("更新用户信息 - 用户不存在时抛出 NOT_FOUND")
    void updateUser_whenUserNotFound_throwsNotFound() {
        when(userMapper.selectById(any())).thenReturn(null);

        UpdateUserDTO dto = new UpdateUserDTO();
        dto.setNickname("New Name");

        assertThatThrownBy(() -> userService.updateUser(1L, dto))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("更新用户信息 - 成功后删除缓存")
    void updateUser_whenValid_updatesAndEvictsCache() {
        Long userId = 1L;
        User user = mockUser(userId, "alice", "hash", 1);
        when(userMapper.selectById(userId)).thenReturn(user);
        when(userMapper.updateById(any(User.class))).thenReturn(1);

        UpdateUserDTO dto = new UpdateUserDTO();
        dto.setNickname("Updated");

        userService.updateUser(userId, dto);

        verify(userMapper).updateById(argThat((User u) -> "Updated".equals(u.getNickname())));
        verify(redisTemplate).delete("user:info:" + userId);
    }

    // ── updatePassword ────────────────────────────────────────

    @Test
    @DisplayName("修改密码 - 原密码错误时抛出参数错误")
    void updatePassword_whenOldPasswordWrong_throwsParamError() {
        Long userId = 1L;
        User user = mockUser(userId, "alice", ENCODER.encode("correct"), 1);
        when(userMapper.selectById(userId)).thenReturn(user);

        UpdatePasswordDTO dto = new UpdatePasswordDTO();
        dto.setOldPassword("wrong");
        dto.setNewPassword("newpass");

        assertThatThrownBy(() -> userService.updatePassword(userId, dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("原密码不正确");
    }

    @Test
    @DisplayName("修改密码 - 新旧密码相同时抛出参数错误")
    void updatePassword_whenSamePassword_throwsParamError() {
        Long userId = 1L;
        User user = mockUser(userId, "alice", ENCODER.encode("same"), 1);
        when(userMapper.selectById(userId)).thenReturn(user);

        UpdatePasswordDTO dto = new UpdatePasswordDTO();
        dto.setOldPassword("same");
        dto.setNewPassword("same");

        assertThatThrownBy(() -> userService.updatePassword(userId, dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("新密码不能与原密码相同");
    }

    @Test
    @DisplayName("修改密码 - 成功后强制登出")
    void updatePassword_whenValid_updatesAndForcesLogout() {
        Long userId = 1L;
        User user = mockUser(userId, "alice", ENCODER.encode("oldpass"), 1);
        when(userMapper.selectById(userId)).thenReturn(user);
        when(userMapper.updateById(any(User.class))).thenReturn(1);

        UpdatePasswordDTO dto = new UpdatePasswordDTO();
        dto.setOldPassword("oldpass");
        dto.setNewPassword("newpass123");

        try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
            stpMock.when(() -> StpUtil.logout(anyLong())).thenAnswer(inv -> null);

            userService.updatePassword(userId, dto);

            verify(userMapper).updateById(any(User.class));
            stpMock.verify(() -> StpUtil.logout(userId));
        }
    }

    // ── helper ───────────────────────────────────────────────

    private User mockUser(Long id, String username, String password, int status) {
        User u = new User();
        u.setId(id);
        u.setUsername(username);
        u.setNickname(username);
        u.setPassword(password);
        u.setStatus(status);
        return u;
    }
}
