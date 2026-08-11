package com.shanyuefang.novel.messaging;

import com.shanyuefang.novel.config.RabbitMQConfig;
import com.shanyuefang.novel.service.ContentRecoveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class ContentRecoveryListener {
    private final ContentRecoveryService contentRecoveryService;

    @RabbitListener(queues = RabbitMQConfig.CONTENT_RECOVERY_QUEUE)
    public void recover(Map<String, Object> payload) {
        Object value = payload.get("taskId");
        if (!(value instanceof Number number)) throw new IllegalArgumentException("Invalid chapter recovery task payload");
        contentRecoveryService.recover(number.longValue());
    }
}
