package com.chatweb.realtime;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Realtime Edge Service â€" unified websocket ingress and event delivery.
 *
 * Owns:
 * - Websocket connection lifecycle (auth, session management)
 * - Client subscription routing
 * - Event consumption from Kafka/Redis
 * - Message delivery to subscribed clients
 *
 * Deployed separately from business services for independent scaling.
 */
@SpringBootApplication
@EnableScheduling
@ComponentScan(basePackages = {"com.chatweb.common", "com.chatweb.realtime"})
public class RealtimeEdgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(RealtimeEdgeApplication.class, args);
    }
}
