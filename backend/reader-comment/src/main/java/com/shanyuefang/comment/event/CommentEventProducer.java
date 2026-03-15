package com.shanyuefang.comment.event;

import com.shanyuefang.comment.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 点评事件生产者
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CommentEventProducer {

    private final RabbitTemplate rabbitTemplate;

    /**
     * 点评创建事件（小说服务消费后 comment_count +1）
     */
    public void sendCommentCreated(Long novelId, Long commentId) {
        send(novelId, commentId, "CREATED");
    }

    /**
     * 点评删除事件（小说服务消费后 comment_count -1）
     */
    public void sendCommentDeleted(Long novelId, Long commentId) {
        send(novelId, commentId, "DELETED");
    }

    private void send(Long novelId, Long commentId, String action) {
        CommentEvent event = new CommentEvent(commentId, novelId, action);
        String routingKey = "comment." + action.toLowerCase() + "." + novelId;
        // messageId 用于消费端幂等去重
        String messageId = UUID.randomUUID().toString();
        CorrelationData correlationData = new CorrelationData(messageId);

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.TOPIC_EXCHANGE,
                routingKey,
                event,
                message -> {
                    message.getMessageProperties().setMessageId(messageId);
                    return message;
                },
                correlationData
        );
        log.debug("发送点评事件: action={}, novelId={}, commentId={}, messageId={}",
                action, novelId, commentId, messageId);
    }
}
