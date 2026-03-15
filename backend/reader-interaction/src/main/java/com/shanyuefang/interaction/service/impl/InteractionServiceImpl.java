package com.shanyuefang.interaction.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shanyuefang.common.exception.BusinessException;
import com.shanyuefang.common.result.ResultCode;
import com.shanyuefang.common.util.SnowflakeIdUtil;
import com.shanyuefang.interaction.domain.dto.InteractionStatusDTO;
import com.shanyuefang.interaction.domain.entity.Interaction;
import com.shanyuefang.interaction.domain.enums.ActionType;
import com.shanyuefang.interaction.domain.enums.TargetType;
import com.shanyuefang.interaction.domain.vo.InteractionResultVO;
import com.shanyuefang.interaction.event.InteractionEventProducer;
import com.shanyuefang.interaction.mapper.InteractionMapper;
import com.shanyuefang.interaction.service.InteractionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class InteractionServiceImpl extends ServiceImpl<InteractionMapper, Interaction>
        implements InteractionService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final InteractionEventProducer eventProducer;

    /** Redis key: interaction:status:{userId}:{targetType}:{action}:{targetId} → "1"/"0" */
    private static final String STATUS_KEY = "interaction:status:%d:%d:%d:%d";
    private static final long   STATUS_TTL = 60L; // 分钟

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InteractionResultVO toggleLike(Long userId, Long targetId, Integer targetType) {
        validateTargetType(targetType);
        return toggle(userId, targetId, targetType, ActionType.LIKE.getCode());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InteractionResultVO toggleFavorite(Long userId, Long targetId) {
        // 收藏仅支持小说
        return toggle(userId, targetId, TargetType.NOVEL.getCode(), ActionType.FAVORITE.getCode());
    }

    @Override
    public Map<Long, Boolean> batchQueryStatus(Long userId, InteractionStatusDTO dto) {
        List<Long> targetIds = dto.getTargetIds();
        int targetType = dto.getTargetType();
        int action = dto.getAction();

        Map<Long, Boolean> result = new HashMap<>(targetIds.size());
        for (Long targetId : targetIds) {
            result.put(targetId, isActive(userId, targetId, targetType, action));
        }
        return result;
    }

    // ─────────── 核心 toggle 逻辑 ───────────

    /**
     * 通用切换逻辑：已存在则删除（取消），不存在则插入（添加）
     * 使用唯一键 (user_id, target_id, target_type, action) 保证幂等
     */
    private InteractionResultVO toggle(Long userId, Long targetId, int targetType, int action) {
        // 1. 查是否已存在（先查 Redis，再查 DB）
        boolean currentlyActive = isActive(userId, targetId, targetType, action);

        if (currentlyActive) {
            // 取消：物理删除该条记录
            lambdaUpdate()
                    .eq(Interaction::getUserId, userId)
                    .eq(Interaction::getTargetId, targetId)
                    .eq(Interaction::getTargetType, targetType)
                    .eq(Interaction::getAction, action)
                    .remove();

            // 删除 Redis 状态缓存
            redisTemplate.delete(statusKey(userId, targetId, targetType, action));

            log.info("取消互动: userId={}, targetId={}, type={}, action={}", userId, targetId, targetType, action);
        } else {
            // 新增：利用 DB 唯一键兜底幂等（并发情况下 DB 会抛 unique violation）
            Interaction record = new Interaction();
            record.setId(SnowflakeIdUtil.next());
            record.setUserId(userId);
            record.setTargetId(targetId);
            record.setTargetType(targetType);
            record.setAction(action);
            record.setCreatedAt(LocalDateTime.now());

            try {
                save(record);
            } catch (Exception e) {
                // 唯一键冲突：说明已存在，幂等处理
                log.warn("互动记录已存在（唯一键冲突），忽略: userId={}, targetId={}", userId, targetId);
                int count = baseMapper.countByTarget(targetId, targetType, action);
                return new InteractionResultVO(true, count);
            }

            // 写入 Redis 状态缓存
            redisTemplate.opsForValue().set(statusKey(userId, targetId, targetType, action),
                    "1", STATUS_TTL, TimeUnit.MINUTES);

            log.info("新增互动: userId={}, targetId={}, type={}, action={}", userId, targetId, targetType, action);
        }

        // 2. 异步发送 MQ 事件，由目标服务更新计数
        boolean nowActive = !currentlyActive;
        if (action == ActionType.LIKE.getCode()) {
            eventProducer.sendLikeEvent(targetId, targetType, userId, nowActive);
        } else {
            eventProducer.sendFavoriteEvent(targetId, userId, nowActive);
        }

        // 3. 返回最新计数（直接查 DB，保证准确；热点场景可改为 Redis 计数）
        int count = baseMapper.countByTarget(targetId, targetType, action);
        return new InteractionResultVO(nowActive, count);
    }

    /** 查询互动状态（先查 Redis，再查 DB，回填缓存）*/
    private boolean isActive(Long userId, Long targetId, int targetType, int action) {
        String key = statusKey(userId, targetId, targetType, action);
        Object cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            return "1".equals(cached.toString());
        }
        // 查 DB
        boolean exists = lambdaQuery()
                .eq(Interaction::getUserId, userId)
                .eq(Interaction::getTargetId, targetId)
                .eq(Interaction::getTargetType, targetType)
                .eq(Interaction::getAction, action)
                .exists();
        // 回填缓存（不管是否存在都缓存，防止缓存穿透）
        redisTemplate.opsForValue().set(key, exists ? "1" : "0", STATUS_TTL, TimeUnit.MINUTES);
        return exists;
    }

    private String statusKey(Long userId, Long targetId, int targetType, int action) {
        return String.format(STATUS_KEY, userId, targetType, action, targetId);
    }

    private void validateTargetType(Integer targetType) {
        if (targetType != TargetType.NOVEL.getCode() && targetType != TargetType.COMMENT.getCode()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "目标类型不合法");
        }
    }
}
