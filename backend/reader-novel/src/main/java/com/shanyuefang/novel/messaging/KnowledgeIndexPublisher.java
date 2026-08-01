package com.shanyuefang.novel.messaging;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Keep source fetching responsive; the Agent service indexes chapter text asynchronously. */
@Component
@RequiredArgsConstructor
public class KnowledgeIndexPublisher {
    public static final String EXCHANGE = "reader.agent.events";
    public static final String ROUTING_KEY = "knowledge.chapter.index";
    public static final String DELETE_ROUTING_KEY = "knowledge.book.delete";
    private final RabbitTemplate rabbitTemplate;

    public void publish(long canonicalBookId, int chapterIndex, String content, String contentVersion) {
        rabbitTemplate.convertAndSend(EXCHANGE, ROUTING_KEY, Map.of(
                "canonicalBookId", canonicalBookId,
                "chapterIndex", chapterIndex,
                "content", content,
                "contentVersion", contentVersion
        ));
    }

    public void publishDelete(long canonicalBookId) {
        rabbitTemplate.convertAndSend(EXCHANGE, DELETE_ROUTING_KEY, Map.of("canonicalBookId", canonicalBookId));
    }
}
