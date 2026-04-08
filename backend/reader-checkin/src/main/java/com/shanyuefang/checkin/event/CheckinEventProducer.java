package com.shanyuefang.checkin.event;

import com.shanyuefang.checkin.config.CheckinRabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 打卡事件生产者：将打卡事件异步发布到 reader.topic
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CheckinEventProducer {

    private final RabbitTemplate checkinRabbitTemplate;

    /**
     * 发布打卡事件（fire-and-forget，不阻塞主流程）
     */
    public void publishCheckinCreated(CheckinEvent event) {
        String msgId = UUID.randomUUID().toString();
        CorrelationData correlationData = new CorrelationData(msgId);
        try {
            checkinRabbitTemplate.convertAndSend(
                    CheckinRabbitMQConfig.TOPIC_EXCHANGE,
                    CheckinRabbitMQConfig.ROUTING_KEY_CREATED,
                    event,
                    message -> {
                        message.getMessageProperties().setMessageId(msgId);
                        return message;
                    },
                    correlationData
            );
            log.debug("打卡事件已发布: msgId={}, userId={}, date={}",
                    msgId, event.getUserId(), event.getCheckinDate());
        } catch (Exception e) {
            // MQ 发送失败不影响主业务
            log.warn("打卡事件发布失败（不影响打卡结果）: userId={}, err={}",
                    event.getUserId(), e.getMessage());
        }
    }
}
