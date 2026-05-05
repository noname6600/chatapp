package com.example.common.kafka.exception;

/**
 * Exception thrown when a Kafka messaging operation fails.
 * @since 2.2
 */
public class KafkaMessagingException extends RuntimeException {

    private final String topic;

    public KafkaMessagingException(String topic, String message, Throwable cause) {
        super(message, cause);
        this.topic = topic;
    }

    public String getTopic() {
        return topic;
    }
}
