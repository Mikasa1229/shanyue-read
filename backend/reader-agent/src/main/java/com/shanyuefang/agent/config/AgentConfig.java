package com.shanyuefang.agent.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AgentProperties.class)
public class AgentConfig {
}
