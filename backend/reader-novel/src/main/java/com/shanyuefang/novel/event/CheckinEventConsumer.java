package com.shanyuefang.novel.event;

import com.rabbitmq.client.Channel;
import com.shanyuefang.novel.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * 打卡事件消费者：
 * - 幂等消费（Redis 去重，TTL 24h）
 * - 若携带 novelId，则累计小说的打卡活跃度（ZSET ranking:novel_activity）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CheckinEventConsumer {

    private final RedisTemplate<String, Object> redisTemplate;

    /** 小说打卡活跃度 ZSET（可用于后续热度计算） */
    private static final String NOVEL_ACTIVITY_ZSET = "ranking:novel_activity";

    /** 幂等 key，TTL 24h */
    private static final String IDEM_KEY = "novel:checkin:idem:%s";

    @RabbitListener(queues = RabbitMQConfig.NOVEL_CHECKIN_QUEUE)
    public void onCheckinEvent(CheckinEvent event, Message message, Channel channel)
            throws IOException {
        long tag = message.getMessageProperties().getDeliveryTag();
        String messageId = message.getMessageProperties().getMessageId();

        try {
            // 幂等去重
            String idemKey = String.format(IDEM_KEY, messageId);
            Boolean absent = redisTemplate.opsForValue()
                    .setIfAbsent(idemKey, 1, 24, TimeUnit.HOURS);
            if (!Boolean.TRUE.equals(absent)) {
                log.debug("打卡事件重复消费，已跳过: messageId={}", messageId);
                channel.basicAck(tag, false);
                return;
            }

            // 若携带 novelId，累计小说打卡活跃度
            if (event.getNovelId() != null) {
                redisTemplate.opsForZSet().incrementScore(
                        NOVEL_ACTIVITY_ZSET,
                        String.valueOf(event.getNovelId()),
                        1
                );
                log.debug("小说打卡活跃度 +1: novelId={}, userId={}",
                        event.getNovelId(), event.getUserId());
            }

            log.info("处理打卡事件: userId={}, date={}, novelId={}",
                    event.getUserId(), event.getCheckinDate(), event.getNovelId());
            channel.basicAck(tag, false);

        } catch (Exception e) {
            log.error("处理打卡事件失败: messageId={}", messageId, e);
            channel.basicNack(tag, false, false);
        }
    }
}
