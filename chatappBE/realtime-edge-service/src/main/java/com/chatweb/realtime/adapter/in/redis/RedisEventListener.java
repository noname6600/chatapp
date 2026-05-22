package com.chatweb.realtime.adapter.in.redis;

import com.chatweb.realtime.delivery.ChatRealtimeDeliveryService;
import com.chatweb.realtime.delivery.NotificationRealtimeDeliveryService;
import com.chatweb.realtime.delivery.PresenceRealtimeDeliveryService;
import com.chatweb.realtime.subscription.ChannelSubscriptionManager;
import com.chatweb.common.redis.channel.RedisChannels;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class RedisEventListener implements MessageListener {

    private static final String CHAT_ROOM_PREFIX = RedisChannels.CHAT_ROOM_PREFIX;
    private static final String NOTIFICATION_USER_PREFIX = RedisChannels.NOTIFICATION_USER_PREFIX;
    private static final String PRESENCE_USER_CHANNEL = RedisChannels.PRESENCE_USER;
    private static final String PRESENCE_GLOBAL_CHANNEL = RedisChannels.PRESENCE_GLOBAL;
    private static final String PRESENCE_ROOM_PREFIX = RedisChannels.PRESENCE_ROOM_PREFIX;
        private static final Set<String> ROOM_MEMBERSHIP_EVENTS = Set.of(
            "chat.room.member.joined",
            "chat.room.member.left",
            "chat.room.member.removed"
        );

    private final ChatRealtimeDeliveryService chatDeliveryService;
    private final NotificationRealtimeDeliveryService notificationDeliveryService;
    private final PresenceRealtimeDeliveryService presenceDeliveryService;
    private final ChannelSubscriptionManager channelSubscriptionManager;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);

        try {
            JsonNode root = objectMapper.readTree(payload);
            String eventType = root.path("metadata").path("eventType").asText(null);
            String eventId = root.path("metadata").path("eventId").asText(null);
            JsonNode body = root.path("payload");

            if (eventType == null || eventId == null || body.isMissingNode()) {
                log.warn("[REDIS] Missing metadata or payload channel={} payload={}", channel, payload);
                return;
            }

            if (channel.startsWith(NOTIFICATION_USER_PREFIX)) {
                UUID userId;
                try {
                    userId = UUID.fromString(channel.substring(NOTIFICATION_USER_PREFIX.length()));
                } catch (Exception ex) {
                    log.warn("[REDIS] Invalid notification channel user id channel={}", channel, ex);
                    return;
                }
                notificationDeliveryService.deliverToUser(userId, eventType, eventId, body);
                return;
            }

            if (channel.startsWith(CHAT_ROOM_PREFIX)) {
                String roomId = channel.substring(CHAT_ROOM_PREFIX.length());
                if (roomId.isBlank()) {
                    return;
                }
                invalidateMembershipCacheIfNeeded(eventType, roomId, body);
                chatDeliveryService.deliverRoom(roomId, eventType, eventId, body);
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

    private void invalidateMembershipCacheIfNeeded(String eventType, String roomId, JsonNode body) {
        if (!ROOM_MEMBERSHIP_EVENTS.contains(eventType)) {
            return;
        }

        try {
            UUID roomUuid = UUID.fromString(roomId);
            String userIdRaw = body.path("userId").asText(null);
            if (userIdRaw == null || userIdRaw.isBlank()) {
                return;
            }
            UUID userId = UUID.fromString(userIdRaw);
            channelSubscriptionManager.invalidateRoomAccess(userId, roomUuid);
        } catch (Exception ex) {
            log.debug("[REDIS] Skipping room access cache invalidation eventType={} roomId={} reason={}",
                    eventType, roomId, ex.getMessage());
        }
    }
}
