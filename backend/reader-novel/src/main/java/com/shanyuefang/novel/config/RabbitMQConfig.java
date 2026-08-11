package com.shanyuefang.novel.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
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

    /** 小说服务消费打卡事件的队列 */
    public static final String NOVEL_CHECKIN_QUEUE = "q.novel.checkin";

    /** Re-fetches bounded chapter ranges when downstream evidence was intentionally repaired. */
    public static final String CONTENT_RECOVERY_QUEUE = "reader.novel.content-recovery";
    public static final String CONTENT_RECOVERY_DEAD_LETTER_QUEUE = "reader.novel.content-recovery.dlq";
    public static final String AGENT_EVENTS_EXCHANGE = "reader.agent.events";
    public static final String CONTENT_RECOVERY_ROUTING_KEY = "knowledge.chapter.recover";
    public static final String CONTENT_RECOVERY_DEAD_LETTER_ROUTING_KEY = "knowledge.chapter.recover.failed";

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
    public Binding deadBinding(@Qualifier("deadQueue") Queue deadQueue, DirectExchange deadExchange) {
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
    public Binding novelInteractionBinding(@Qualifier("novelInteractionQueue") Queue novelInteractionQueue, TopicExchange topicExchange) {
        return BindingBuilder.bind(novelInteractionQueue).to(topicExchange).with("interaction.#");
    }

    /** 绑定 checkin.# 到小说打卡队列，接收打卡事件 */
    @Bean
    public Queue novelCheckinQueue() {
        return QueueBuilder.durable(NOVEL_CHECKIN_QUEUE)
                .withArgument("x-dead-letter-exchange", DEAD_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", DEAD_QUEUE)
                .build();
    }

    @Bean
    public Binding novelCheckinBinding(@Qualifier("novelCheckinQueue") Queue novelCheckinQueue, TopicExchange topicExchange) {
        return BindingBuilder.bind(novelCheckinQueue).to(topicExchange).with("checkin.#");
    }

    @Bean
    public TopicExchange agentEventsExchange() {
        return ExchangeBuilder.topicExchange(AGENT_EVENTS_EXCHANGE).durable(true).build();
    }

    @Bean
    public Queue contentRecoveryQueue() {
        return QueueBuilder.durable(CONTENT_RECOVERY_QUEUE)
                .withArgument("x-dead-letter-exchange", AGENT_EVENTS_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", CONTENT_RECOVERY_DEAD_LETTER_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue contentRecoveryDeadLetterQueue() {
        return QueueBuilder.durable(CONTENT_RECOVERY_DEAD_LETTER_QUEUE).build();
    }

    @Bean
    public Binding contentRecoveryBinding(@Qualifier("contentRecoveryQueue") Queue contentRecoveryQueue,
                                          @Qualifier("agentEventsExchange") TopicExchange agentEventsExchange) {
        return BindingBuilder.bind(contentRecoveryQueue).to(agentEventsExchange).with(CONTENT_RECOVERY_ROUTING_KEY);
    }

    @Bean
    public Binding contentRecoveryDeadLetterBinding(@Qualifier("contentRecoveryDeadLetterQueue") Queue contentRecoveryDeadLetterQueue,
                                                    @Qualifier("agentEventsExchange") TopicExchange agentEventsExchange) {
        return BindingBuilder.bind(contentRecoveryDeadLetterQueue).to(agentEventsExchange).with(CONTENT_RECOVERY_DEAD_LETTER_ROUTING_KEY);
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
