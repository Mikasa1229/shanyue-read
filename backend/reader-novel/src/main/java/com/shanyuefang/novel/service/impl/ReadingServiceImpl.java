package com.shanyuefang.novel.service.impl;

import com.shanyuefang.common.result.R;
import com.shanyuefang.novel.domain.vo.RankingVO;
import com.shanyuefang.novel.domain.dto.ReadingHeartbeatDTO;
import com.shanyuefang.novel.domain.dto.StartReadingSessionDTO;
import com.shanyuefang.novel.domain.vo.ReadingSessionVO;
import com.shanyuefang.novel.feign.UserFeignClient;
import com.shanyuefang.novel.feign.vo.UserSimpleVO;
import com.shanyuefang.novel.service.ReadingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
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
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReadingServiceImpl implements ReadingService {

    /** Redis ZSET key 前缀：成员=userId，分值=本周累计阅读秒数。完整 key 如 ranking:reading_time:2026-W15 */
    private static final String RANKING_KEY_PREFIX = "ranking:reading_time:";
    private static final String SESSION_PREFIX = "reading:session:";
    private static final int REWARD_SECONDS = 30 * 60;
    private static final int MAX_DAILY_QUALIFIED_SECONDS = 4 * 60 * 60;
    private static final DefaultRedisScript<Long> CLAIM_DAILY_SECONDS = new DefaultRedisScript<>("""
            local current = tonumber(redis.call('GET', KEYS[1]) or '0')
            local requested = tonumber(ARGV[1])
            local maximum = tonumber(ARGV[2])
            local accepted = math.max(0, math.min(requested, maximum - current))
            if accepted > 0 then
              redis.call('INCRBY', KEYS[1], accepted)
              if current == 0 then redis.call('EXPIRE', KEYS[1], ARGV[3]) end
            end
            return accepted
            """, Long.class);

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

    private void recordVerifiedSeconds(long userId, int seconds) {
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

    @Override
    public ReadingSessionVO startSession(long userId, StartReadingSessionDTO dto) {
        String token = UUID.randomUUID().toString().replace("-", "");
        long now = System.currentTimeMillis();
        String key = SESSION_PREFIX + token;
        stringRedisTemplate.opsForHash().put(key, "userId", String.valueOf(userId));
        stringRedisTemplate.opsForHash().put(key, "bookUrl", dto.getBookUrl());
        stringRedisTemplate.opsForHash().put(key, "lastHeartbeat", String.valueOf(now));
        stringRedisTemplate.opsForHash().put(key, "qualifiedSeconds", "0");
        stringRedisTemplate.expire(key, Duration.ofHours(8));
        return new ReadingSessionVO(token, 0, false);
    }

    @Override
    public ReadingSessionVO heartbeat(long userId, ReadingHeartbeatDTO dto) {
        String key = SESSION_PREFIX + dto.getSessionToken();
        Object owner = stringRedisTemplate.opsForHash().get(key, "userId");
        if (owner == null || !String.valueOf(userId).equals(String.valueOf(owner))) {
            throw new com.shanyuefang.common.exception.BusinessException(com.shanyuefang.common.result.ResultCode.FORBIDDEN, "Invalid reading session");
        }
        long now = System.currentTimeMillis();
        long previous = Long.parseLong(String.valueOf(stringRedisTemplate.opsForHash().get(key, "lastHeartbeat")));
        // A hidden tab earns nothing; visible intervals are server-clamped to resist client-side time spoofing.
        long earned = dto.isPageVisible() && now - previous >= 15_000L ? Math.min(90L, (now - previous) / 1000L) : 0L;
        String dayKey = "reading:qualified:" + java.time.LocalDate.now(BEIJING) + ":" + userId;
        Long accepted = earned == 0 ? 0L : stringRedisTemplate.execute(CLAIM_DAILY_SECONDS, List.of(dayKey),
                String.valueOf(earned), String.valueOf(MAX_DAILY_QUALIFIED_SECONDS), String.valueOf(Duration.ofDays(2).toSeconds()));
        earned = accepted == null ? 0L : accepted;
        long current = Long.parseLong(String.valueOf(stringRedisTemplate.opsForHash().get(key, "qualifiedSeconds"))) + earned;
        stringRedisTemplate.opsForHash().put(key, "qualifiedSeconds", String.valueOf(current));
        stringRedisTemplate.opsForHash().put(key, "lastHeartbeat", String.valueOf(now));
        if (earned > 0) recordVerifiedSeconds(userId, (int) earned);
        boolean rewarded = false;
        if (earned > 0) {
            try {
                userFeignClient.recordVerifiedReading(userId, Math.toIntExact(earned));
                rewarded = current >= REWARD_SECONDS;
            } catch (Exception e) {
                log.warn("Unable to report verified reading progress: userId={}", userId, e);
            }
        }
        return new ReadingSessionVO(dto.getSessionToken(), current, rewarded);
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
