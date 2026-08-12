package com.shanyuefang.user.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shanyuefang.common.exception.BusinessException;
import com.shanyuefang.common.result.ResultCode;
import com.shanyuefang.common.util.SnowflakeIdUtil;
import com.shanyuefang.user.domain.dto.LoginDTO;
import com.shanyuefang.user.domain.dto.LevelActionDTO;
import com.shanyuefang.user.domain.dto.CreditOperationDTO;
import com.shanyuefang.user.domain.dto.RegisterDTO;
import com.shanyuefang.user.domain.dto.UpdatePasswordDTO;
import com.shanyuefang.user.domain.dto.UpdateUserDTO;
import com.shanyuefang.user.domain.entity.User;
import com.shanyuefang.user.domain.vo.LevelActionResultVO;
import com.shanyuefang.user.domain.vo.LoginVO;
import com.shanyuefang.user.domain.vo.UserLevelTaskVO;
import com.shanyuefang.user.domain.vo.UserLevelVO;
import com.shanyuefang.user.domain.vo.UserVO;
import com.shanyuefang.user.mapper.UserMapper;
import com.shanyuefang.user.service.UserService;
import com.shanyuefang.user.service.CreditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.concurrent.TimeUnit;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();
    private static final String USER_CACHE_KEY = "user:info:";
    private static final long USER_CACHE_TTL = 30L; // 分钟

    // B 站风格等级：Lv0-Lv6
    private static final long[] LEVEL_EXP = {0L, 100L, 300L, 700L, 1500L, 3000L, 5500L};
    private static final String[] LEVEL_NAMES = {"Lv0 初识", "Lv1 渐读", "Lv2 入文", "Lv3 沉浸", "Lv4 通透", "Lv5 了然", "Lv6 臻阅"};

    private static final String ACTION_CHECKIN = "CHECKIN";
    private static final String ACTION_READ_SECONDS = "READ_SECONDS";
    private static final String ACTION_COMMENT = "COMMENT";
    private static final String ACTION_RATE = "RATE";

    private static final String KEY_TASK_PROGRESS = "user:level:task:progress:%d:%s";
    private static final String KEY_TASK_COMPLETED = "user:level:task:completed:%d:%s";

    private final RedisTemplate<String, Object> redisTemplate;
    private final CreditService creditService;

    private record DailyTaskRule(String taskId, String actionType, int target, int rewardExp, int rewardCredits,
                                 String title, String description) {}

    private List<DailyTaskRule> buildDailyTaskRules() {
        return List.of(
                new DailyTaskRule("CHECKIN_ONCE", ACTION_CHECKIN, 1, 12, 1,
                        "每日打卡", "完成 1 次打卡"),
                new DailyTaskRule("READ_30_MIN", ACTION_READ_SECONDS, 1800, 20, 2,
                        "每日阅读", "累计阅读 30 分钟"),
                new DailyTaskRule("WRITE_REVIEW", ACTION_COMMENT, 1, 18, 1,
                        "写点评", "发布 1 条点评"),
                new DailyTaskRule("RATE_BOOK", ACTION_RATE, 1, 10, 1,
                        "书籍评分", "提交 1 次评分")
        );
    }

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
        user.setExpTotal(0L);
        user.setStatus(1);

        // 3. 入库
        save(user);
        creditService.grantStarterCredits(user.getId());
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
        if (dto.getBio() != null) {
            user.setBio(dto.getBio());
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

    @Override
    public UserLevelVO getUserLevel(Long userId) {
        User user = getById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        long expTotal = user.getExpTotal() != null ? user.getExpTotal() : 0L;
        int level = calcLevel(expTotal);

        long currentLevelExp = LEVEL_EXP[level];
        long nextLevelExp = level >= LEVEL_EXP.length - 1 ? LEVEL_EXP[level] : LEVEL_EXP[level + 1];
        long needToNext = Math.max(0L, nextLevelExp - expTotal);
        int progressPercent = nextLevelExp == currentLevelExp
                ? 100
                : (int) Math.min(100, Math.max(0,
                Math.round((expTotal - currentLevelExp) * 100.0 / (nextLevelExp - currentLevelExp))));

        UserLevelVO vo = new UserLevelVO();
        vo.setLevel(level);
        vo.setLevelName(LEVEL_NAMES[level]);
        vo.setExpTotal(expTotal);
        vo.setCurrentLevelExp(currentLevelExp);
        vo.setNextLevelExp(nextLevelExp);
        vo.setNeedExpToNext(needToNext);
        vo.setProgressPercent(progressPercent);
        vo.setDailyTasks(buildDailyTasks(userId));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LevelActionResultVO recordLevelAction(Long userId, LevelActionDTO dto) {
        User user = getById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }

        String actionType = dto.getActionType().trim().toUpperCase();
        int value = dto.getValue();
        if (!Set.of(ACTION_CHECKIN, ACTION_COMMENT, ACTION_RATE).contains(actionType)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "不支持的行为类型");
        }

        return recordDailyAction(userId, actionType, value);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LevelActionResultVO recordVerifiedReading(Long userId, int seconds) {
        if (seconds <= 0 || seconds > 90) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "有效阅读时长必须在 1 到 90 秒之间");
        }
        return recordDailyAction(userId, ACTION_READ_SECONDS, seconds);
    }

    private LevelActionResultVO recordDailyAction(Long userId, String actionType, int value) {
        User user = getById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }

        LocalDate today = LocalDate.now();
        String progressKey = String.format(KEY_TASK_PROGRESS, userId, today);
        String completedKey = String.format(KEY_TASK_COMPLETED, userId, today);

        // 记录当日行为进度
        redisTemplate.opsForHash().increment(progressKey, actionType, value);
        redisTemplate.expire(progressKey, 2, TimeUnit.DAYS);
        redisTemplate.expire(completedKey, 2, TimeUnit.DAYS);

        int gainedExp = 0;
        for (DailyTaskRule rule : buildDailyTaskRules()) {
            Boolean done = redisTemplate.opsForSet().isMember(completedKey, rule.taskId());
            if (Boolean.TRUE.equals(done)) {
                continue;
            }
            if (!rule.actionType().equals(actionType)) {
                continue;
            }
            Object raw = redisTemplate.opsForHash().get(progressKey, rule.actionType());
            int progress = raw == null ? 0 : Integer.parseInt(String.valueOf(raw));
            if (progress >= rule.target()) {
                redisTemplate.opsForSet().add(completedKey, rule.taskId());
                gainedExp += rule.rewardExp();
                grantTaskCredits(userId, today, rule.taskId());
            }
        }

        long newExp = user.getExpTotal() != null ? user.getExpTotal() : 0L;
        if (gainedExp > 0) {
            lambdaUpdate()
                    .eq(User::getId, userId)
                    .setSql("exp_total = COALESCE(exp_total, 0) + " + gainedExp)
                    .update();
            User refreshed = getById(userId);
            newExp = refreshed != null && refreshed.getExpTotal() != null ? refreshed.getExpTotal() : newExp + gainedExp;
            redisTemplate.delete(USER_CACHE_KEY + userId);
        }

        return new LevelActionResultVO(gainedExp, newExp, calcLevel(newExp));
    }

    private int calcLevel(long expTotal) {
        for (int i = LEVEL_EXP.length - 1; i >= 0; i--) {
            if (expTotal >= LEVEL_EXP[i]) {
                return i;
            }
        }
        return 0;
    }

    private void grantTaskCredits(long userId, LocalDate date, String taskId) {
        int amount = switch (taskId) {
            case "CHECKIN_ONCE" -> 1;
            case "READ_30_MIN" -> 2;
            case "WRITE_REVIEW", "RATE_BOOK" -> 1;
            default -> throw new IllegalArgumentException("Unknown daily task: " + taskId);
        };
        if (amount == 0) return;
        CreditOperationDTO credit = new CreditOperationDTO();
        credit.setUserId(userId);
        credit.setAmount(amount);
        credit.setRequestId("credit:" + taskId + ":" + userId + ":" + date);
        credit.setReason(taskId);
        creditService.grant(credit);
    }

    private List<UserLevelTaskVO> buildDailyTasks(Long userId) {
        LocalDate today = LocalDate.now();
        String progressKey = String.format(KEY_TASK_PROGRESS, userId, today);
        String completedKey = String.format(KEY_TASK_COMPLETED, userId, today);
        Set<Object> completed = redisTemplate.opsForSet().members(completedKey);

        List<UserLevelTaskVO> list = new ArrayList<>();
        for (DailyTaskRule rule : buildDailyTaskRules()) {
            Object raw = redisTemplate.opsForHash().get(progressKey, rule.actionType());
            int progress = raw == null ? 0 : Integer.parseInt(String.valueOf(raw));

            UserLevelTaskVO vo = new UserLevelTaskVO();
            vo.setTaskId(rule.taskId());
            vo.setTitle(rule.title());
            vo.setDescription(rule.description());
            vo.setTarget(rule.target());
            vo.setProgress(Math.min(progress, rule.target()));
            vo.setRewardExp(rule.rewardExp());
            vo.setRewardCredits(rule.rewardCredits());
            vo.setCompleted(completed != null && completed.contains(rule.taskId()));
            list.add(vo);
        }
        return list;
    }

    /** Entity → VO（不暴露 password 等敏感字段）*/
    private UserVO toVO(User user) {
        UserVO vo = new UserVO();
        BeanUtils.copyProperties(user, vo);
        return vo;
    }
}
