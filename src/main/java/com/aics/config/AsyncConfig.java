package com.aics.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class AsyncConfig {

    @Bean(name = "workerExecutor")
    public Executor workerExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(8);
        ex.setMaxPoolSize(32);
        ex.setQueueCapacity(200);
        ex.setThreadNamePrefix("aics-worker-");
        ex.setAwaitTerminationSeconds(30);
        ex.setWaitForTasksToCompleteOnShutdown(true);
        ex.initialize();
        return ex;
    }

    @Bean(name = "kbParserExecutor")
    public Executor kbParserExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(2);
        ex.setMaxPoolSize(8);
        ex.setQueueCapacity(50);
        ex.setThreadNamePrefix("aics-kb-parser-");
        ex.initialize();
        return ex;
    }
}
