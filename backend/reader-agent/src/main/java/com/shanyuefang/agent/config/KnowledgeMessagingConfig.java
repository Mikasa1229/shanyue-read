package com.shanyuefang.agent.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.RepublishMessageRecoverer;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KnowledgeMessagingConfig {
    public static final String EXCHANGE = "reader.agent.events";
    public static final String QUEUE = "reader.agent.knowledge.index";
    public static final String ROUTING_KEY = "knowledge.chapter.index";
    public static final String DELETE_QUEUE = "reader.agent.knowledge.delete";
    public static final String DELETE_ROUTING_KEY = "knowledge.book.delete";
    public static final String DEAD_LETTER_QUEUE = "reader.agent.knowledge.index.dlq";
    public static final String DEAD_LETTER_ROUTING_KEY = "knowledge.chapter.index.failed";
    public static final String DELETE_DEAD_LETTER_QUEUE = "reader.agent.knowledge.delete.dlq";
    public static final String DELETE_DEAD_LETTER_ROUTING_KEY = "knowledge.book.delete.failed";

    @Bean TopicExchange agentEventsExchange() { return new TopicExchange(EXCHANGE, true, false); }
    @Bean Queue knowledgeIndexQueue() { return new Queue(QUEUE, true); }
    @Bean Queue knowledgeIndexDeadLetterQueue() { return new Queue(DEAD_LETTER_QUEUE, true); }
    @Bean Queue knowledgeDeleteQueue() { return new Queue(DELETE_QUEUE, true); }
    @Bean Queue knowledgeDeleteDeadLetterQueue() { return new Queue(DELETE_DEAD_LETTER_QUEUE, true); }
    @Bean Binding knowledgeIndexBinding() { return BindingBuilder.bind(knowledgeIndexQueue()).to(agentEventsExchange()).with(ROUTING_KEY); }
    @Bean Binding knowledgeIndexDeadLetterBinding() { return BindingBuilder.bind(knowledgeIndexDeadLetterQueue()).to(agentEventsExchange()).with(DEAD_LETTER_ROUTING_KEY); }
    @Bean Binding knowledgeDeleteBinding() { return BindingBuilder.bind(knowledgeDeleteQueue()).to(agentEventsExchange()).with(DELETE_ROUTING_KEY); }
    @Bean Binding knowledgeDeleteDeadLetterBinding() { return BindingBuilder.bind(knowledgeDeleteDeadLetterQueue()).to(agentEventsExchange()).with(DELETE_DEAD_LETTER_ROUTING_KEY); }

    @Bean
    SimpleRabbitListenerContainerFactory agentRabbitListenerContainerFactory(ConnectionFactory connectionFactory,
                                                                              RabbitTemplate rabbitTemplate) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        // Chapter indexing is I/O-heavy (PostgreSQL plus optional vector projections); bounded
        // consumers keep a bulk import moving without allowing unbounded broker prefetch.
        factory.setConcurrentConsumers(4);
        factory.setMaxConcurrentConsumers(4);
        factory.setPrefetchCount(1);
        factory.setAdviceChain(RetryInterceptorBuilder.stateless()
                .maxAttempts(3)
                .backOffOptions(500, 2.0, 5_000)
                .recoverer(new RepublishMessageRecoverer(rabbitTemplate, EXCHANGE, DEAD_LETTER_ROUTING_KEY))
                .build());
        return factory;
    }

    @Bean
    SimpleRabbitListenerContainerFactory agentDeleteRabbitListenerContainerFactory(ConnectionFactory connectionFactory,
                                                                                    RabbitTemplate rabbitTemplate) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setAdviceChain(RetryInterceptorBuilder.stateless()
                .maxAttempts(3)
                .backOffOptions(500, 2.0, 5_000)
                .recoverer(new RepublishMessageRecoverer(rabbitTemplate, EXCHANGE, DELETE_DEAD_LETTER_ROUTING_KEY))
                .build());
        return factory;
    }
}
