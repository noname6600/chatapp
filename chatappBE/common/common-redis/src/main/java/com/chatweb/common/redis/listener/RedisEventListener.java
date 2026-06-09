package com.chatweb.common.redis.listener;

import com.chatweb.common.event.EventEnvelope;
import com.chatweb.common.redis.dispatcher.RedisEventDispatcher;
import com.chatweb.common.redis.observability.RedisPubSubObserver;
import com.chatweb.common.redis.serialization.RedisEventSerializer;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Canonical Redis event listener.
 */
@RequiredArgsConstructor
public class RedisEventListener implements MessageListener {

    private static final TextMapGetter<Map<String, String>> MAP_GETTER = new TextMapGetter<>() {
        @Override
        public Iterable<String> keys(Map<String, String> carrier) {
            return carrier.keySet();
        }

        @Override
        public String get(Map<String, String> carrier, String key) {
            return carrier.get(key);
        }
    };

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

        // Restore the publisher's trace context so this dispatch is a child span
        // of the original request that published the event.
        String traceparent = envelope.metadata().getTraceparent();
        Context parent = traceparent != null
                ? GlobalOpenTelemetry.getPropagators().getTextMapPropagator()
                        .extract(Context.current(), Map.of("traceparent", traceparent), MAP_GETTER)
                : Context.current();

        try (Scope ignored = parent.makeCurrent()) {
            logger.logReceive(channel, envelope);
            dispatcher.dispatch(channel, envelope);
        } catch (Exception ex) {
            logger.logError(channel, envelope, ex);
        }
    }
}

