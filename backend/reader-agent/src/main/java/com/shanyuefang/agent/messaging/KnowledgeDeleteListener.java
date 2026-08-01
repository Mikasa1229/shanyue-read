package com.shanyuefang.agent.messaging;

import com.shanyuefang.agent.config.KnowledgeMessagingConfig;
import com.shanyuefang.agent.service.KnowledgeService;
import com.shanyuefang.agent.service.KnowledgeIndexJobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.core.Message;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Deletes all Agent projections once the last source mapping for a work has been removed. */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeDeleteListener {
    private final KnowledgeService knowledgeService;
    private final KnowledgeIndexJobService indexJobService;

    @RabbitListener(queues = KnowledgeMessagingConfig.DELETE_QUEUE, containerFactory = "agentDeleteRabbitListenerContainerFactory")
    public void onMessage(Message message) {
        Map<String, Object> payload;
        try {
            payload = new com.fasterxml.jackson.databind.ObjectMapper().readValue(message.getBody(), Map.class);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid knowledge delete event payload", exception);
        }
        delete(payload);
    }

    /** Kept as a small adapter for already decoded events. */
    public void delete(Map<String, Object> payload) {
        Object value = payload.get("canonicalBookId");
        if (value == null) return;
        long canonicalBookId = Long.parseLong(String.valueOf(value));
        var job = indexJobService.beginDelete(canonicalBookId);
        if ("COMPLETED".equals(job.getStatus())) return;
        try {
            knowledgeService.deleteBookKnowledge(canonicalBookId);
            indexJobService.complete(job.getId());
            log.info("Removed Agent knowledge projections: bookId={}", canonicalBookId);
        } catch (Exception exception) {
            indexJobService.fail(job.getId(), exception);
            throw exception;
        }
    }
}
