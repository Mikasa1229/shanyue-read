package com.shanyuefang.novel.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class RabbitMQConfig {

    public static final String TOPIC_EXCHANGE = "reader.topic";
    public static final String DEAD_EXCHANGE  = "reader.dead";
    public static final String DEAD_QUEUE     = "q.dead.letter";

    /** 小说服务消费互动事件的队列 */
    public static final String NOVEL_INTERACTION_QUEUE = "q.novel.interaction";

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

    /** 绑定 interaction.# 到小说互动队列，接收点赞/收藏事件 */
    @Bean
    public Queue novelInteractionQueue() {
        return QueueBuilder.durable(NOVEL_INTERACTION_QUEUE)
                .withArgument("x-dead-letter-exchange", DEAD_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", DEAD_QUEUE)
                .build();
    }

    @Bean
    public Binding novelInteractionBinding(Queue novelInteractionQueue, TopicExchange topicExchange) {
        return BindingBuilder.bind(novelInteractionQueue).to(topicExchange).with("interaction.#");
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
        return template;
    }
}
