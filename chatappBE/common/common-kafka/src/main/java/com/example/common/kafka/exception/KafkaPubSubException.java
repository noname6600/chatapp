    package com.example.common.kafka.exception;

    public class KafkaPubSubException extends RuntimeException {

        private final String topic;

        public KafkaPubSubException(String topic, String message, Throwable cause) {
            super(message, cause);
            this.topic = topic;
        }

        public String getTopic() {
            return topic;
        }
    }
