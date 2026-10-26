package com.secureone.auth.config;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.boot.task.ThreadPoolTaskExecutorCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/** @Async pool sizes are in application.yml (spring.task.execution.pool). */
@Configuration
@EnableAsync
public class AsyncConfig {

    /** A full pool runs the task on the caller's thread instead of dropping it (webhooks, logout notices). */
    @Bean
    ThreadPoolTaskExecutorCustomizer callerRunsWhenFull() {
        return executor -> executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
    }
}
