package com.shanyuefang.user.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shanyuefang.common.exception.BusinessException;
import com.shanyuefang.common.result.ResultCode;
import com.shanyuefang.common.util.SnowflakeIdUtil;
import com.shanyuefang.user.domain.dto.LoginDTO;
import com.shanyuefang.user.domain.dto.RegisterDTO;
import com.shanyuefang.user.domain.dto.UpdatePasswordDTO;
import com.shanyuefang.user.domain.dto.UpdateUserDTO;
import com.shanyuefang.user.domain.entity.User;
import com.shanyuefang.user.domain.vo.LoginVO;
import com.shanyuefang.user.domain.vo.UserVO;
import com.shanyuefang.user.mapper.UserMapper;
import com.shanyuefang.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();
    private static final String USER_CACHE_KEY = "user:info:";
    private static final long USER_CACHE_TTL = 30L; // 分钟

    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void register(RegisterDTO dto) {
        // 1. 用户名唯一校验
        boolean exists = lambdaQuery()
                .eq(User::getUsername, dto.getUsername())
                .exists();
        if (exists) {
            throw new BusinessException(ResultCode.CONFLICT, "用户名已被注册");
        }

        // 2. 构建用户实体
        User user = new User();
        user.setId(SnowflakeIdUtil.next());
        user.setUsername(dto.getUsername());
        user.setPassword(PASSWORD_ENCODER.encode(dto.getPassword()));
        user.setNickname(StringUtils.hasText(dto.getNickname()) ? dto.getNickname() : dto.getUsername());
        user.setStatus(1);

        // 3. 入库
        save(user);
        log.info("用户注册成功: userId={}, username={}", user.getId(), user.getUsername());
    }

    @Override
    public LoginVO login(LoginDTO dto) {
        // 1. 查用户
        User user = lambdaQuery()
                .eq(User::getUsername, dto.getUsername())
                .one();

        // 2. 校验密码（用户不存在和密码错误返回同一提示，防止用户名枚举）
        if (user == null || !PASSWORD_ENCODER.matches(dto.getPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "用户名或密码错误");
        }

        // 3. 账号状态校验
        if (user.getStatus() == 0) {
            throw new BusinessException(ResultCode.FORBIDDEN, "账号已被封禁，请联系客服");
        }

        // 4. Sa-Token 登录，颁发 Token
        StpUtil.login(user.getId());
        String token = StpUtil.getTokenValue();

        log.info("用户登录成功: userId={}", user.getId());
        return new LoginVO(token, toVO(user));
    }

    @Override
    public void logout() {
        StpUtil.logout();
    }

    @Override
    public UserVO getCurrentUser(Long userId) {
        // 1. 先查 Redis 缓存
        String cacheKey = USER_CACHE_KEY + userId;
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached instanceof UserVO vo) {
            return vo;
        }

        // 2. 缓存未命中，查库
        User user = getById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }

        // 3. 回填缓存
        UserVO vo = toVO(user);
        redisTemplate.opsForValue().set(cacheKey, vo, USER_CACHE_TTL, TimeUnit.MINUTES);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserVO updateUser(Long userId, UpdateUserDTO dto) {
        User user = getById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }

        // 只更新有值的字段
        if (StringUtils.hasText(dto.getNickname())) {
            user.setNickname(dto.getNickname());
        }
        if (StringUtils.hasText(dto.getAvatar())) {
            user.setAvatar(dto.getAvatar());
        }

        updateById(user);

        // 删除缓存（Cache Aside：先写库再删缓存）
        redisTemplate.delete(USER_CACHE_KEY + userId);
        log.info("用户信息更新: userId={}", userId);

        return toVO(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePassword(Long userId, UpdatePasswordDTO dto) {
        User user = getById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }

        // 校验原密码
        if (!PASSWORD_ENCODER.matches(dto.getOldPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "原密码不正确");
        }

        // 新旧密码不能相同
        if (PASSWORD_ENCODER.matches(dto.getNewPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "新密码不能与原密码相同");
        }

        user.setPassword(PASSWORD_ENCODER.encode(dto.getNewPassword()));
        updateById(user);

        // 强制下线，要求重新登录
        StpUtil.logout(userId);
        log.info("用户修改密码并强制下线: userId={}", userId);
    }

    /** Entity → VO（不暴露 password 等敏感字段）*/
    private UserVO toVO(User user) {
        UserVO vo = new UserVO();
        BeanUtils.copyProperties(user, vo);
        return vo;
    }
}
