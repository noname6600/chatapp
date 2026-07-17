package com.chatweb.realtime.redis.handler;

import com.chatweb.common.event.EventEnvelope;
import com.chatweb.common.redis.channel.RedisChannels;
import com.chatweb.common.redis.subscriber.RedisEventHandler;
import com.chatweb.realtime.delivery.PresenceRealtimeDeliveryService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class RealtimeChannelRedisEventHandler implements RedisEventHandler<Object> {

    private static final String PRESENCE_USER_CHANNEL = RedisChannels.PRESENCE_USER;
    private static final String PRESENCE_GLOBAL_CHANNEL = RedisChannels.PRESENCE_GLOBAL;
    private static final String PRESENCE_ROOM_PREFIX = RedisChannels.PRESENCE_ROOM_PREFIX;

    private final PresenceRealtimeDeliveryService presenceDeliveryService;
    private final ObjectMapper objectMapper;

    @Override
    public String eventType() {
        return "*";
    }

    @Override
    public boolean supports(String channel, EventEnvelope<?> envelope) {
        if (channel == null || channel.isBlank()) {
            return false;
        }
        // Chat and notification delivery moved to Kafka consumers (FanoutChatEventConsumer,
        // FanoutNotificationEventConsumer). Only presence events remain on the Redis path
        // because presence-service has no Kafka publishing and global broadcast semantics
        // make Redis pub/sub appropriate.
        return channel.startsWith(PRESENCE_ROOM_PREFIX)
                || PRESENCE_USER_CHANNEL.equals(channel)
                || PRESENCE_GLOBAL_CHANNEL.equals(channel);
    }

    @Override
    public void handle(String channel, EventEnvelope<Object> envelope) {
        try {
            String eventType = envelope == null || envelope.metadata() == null ? null : envelope.metadata().getEventType();
            String eventId = envelope == null || envelope.metadata() == null ? null : envelope.metadata().getEventId();
            JsonNode body = envelope == null ? null : objectMapper.valueToTree(envelope.payload());

            if (eventType == null || eventId == null || body == null || body.isMissingNode() || body.isNull()) {
                log.warn("[REDIS] Missing metadata or payload channel={}", channel);
                return;
            }

            if (PRESENCE_USER_CHANNEL.equals(channel) || PRESENCE_GLOBAL_CHANNEL.equals(channel)) {
                presenceDeliveryService.deliverGlobal(eventType, eventId, body);
                return;
            }

            if (channel.startsWith(PRESENCE_ROOM_PREFIX)) {
                String roomId = channel.substring(PRESENCE_ROOM_PREFIX.length());
                if (roomId.isBlank()) {
                    return;
                }
                presenceDeliveryService.deliverRoom(roomId, eventType, eventId, body);
            }
        } catch (Exception ex) {
            log.warn("[REDIS] Failed to process event channel={}", channel, ex);
        }
    }

}
