package com.shanyuefang.agent.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;

class KnowledgeMessagingConfigTest {
    @Test
    void graphBuildMessagesUseJsonThatTheListenerCanRead() throws Exception {
        KnowledgeMessagingConfig config = new KnowledgeMessagingConfig();
        Jackson2JsonMessageConverter converter = config.agentMessageConverter();
        RabbitTemplate template = config.rabbitTemplate(mock(ConnectionFactory.class), converter);

        assertInstanceOf(Jackson2JsonMessageConverter.class, template.getMessageConverter());
        byte[] body = template.getMessageConverter().toMessage(Map.of("taskId", 71L), null).getBody();
        Map<String, Object> payload = new ObjectMapper().readValue(body,
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() { });
        assertEquals(71, ((Number) payload.get("taskId")).longValue());
    }
}
