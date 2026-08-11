package com.shanyuefang.novel.messaging;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Keeps an operator-triggered source refetch out of the request thread. */
@Component
@RequiredArgsConstructor
public class ContentRecoveryPublisher {
    public static final String EXCHANGE = "reader.agent.events";
    public static final String ROUTING_KEY = "knowledge.chapter.recover";

    private final RabbitTemplate rabbitTemplate;

    public void publish(long taskId) {
        rabbitTemplate.convertAndSend(EXCHANGE, ROUTING_KEY, Map.of("taskId", taskId));
    }
}
