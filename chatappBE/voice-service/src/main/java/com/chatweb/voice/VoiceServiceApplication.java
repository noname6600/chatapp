package com.chatweb.voice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

@SpringBootApplication
@EnableFeignClients
@ComponentScan(
        basePackages = {"com.chatweb.common", "com.chatweb.voice"},
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.REGEX, pattern = ".*TestApplication.*")
        }
)
public class VoiceServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(VoiceServiceApplication.class, args);
    }
}
