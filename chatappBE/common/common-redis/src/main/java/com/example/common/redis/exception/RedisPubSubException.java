package com.example.common.redis.exception;

public class RedisPubSubException extends RuntimeException {

    private final String channel;

    public RedisPubSubException(
            String channel,
            String message,
            Throwable cause
    ) {
        super(message, cause);
        this.channel = channel;
    }

    public RedisPubSubException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
        this.channel = null;
    }

    public String getChannel() {
        return channel;
    }
}

