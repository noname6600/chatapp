package com.chatweb.gateway;

import com.chatweb.common.web.cors.CorsProperties;
import com.chatweb.gateway.health.GatewayReadinessProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({GatewayReadinessProperties.class, CorsProperties.class})
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
