package com.example.chat.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Configuration
public class PipelineConfig {

    @Bean
    public Executor pipelineExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
