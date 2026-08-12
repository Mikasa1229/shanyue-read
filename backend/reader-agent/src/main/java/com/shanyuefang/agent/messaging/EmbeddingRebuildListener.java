package com.shanyuefang.agent.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shanyuefang.agent.config.KnowledgeMessagingConfig;
import com.shanyuefang.agent.service.KnowledgeIndexJobService;
import com.shanyuefang.agent.service.KnowledgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Rebuilds vectors through a durable queue rather than an in-process future. */
@Component
@RequiredArgsConstructor
public class EmbeddingRebuildListener {
    private final ObjectMapper objectMapper;
    private final KnowledgeIndexJobService indexJobService;
    private final KnowledgeService knowledgeService;
    private final RabbitTemplate rabbitTemplate;

    @EventListener(ApplicationReadyEvent.class)
    public void recoverAndRepublish() {
        indexJobService.recoverInterruptedEmbeddingRebuilds();
        republishPending();
    }

    @Scheduled(fixedDelayString = "${AGENT_EMBEDDING_REBUILD_QUEUE_RECONCILE_MILLIS:30000}")
    public void republishPending() { indexJobService.pendingEmbeddingRebuilds().forEach(job -> publish(job.getId())); }

    @RabbitListener(queues = KnowledgeMessagingConfig.EMBEDDING_REBUILD_QUEUE,
            containerFactory = "embeddingRebuildRabbitListenerContainerFactory")
    public void onMessage(Message message) {
        try {
            Map<String, Object> payload = objectMapper.readValue(message.getBody(),
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() { });
            Object value = payload.get("jobId");
            if (value == null) throw new IllegalArgumentException("向量重建消息缺少任务编号");
            long jobId = Long.parseLong(String.valueOf(value));
            if (!indexJobService.claimEmbeddingRebuild(jobId)) return;
            var job = indexJobService.find(jobId);
            if (job == null || job.getCanonicalBookId() == null) return;
            try {
                knowledgeService.reembedBookEvidence(job.getCanonicalBookId());
                indexJobService.complete(jobId);
            } catch (Exception exception) {
                indexJobService.fail(jobId, exception);
                throw exception;
            }
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("向量重建消息无效", exception);
        }
    }

    private void publish(long jobId) {
        rabbitTemplate.convertAndSend(KnowledgeMessagingConfig.EXCHANGE,
                KnowledgeMessagingConfig.EMBEDDING_REBUILD_ROUTING_KEY, Map.of("jobId", jobId));
    }
}
