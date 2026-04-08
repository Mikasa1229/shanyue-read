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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReadingServiceImpl implements ReadingService {

    /** Redis ZSET key：成员=userId，分值=累计阅读秒数 */
    private static final String RANKING_KEY = "ranking:reading_time";

    private final StringRedisTemplate stringRedisTemplate;
    private final UserFeignClient userFeignClient;

    @Override
    public void record(long userId, int seconds) {
        String member = String.valueOf(userId);
        stringRedisTemplate.opsForZSet().incrementScore(RANKING_KEY, member, seconds);
        log.debug("记录阅读时长: userId={}, seconds={}", userId, seconds);
    }

    @Override
    public List<RankingVO> getRanking(int top) {
        int limit = Math.min(top, 100);
        Set<ZSetOperations.TypedTuple<String>> tuples =
                stringRedisTemplate.opsForZSet().reverseRangeWithScores(RANKING_KEY, 0, limit - 1);

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
