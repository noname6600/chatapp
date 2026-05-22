package com.chatweb.presence.state.redis;

import com.chatweb.presence.state.port.PresenceEphemeralStatePort;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class RedisPresenceEphemeralStateStore implements PresenceEphemeralStatePort {

    private static final String USERS_ONLINE_KEY = "presence::users:online";
    private static final String ROOM_PREFIX = "presence::room:";
    private static final String USER_ROOMS_PREFIX = "presence::user:rooms:";
    private static final String USER_CONNECTIONS_PREFIX = "presence::user:connections:";
    private static final Duration EPHEMERAL_TTL = Duration.ofSeconds(90);

    private final StringRedisTemplate redis;

    @Override
    public long incrementConnectionCount(UUID userId) {
        Long count = redis.opsForValue().increment(userConnectionsKey(userId));
        if (count == null) {
            throw new IllegalStateException("Cannot increment connection count");
        }
        refreshUserTtl(userId);
        return count;
    }

    @Override
    public long decrementConnectionCount(UUID userId) {
        Long count = redis.opsForValue().decrement(userConnectionsKey(userId));
        if (count == null || count <= 0) {
            redis.delete(userConnectionsKey(userId));
            return 0L;
        }
        refreshUserTtl(userId);
        return count;
    }

    @Override
    public void clearConnectionCount(UUID userId) {
        redis.delete(userConnectionsKey(userId));
    }

    @Override
    public void addOnlineUser(UUID userId) {
        redis.opsForSet().add(USERS_ONLINE_KEY, userId.toString());
    }

    @Override
    public void removeOnlineUser(UUID userId) {
        redis.opsForSet().remove(USERS_ONLINE_KEY, userId.toString());
    }

    @Override
    public Set<UUID> getOnlineUsers() {
        return toUuidSet(redis.opsForSet().members(USERS_ONLINE_KEY));
    }

    @Override
    public void addUserToRoom(UUID roomId, UUID userId) {
        redis.opsForSet().add(roomKey(roomId), userId.toString());
        redis.opsForSet().add(userRoomsKey(userId), roomId.toString());
        redis.expire(roomKey(roomId), EPHEMERAL_TTL);
        refreshUserTtl(userId);
    }

    @Override
    public void removeUserFromRoom(UUID roomId, UUID userId) {
        redis.opsForSet().remove(roomKey(roomId), userId.toString());
        redis.opsForSet().remove(userRoomsKey(userId), roomId.toString());
    }

    @Override
    public Set<UUID> getRoomUsers(UUID roomId) {
        return toUuidSet(redis.opsForSet().members(roomKey(roomId)));
    }

    @Override
    public Set<UUID> getUserRooms(UUID userId) {
        return toUuidSet(redis.opsForSet().members(userRoomsKey(userId)));
    }

    @Override
    public void clearUserRooms(UUID userId) {
        redis.delete(userRoomsKey(userId));
    }

    private Set<UUID> toUuidSet(Set<String> rawValues) {
        if (rawValues == null || rawValues.isEmpty()) {
            return Set.of();
        }
        return rawValues.stream()
                .map(UUID::fromString)
                .collect(Collectors.toSet());
    }

    private String roomKey(UUID roomId) {
        return ROOM_PREFIX + roomId;
    }

    private String userRoomsKey(UUID userId) {
        return USER_ROOMS_PREFIX + userId;
    }

    private String userConnectionsKey(UUID userId) {
        return USER_CONNECTIONS_PREFIX + userId;
    }

    private void refreshUserTtl(UUID userId) {
        redis.expire(userRoomsKey(userId), EPHEMERAL_TTL);
        redis.expire(userConnectionsKey(userId), EPHEMERAL_TTL);
    }
}
