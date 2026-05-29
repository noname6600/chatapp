package com.chatweb.common.kafka.retry;

import com.chatweb.common.kafka.topic.KafkaTopics;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configurable retry + dead-letter policy for all Kafka consumers.
 *
 * Override per-service via application.yaml:
 *   common.kafka.retry.max-attempts: 5
 *   common.kafka.retry.initial-interval-ms: 500
 *   common.kafka.retry.max-interval-ms: 60000
 *   common.kafka.retry.multiplier: 2.0
 */
@ConfigurationProperties(prefix = "common.kafka.retry")
public class KafkaRetryDlqPolicy {

    private int maxAttempts = 3;
    private long initialIntervalMs = 1000L;
    private long maxIntervalMs = 30000L;
    private double multiplier = 2.0;

    public int getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }

    public long getInitialIntervalMs() { return initialIntervalMs; }
    public void setInitialIntervalMs(long initialIntervalMs) { this.initialIntervalMs = initialIntervalMs; }

    public long getMaxIntervalMs() { return maxIntervalMs; }
    public void setMaxIntervalMs(long maxIntervalMs) { this.maxIntervalMs = maxIntervalMs; }

    public double getMultiplier() { return multiplier; }
    public void setMultiplier(double multiplier) { this.multiplier = multiplier; }

    public String deadLetterTopic(String sourceTopic) {
        return KafkaTopics.TOPIC_SYSTEM_DEAD_LETTER;
    }

    public Integer deadLetterPartition(String sourceTopic, int sourcePartition) {
        return null;
    }
}
