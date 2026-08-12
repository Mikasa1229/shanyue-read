package com.shanyuefang.agent.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shanyuefang.agent.config.KnowledgeMessagingConfig;
import com.shanyuefang.agent.service.BookKnowledgeBuildService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Executes a persisted graph-build task only after RabbitMQ durably delivers its task id. */
@Component
@RequiredArgsConstructor
public class BookKnowledgeBuildListener {
    private final ObjectMapper objectMapper;
    private final BookKnowledgeBuildService bookKnowledgeBuildService;

    @RabbitListener(queues = KnowledgeMessagingConfig.GRAPH_BUILD_QUEUE,
            containerFactory = "graphBuildRabbitListenerContainerFactory")
    public void onMessage(Message message) {
        try {
            Map<String, Object> payload = objectMapper.readValue(message.getBody(),
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() { });
            Object value = payload.get("taskId");
            if (value == null) throw new IllegalArgumentException("知识图谱构建消息缺少任务编号");
            bookKnowledgeBuildService.consumeQueuedTask(Long.parseLong(String.valueOf(value)));
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("知识图谱构建消息无效", exception);
        }
    }
}
