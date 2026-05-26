package com.chatweb.common.redis.listener;

import com.chatweb.common.event.EventEnvelope;
import com.chatweb.common.redis.dispatcher.RedisEventDispatcher;
import com.chatweb.common.redis.observability.RedisPubSubObserver;
import com.chatweb.common.redis.serialization.RedisEventSerializer;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;

import java.nio.charset.StandardCharsets;

/**
 * Canonical Redis event listener.
 */
@RequiredArgsConstructor
public class RedisEventListener implements MessageListener {

    private final RedisEventSerializer serializer;
    private final RedisEventDispatcher dispatcher;
    private final RedisPubSubObserver logger;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);

        EventEnvelope<?> envelope;
        try {
            envelope = serializer.deserialize(payload);
        } catch (Exception ex) {
            logger.logDeserializeError(channel, payload, ex);
            return;
        }

        try {
            logger.logReceive(channel, envelope);
            dispatcher.dispatch(channel, envelope);
        } catch (Exception ex) {
            logger.logError(channel, envelope, ex);
        }
    }
}

