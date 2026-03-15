package com.shanyuefang.interaction.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class RabbitMQConfig {

    public static final String TOPIC_EXCHANGE   = "reader.topic";
    public static final String DEAD_EXCHANGE    = "reader.dead";
    public static final String DEAD_QUEUE       = "q.dead.letter";

    // 路由 Key 模板
    public static final String LIKE_KEY_PREFIX      = "interaction.like.";
    public static final String FAVORITE_KEY_PREFIX  = "interaction.favorite.";

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

    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(
            org.springframework.amqp.rabbit.connection.ConnectionFactory factory,
            Jackson2JsonMessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(factory);
        template.setMessageConverter(converter);

        template.setConfirmCallback((correlationData, ack, cause) -> {
            if (!ack) {
                log.error("互动事件投递失败: id={}, cause={}",
                        correlationData != null ? correlationData.getId() : "-", cause);
            }
        });
        template.setReturnsCallback(r ->
                log.error("互动事件路由失败: routingKey={}", r.getRoutingKey()));

        return template;
    }
}
