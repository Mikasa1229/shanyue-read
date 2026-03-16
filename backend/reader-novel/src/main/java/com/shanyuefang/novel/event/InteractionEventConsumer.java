package com.shanyuefang.novel.event;

import com.rabbitmq.client.Channel;
import com.shanyuefang.novel.config.RabbitMQConfig;
import com.shanyuefang.novel.service.NovelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class InteractionEventConsumer {

    private final NovelService novelService;
    private final RedisTemplate<String, Object> redisTemplate;

    /** 互动事件幂等 key，TTL 24h */
    private static final String IDEM_KEY = "novel:mq:idem:%s";

    @RabbitListener(queues = RabbitMQConfig.NOVEL_INTERACTION_QUEUE)
    public void onInteractionEvent(InteractionEvent event, Message message, Channel channel)
            throws IOException {
        long tag = message.getMessageProperties().getDeliveryTag();
        String messageId = message.getMessageProperties().getMessageId();

        try {
            // 仅处理小说维度的互动（targetType = 1）
            if (event.getTargetType() == null || event.getTargetType() != 1) {
                channel.basicAck(tag, false);
                return;
            }

            // 幂等去重
            String idemKey = String.format(IDEM_KEY, messageId);
            Boolean absent = redisTemplate.opsForValue()
                    .setIfAbsent(idemKey, 1, 24, TimeUnit.HOURS);
            if (!Boolean.TRUE.equals(absent)) {
                log.debug("互动事件重复消费，已跳过: messageId={}", messageId);
                channel.basicAck(tag, false);
                return;
            }

            int delta = isPositive(event.getEventType()) ? 1 : -1;

            if (event.getAction() == 1) {
                novelService.updateLikeCount(event.getTargetId(), delta);
            } else if (event.getAction() == 2) {
                novelService.updateFavoriteCount(event.getTargetId(), delta);
            }

            log.info("处理互动事件: novelId={}, action={}, eventType={}",
                    event.getTargetId(), event.getAction(), event.getEventType());
            channel.basicAck(tag, false);

        } catch (Exception e) {
            log.error("处理互动事件失败: messageId={}", messageId, e);
            // 重试次数耗尽后进死信队列
            channel.basicNack(tag, false, false);
        }
    }

    private boolean isPositive(String eventType) {
        return "LIKED".equals(eventType) || "FAVORITED".equals(eventType);
    }
}
