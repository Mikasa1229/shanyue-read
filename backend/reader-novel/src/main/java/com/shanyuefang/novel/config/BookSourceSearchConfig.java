package com.shanyuefang.novel.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class BookSourceSearchConfig {

    @Bean("bookSourceSearchExecutor")
    public Executor bookSourceSearchExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // Remote sources are I/O-bound. Keep them away from the shared common pool and cap overload.
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(24);
        executor.setQueueCapacity(120);
        executor.setThreadNamePrefix("source-search-");
        executor.initialize();
        return executor;
    }
}
