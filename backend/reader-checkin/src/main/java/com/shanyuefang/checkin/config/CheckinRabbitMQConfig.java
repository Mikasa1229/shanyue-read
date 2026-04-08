package com.shanyuefang.checkin.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 打卡服务 RabbitMQ 配置
 * 只声明 Exchange 和消息转换器；队列由消费方（reader-novel）声明
 */
@Configuration
public class CheckinRabbitMQConfig {

    public static final String TOPIC_EXCHANGE    = "reader.topic";
    public static final String ROUTING_KEY_CREATED = "checkin.created";

    @Bean
    public TopicExchange checkinTopicExchange() {
        return ExchangeBuilder.topicExchange(TOPIC_EXCHANGE).durable(true).build();
    }

    @Bean
    public Jackson2JsonMessageConverter checkinMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate checkinRabbitTemplate(
            org.springframework.amqp.rabbit.connection.ConnectionFactory factory,
            Jackson2JsonMessageConverter checkinMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(factory);
        template.setMessageConverter(checkinMessageConverter);
        // 生产者确认：发送失败时记录日志
        template.setConfirmCallback((correlationData, ack, cause) -> {
            if (!ack) {
                String msgId = correlationData != null ? correlationData.getId() : "unknown";
                org.slf4j.LoggerFactory.getLogger(CheckinRabbitMQConfig.class)
                        .warn("打卡事件发送失败: msgId={}, cause={}", msgId, cause);
            }
        });
        return template;
    }
}
