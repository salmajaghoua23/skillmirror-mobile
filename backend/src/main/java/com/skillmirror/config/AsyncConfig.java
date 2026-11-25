package com.skillmirror.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean("aiExecutor")
    public Executor aiExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(1);      // un seul Ollama local : analyses en série
        ex.setMaxPoolSize(1);
        ex.setQueueCapacity(20);
        ex.setThreadNamePrefix("ai-job-");
        ex.initialize();
        return ex;
    }
}