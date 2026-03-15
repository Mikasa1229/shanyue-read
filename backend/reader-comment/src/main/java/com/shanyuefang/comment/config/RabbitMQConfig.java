package com.shanyuefang.comment.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 声明：Exchange、Queue、Binding
 * 点评服务只作为生产者，消费端在小说服务中声明
 */
@Slf4j
@Configuration
public class RabbitMQConfig {

    /** 业务 Topic Exchange */
    public static final String TOPIC_EXCHANGE = "reader.topic";

    /** 死信 Exchange */
    public static final String DEAD_EXCHANGE = "reader.dead";

    /** 死信队列 */
    public static final String DEAD_QUEUE = "q.dead.letter";

    @Bean
    public TopicExchange topicExchange() {
        return ExchangeBuilder.topicExchange(TOPIC_EXCHANGE).durable(true).build();
    }

    @Bean
    public DirectExchange deadExchange() {
        return ExchangeBuilder.directExchange(DEAD_EXCHANGE).durable(true).build();
    }

    @Bean
    public Queue deadQueue() {
        return QueueBuilder.durable(DEAD_QUEUE).build();
    }

    @Bean
    public Binding deadBinding(Queue deadQueue, DirectExchange deadExchange) {
        return BindingBuilder.bind(deadQueue).to(deadExchange).with(DEAD_QUEUE);
    }

    /** 消息序列化：Java Object → JSON */
    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /** 注入 JSON 消息转换器 + 生产者确认回调 */
    @Bean
    public RabbitTemplate rabbitTemplate(
            org.springframework.amqp.rabbit.connection.ConnectionFactory factory,
            Jackson2JsonMessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(factory);
        template.setMessageConverter(converter);

        // 生产者 Confirm：消息到达 Broker 回调
        template.setConfirmCallback((correlationData, ack, cause) -> {
            if (!ack) {
                log.error("消息发送到 Broker 失败: correlationId={}, cause={}",
                        correlationData != null ? correlationData.getId() : "null", cause);
                // TODO: 写入 DB 重试表，保证最终投递
            }
        });

        // 生产者 Returns：消息路由到 Queue 失败回调
        template.setReturnsCallback(returned ->
                log.error("消息路由失败: exchange={}, routingKey={}, replyCode={}",
                        returned.getExchange(), returned.getRoutingKey(), returned.getReplyCode())
        );

        return template;
    }
}
