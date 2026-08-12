package com.shanyuefang.agent.config;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "app.agent", name = "neo4j-enabled", havingValue = "true")
public class Neo4jConfig {
    @Bean(destroyMethod = "close")
    Driver knowledgeGraphDriver(AgentProperties properties) {
        return GraphDatabase.driver(properties.getNeo4jUri(), AuthTokens.basic(properties.getNeo4jUsername(), properties.getNeo4jPassword()));
    }
}
