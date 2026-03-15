package com.shanyuefang.checkin.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shanyuefang.checkin.domain.dto.CheckinDTO;
import com.shanyuefang.checkin.domain.entity.Checkin;
import com.shanyuefang.checkin.domain.vo.CheckinCalendarVO;
import com.shanyuefang.checkin.domain.vo.StreakVO;
import com.shanyuefang.checkin.mapper.CheckinMapper;
import com.shanyuefang.checkin.service.CheckinService;
import com.shanyuefang.common.exception.BusinessException;
import com.shanyuefang.common.result.ResultCode;
import com.shanyuefang.common.util.SnowflakeIdUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class CheckinServiceImpl extends ServiceImpl<CheckinMapper, Checkin> implements CheckinService {

    private final RedisTemplate<String, Object> redisTemplate;

    /** Redis Bitmap key: checkin:bitmap:{userId}:{yyyyMM} */
    private static final String BITMAP_KEY = "checkin:bitmap:%d:%s";
    /** Redis streak cache key */
    private static final String STREAK_KEY = "checkin:streak:%d";

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void checkin(Long userId, CheckinDTO dto) {
        LocalDate today = LocalDate.now();
        String monthKey = bitmapKey(userId, today);
        int dayBit = today.getDayOfMonth() - 1; // 0-indexed

        // 1. Redis Bitmap 快速判断今日是否已打卡
        Boolean alreadyChecked = redisTemplate.opsForValue().getBit(monthKey, dayBit);
        if (Boolean.TRUE.equals(alreadyChecked)) {
            throw new BusinessException(ResultCode.CONFLICT, "今日已打卡，明日再来！");
        }

        // 2. 写入 DB（唯一键兜底幂等，防止 Redis 重启后的并发重复）
        Checkin checkin = new Checkin();
        checkin.setId(SnowflakeIdUtil.next());
        checkin.setUserId(userId);
        checkin.setNovelId(dto.getNovelId());
        checkin.setCheckinDate(today);
        checkin.setNote(dto.getNote());
        checkin.setCreatedAt(LocalDateTime.now());

        try {
            save(checkin);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ResultCode.CONFLICT, "今日已打卡，明日再来！");
        }

        // 3. 设置 Redis Bitmap（TTL 到月末 + 1天）
        redisTemplate.opsForValue().setBit(monthKey, dayBit, true);
        long ttlDays = YearMonth.from(today).lengthOfMonth() - today.getDayOfMonth() + 2L;
        redisTemplate.expire(monthKey, ttlDays, TimeUnit.DAYS);

        // 4. 删除 streak 缓存，下次查询重新计算
        redisTemplate.delete(String.format(STREAK_KEY, userId));

        log.info("打卡成功: userId={}, date={}, novelId={}", userId, today, dto.getNovelId());
    }

    @Override
    public CheckinCalendarVO getMonthCalendar(Long userId, int year, int month) {
        String key = bitmapKey(userId, LocalDate.of(year, month, 1));
        int daysInMonth = YearMonth.of(year, month).lengthOfMonth();

        List<Integer> checkedDays = new ArrayList<>();
        for (int day = 1; day <= daysInMonth; day++) {
            Boolean checked = redisTemplate.opsForValue().getBit(key, day - 1);
            if (Boolean.TRUE.equals(checked)) {
                checkedDays.add(day);
            }
        }

        // 若 Bitmap 为空（缓存过期），从 DB 重建
        if (checkedDays.isEmpty()) {
            checkedDays = rebuildFromDB(userId, year, month, daysInMonth, key);
        }

        CheckinCalendarVO vo = new CheckinCalendarVO();
        vo.setYear(year);
        vo.setMonth(month);
        vo.setCheckedDays(checkedDays);
        vo.setTotalDays(checkedDays.size());
        return vo;
    }

    @Override
    public StreakVO getStreak(Long userId) {
        String cacheKey = String.format(STREAK_KEY, userId);
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached instanceof StreakVO vo) {
            return vo;
        }

        StreakVO vo = calculateStreak(userId);

        // 缓存到次日凌晨（TTL 到明天结束的秒数）
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        long ttlSeconds = tomorrow.toEpochDay() * 86400 - System.currentTimeMillis() / 1000;
        redisTemplate.opsForValue().set(cacheKey, vo, ttlSeconds, TimeUnit.SECONDS);
        return vo;
    }

    // ─────────── 私有方法 ───────────

    private StreakVO calculateStreak(Long userId) {
        LocalDate today = LocalDate.now();
        StreakVO vo = new StreakVO();

        // 今日是否已打卡
        Boolean todayChecked = redisTemplate.opsForValue().getBit(bitmapKey(userId, today), today.getDayOfMonth() - 1);
        vo.setCheckedToday(Boolean.TRUE.equals(todayChecked));

        // 从今天往回遍历，计算连续天数
        int current = 0;
        LocalDate cursor = today;
        while (true) {
            Boolean checked = redisTemplate.opsForValue().getBit(bitmapKey(userId, cursor), cursor.getDayOfMonth() - 1);
            if (!Boolean.TRUE.equals(checked)) break;
            current++;
            cursor = cursor.minusDays(1);
        }
        vo.setCurrentStreak(current);

        // 累计总天数（从 DB 查）
        vo.setTotalDays(baseMapper.countTotalDays(userId));

        // 历史最长（简化：从 DB 计算）
        vo.setLongestStreak(calcLongestFromDB(userId));
        return vo;
    }

    /** 从 DB 重建 Bitmap 并返回已打卡日列表 */
    private List<Integer> rebuildFromDB(Long userId, int year, int month, int daysInMonth, String key) {
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = LocalDate.of(year, month, daysInMonth);

        List<Checkin> records = lambdaQuery()
                .eq(Checkin::getUserId, userId)
                .between(Checkin::getCheckinDate, start, end)
                .list();

        List<Integer> checkedDays = new ArrayList<>();
        for (Checkin c : records) {
            int day = c.getCheckinDate().getDayOfMonth();
            checkedDays.add(day);
            redisTemplate.opsForValue().setBit(key, day - 1, true);
        }
        if (!records.isEmpty()) {
            redisTemplate.expire(key, 33, TimeUnit.DAYS);
        }
        return checkedDays;
    }

    /** 从 DB 计算历史最长连续天数 */
    private int calcLongestFromDB(Long userId) {
        List<Checkin> all = lambdaQuery()
                .eq(Checkin::getUserId, userId)
                .orderByAsc(Checkin::getCheckinDate)
                .list();
        if (all.isEmpty()) return 0;

        int longest = 1, cur = 1;
        for (int i = 1; i < all.size(); i++) {
            LocalDate prev = all.get(i - 1).getCheckinDate();
            LocalDate curr = all.get(i).getCheckinDate();
            if (curr.equals(prev.plusDays(1))) {
                cur++;
                longest = Math.max(longest, cur);
            } else if (!curr.equals(prev)) {
                cur = 1;
            }
        }
        return longest;
    }

    private String bitmapKey(Long userId, LocalDate date) {
        String month = date.format(DateTimeFormatter.ofPattern("yyyyMM"));
        return String.format(BITMAP_KEY, userId, month);
    }
}
