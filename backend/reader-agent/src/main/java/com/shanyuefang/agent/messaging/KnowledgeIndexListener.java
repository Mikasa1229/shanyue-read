package com.shanyuefang.agent.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shanyuefang.common.result.R;
import com.shanyuefang.agent.config.KnowledgeMessagingConfig;
import com.shanyuefang.agent.domain.dto.IndexChapterDTO;
import com.shanyuefang.agent.service.KnowledgeService;
import com.shanyuefang.agent.service.KnowledgeIndexJobService;
import com.shanyuefang.agent.feign.ContentVersionFeignClient;
import com.shanyuefang.agent.config.AgentProperties;
import org.springframework.amqp.core.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeIndexListener {
    private final ObjectMapper objectMapper;
    private final KnowledgeService knowledgeService;
    private final KnowledgeIndexJobService indexJobService;
    private final ContentVersionFeignClient contentVersionClient;
    private final AgentProperties agentProperties;

    @RabbitListener(queues = KnowledgeMessagingConfig.QUEUE, containerFactory = "agentRabbitListenerContainerFactory")
    public void onMessage(Message message) {
        Map<String, Object> payload;
        try {
            payload = objectMapper.readValue(message.getBody(), new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() { });
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid knowledge index event payload", exception);
        }
        index(payload);
    }

    /** Kept as a small unit-test and internal adapter for already decoded events. */
    public void index(Map<String, Object> payload) {
        IndexChapterDTO dto = objectMapper.convertValue(payload, IndexChapterDTO.class);
        var job = indexJobService.begin(dto);
        if ("COMPLETED".equals(job.getStatus())) return;
        try {
            knowledgeService.indexChapter(dto);
            indexJobService.complete(job.getId());
            updateSourceVersion(dto, "READY");
            log.debug("Indexed Agent knowledge: bookId={}, chapter={}", dto.getCanonicalBookId(), dto.getChapterIndex());
        } catch (Exception exception) {
            indexJobService.fail(job.getId(), exception);
            updateSourceVersion(dto, "FAILED");
            throw exception;
        }
    }

    private void updateSourceVersion(IndexChapterDTO dto, String status) {
        try {
            R<Void> response = contentVersionClient.updateStatus(agentProperties.getInternalToken(), Map.of("canonicalBookId", dto.getCanonicalBookId(),
                    "chapterIndex", dto.getChapterIndex(), "contentHash", dto.getContentVersion(), "indexStatus", status));
            if (response == null || response.getCode() != 200) {
                log.warn("Chapter index status was not accepted by source: bookId={}, chapter={}, status={}, code={}",
                        dto.getCanonicalBookId(), dto.getChapterIndex(), status, response == null ? "no-response" : response.getCode());
            }
        } catch (Exception exception) {
            // Source-side observability must not turn a completed durable Agent index into a retry loop.
            log.warn("Could not update chapter index status: bookId={}, chapter={}, status={}", dto.getCanonicalBookId(), dto.getChapterIndex(), status);
        }
    }
}
