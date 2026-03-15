package com.shanyuefang.interaction.event;

import com.shanyuefang.interaction.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class InteractionEventProducer {

    private final RabbitTemplate rabbitTemplate;

    public void sendLikeEvent(Long targetId, int targetType, Long userId, boolean liked) {
        String eventType = liked ? "LIKED" : "UNLIKED";
        send(RabbitMQConfig.LIKE_KEY_PREFIX + targetId,
                new InteractionEvent(targetId, targetType, 1, eventType, userId));
    }

    public void sendFavoriteEvent(Long targetId, Long userId, boolean favorited) {
        String eventType = favorited ? "FAVORITED" : "UNFAVORITED";
        send(RabbitMQConfig.FAVORITE_KEY_PREFIX + targetId,
                new InteractionEvent(targetId, 1, 2, eventType, userId));
    }

    private void send(String routingKey, InteractionEvent event) {
        String messageId = UUID.randomUUID().toString();
        CorrelationData cd = new CorrelationData(messageId);
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.TOPIC_EXCHANGE, routingKey, event,
                msg -> {
                    msg.getMessageProperties().setMessageId(messageId);
                    return msg;
                },
                cd
        );
        log.debug("发送互动事件: routingKey={}, eventType={}, messageId={}",
                routingKey, event.getEventType(), messageId);
    }
}
