package com.shanyuefang.novel.service.impl;

import com.shanyuefang.common.result.R;
import com.shanyuefang.novel.domain.vo.RankingVO;
import com.shanyuefang.novel.feign.UserFeignClient;
import com.shanyuefang.novel.feign.vo.UserSimpleVO;
import com.shanyuefang.novel.service.ReadingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReadingServiceImpl implements ReadingService {

    /** Redis ZSET key 前缀：成员=userId，分值=本周累计阅读秒数。完整 key 如 ranking:reading_time:2026-W15 */
    private static final String RANKING_KEY_PREFIX = "ranking:reading_time:";

    private final StringRedisTemplate stringRedisTemplate;
    private final UserFeignClient userFeignClient;

    private static final ZoneId BEIJING = ZoneId.of("Asia/Shanghai");

    /** 返回当前 ISO 周的 ZSET key（北京时间），格式：ranking:reading_time:yyyy-Www */
    private String currentWeekKey() {
        ZonedDateTime now = ZonedDateTime.now(BEIJING);
        int year = now.get(IsoFields.WEEK_BASED_YEAR);
        int week = now.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        return String.format("%s%d-W%02d", RANKING_KEY_PREFIX, year, week);
    }

    /** 计算距下周一北京时间 00:00:00 的秒数（用于设置 TTL） */
    private long secondsUntilNextMonday() {
        ZonedDateTime now = ZonedDateTime.now(BEIJING);
        ZonedDateTime nextMonday = now.toLocalDate()
                .with(TemporalAdjusters.next(DayOfWeek.MONDAY))
                .atStartOfDay(BEIJING);
        return Duration.between(now, nextMonday).getSeconds();
    }

    @Override
    public void record(long userId, int seconds) {
        String key = currentWeekKey();
        String member = String.valueOf(userId);
        stringRedisTemplate.opsForZSet().incrementScore(key, member, seconds);

        // 若 key 尚未设置过期时间（新建的本周 key），设置 TTL 到下周一 00:00
        Long ttl = stringRedisTemplate.getExpire(key, TimeUnit.SECONDS);
        if (ttl != null && ttl < 0) {
            long expireSeconds = secondsUntilNextMonday();
            if (expireSeconds > 0) {
                stringRedisTemplate.expire(key, expireSeconds, TimeUnit.SECONDS);
            }
        }
        log.debug("记录阅读时长: userId={}, seconds={}, key={}", userId, seconds, key);
    }

    @Override
    public List<RankingVO> getRanking(int top) {
        int limit = Math.min(top, 100);
        String key = currentWeekKey();
        Set<ZSetOperations.TypedTuple<String>> tuples =
                stringRedisTemplate.opsForZSet().reverseRangeWithScores(key, 0, limit - 1);

        if (tuples == null || tuples.isEmpty()) {
            return List.of();
        }

        // 收集 userId 列表，批量查询用户信息
        List<Long> userIds = tuples.stream()
                .map(t -> Long.parseLong(t.getValue()))
                .collect(Collectors.toList());

        Map<Long, UserSimpleVO> userMap;
        try {
            R<Map<Long, UserSimpleVO>> resp = userFeignClient.batchGetUsers(userIds);
            userMap = (resp != null && resp.getData() != null) ? resp.getData() : Map.of();
        } catch (Exception e) {
            log.warn("批量查询用户信息失败，排行榜将不含用户信息: {}", e.getMessage());
            userMap = Map.of();
        }

        List<RankingVO> result = new ArrayList<>();
        int rank = 1;
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            long userId2 = Long.parseLong(tuple.getValue());
            Double scoreObj = tuple.getScore();
            long totalSeconds = scoreObj != null ? scoreObj.longValue() : 0L;

            RankingVO vo = new RankingVO();
            vo.setRank(rank++);
            vo.setUserId(userId2);
            vo.setTotalSeconds(totalSeconds);
            vo.setReadingTime(formatSeconds(totalSeconds));

            UserSimpleVO user = userMap.get(userId2);
            if (user != null) {
                vo.setNickname(user.getNickname());
                vo.setAvatar(user.getAvatar());
            } else {
                vo.setNickname("用户" + userId2);
            }

            result.add(vo);
        }
        return result;
    }

    /** 将秒数格式化为可读字符串，如 "2小时35分钟" */
    private String formatSeconds(long seconds) {
        if (seconds < 60) return seconds + "秒";
        long minutes = seconds / 60;
        if (minutes < 60) return minutes + "分钟";
        long hours = minutes / 60;
        long remainMinutes = minutes % 60;
        if (remainMinutes == 0) return hours + "小时";
        return hours + "小时" + remainMinutes + "分钟";
    }
}
