package com.chatweb.realtime.subscription;

import com.chatweb.realtime.connection.RealtimeSession;
import com.chatweb.realtime.routing.command.IChatCommandRouter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * Manages subscription requests: authorization, validation, registry updates.
 *
 * Defines channel types and authorization rules.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ChannelSubscriptionManager {

    private static final String ROOM_ACCESS_CACHE_PREFIX = "room:access:";
    private static final Duration ROOM_ACCESS_CACHE_TTL = Duration.ofSeconds(30);

    private final IChatCommandRouter chatCommandRouter;
    private final StringRedisTemplate redisTemplate;

    /**
     * Channel types and their authorization rules.
     */
    public enum ChannelType {
        ROOM("room:"),           // room:{roomId} - messages, reactions, member updates
        USER("user:"),           // user:{userId} - notifications, friend requests
        PRESENCE("presence:"),   // presence:{roomId} - online status
        TYPING("typing:"),       // typing:{roomId} - typing indicators
        NOTIFICATION("notification:"); // notification:{userId} - unread counts

        public final String prefix;

        ChannelType(String prefix) {
            this.prefix = prefix;
        }

        public static ChannelType fromChannel(String channel) {
            for (ChannelType type : values()) {
                if (channel.startsWith(type.prefix)) {
                    return type;
                }
            }
            return null;
        }
    }

    /**
     * Validates if a session can subscribe to a channel.
     *
     * @param session the session requesting subscription
     * @param channel the channel to subscribe to
     * @return true if authorized, false otherwise
     */
    public boolean isAuthorized(RealtimeSession session, String channel, String accessToken) {
        ChannelType type = ChannelType.fromChannel(channel);
        if (type == null) {
            log.warn("[AUTH] Unknown channel type: {}", channel);
            return false;
        }

        switch (type) {
            case ROOM:
                return hasRoomAccess(session.getUserId(), accessToken, channel, type);
            case USER:
                // Only the user themselves or admins can subscribe to user:{userId} channels
                String userIdStr = channel.substring(ChannelType.USER.prefix.length());
                try {
                    UUID userId = UUID.fromString(userIdStr);
                    return session.getUserId().equals(userId);
                } catch (IllegalArgumentException e) {
                    log.warn("[AUTH] Invalid userId in channel: {}", channel);
                    return false;
                }
            case PRESENCE:
                return hasRoomAccess(session.getUserId(), accessToken, channel, type);
            case TYPING:
                return hasRoomAccess(session.getUserId(), accessToken, channel, type);
            case NOTIFICATION:
                // Only the user themselves can subscribe to their notification channel
                String notifUserIdStr = channel.substring(ChannelType.NOTIFICATION.prefix.length());
                try {
                    UUID userId = UUID.fromString(notifUserIdStr);
                    return session.getUserId().equals(userId);
                } catch (IllegalArgumentException e) {
                    log.warn("[AUTH] Invalid userId in notification channel: {}", channel);
                    return false;
                }
            default:
                return false;
        }
    }

    private boolean hasRoomAccess(UUID userId, String accessToken, String channel, ChannelType type) {
        String roomIdStr = channel.substring(type.prefix.length());

        UUID roomId;
        try {
            roomId = UUID.fromString(roomIdStr);
        } catch (IllegalArgumentException e) {
            log.warn("[AUTH] Invalid roomId in channel: {}", channel);
            return false;
        }

        // Cache lookup: stale window ≤ 60 s — brief post-leave access is acceptable
        // vs. the cost of an HTTP call per subscription request.
        String cacheKey = roomAccessCacheKey(userId, roomId);
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                return Boolean.parseBoolean(cached);
            }
        } catch (Exception ex) {
            log.warn("[AUTH] Cache read failed for room access check, falling through to service: {}", ex.getMessage());
        }

        boolean hasAccess = chatCommandRouter.canAccessRoom(accessToken, roomId);

        try {
            redisTemplate.opsForValue().set(cacheKey, String.valueOf(hasAccess), ROOM_ACCESS_CACHE_TTL);
        } catch (Exception ex) {
            log.warn("[AUTH] Failed to cache room access result: {}", ex.getMessage());
        }

        return hasAccess;
    }

    private String roomAccessCacheKey(UUID userId, UUID roomId) {
        return ROOM_ACCESS_CACHE_PREFIX + userId + ":" + roomId;
    }

    /**
     * Validates channel name format.
     *
     * @param channel the channel name
     * @return true if valid format, false otherwise
     */
    public boolean isValidChannelFormat(String channel) {
        if (channel == null || channel.isBlank()) {
            return false;
        }
        // Channel must have type prefix and a non-empty ID
        ChannelType type = ChannelType.fromChannel(channel);
        if (type == null) {
            return false;
        }
        String id = channel.substring(type.prefix.length());
        return !id.isBlank();
    }
}
