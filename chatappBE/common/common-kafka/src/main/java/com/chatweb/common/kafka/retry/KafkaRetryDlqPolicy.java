package com.chatweb.common.kafka.retry;

import com.chatweb.common.kafka.topic.KafkaTopics;

/**
 * Default in-place retry plus dead-letter policy for shared Kafka modules.
 */
public class KafkaRetryDlqPolicy {

    public long backoffMs() {
        return 1000L;
    }

    public long retryAttempts() {
        return 2L;
    }

    public String deadLetterTopic(String sourceTopic) {
        return KafkaTopics.TOPIC_SYSTEM_DEAD_LETTER;
    }

    /**
     * Returns null to let Kafka choose a safe partition for DLQ destination topic.
     */
    public Integer deadLetterPartition(String sourceTopic, int sourcePartition) {
        return null;
    }
}
