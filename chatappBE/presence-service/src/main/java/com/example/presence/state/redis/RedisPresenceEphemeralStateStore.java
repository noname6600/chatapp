package com.example.presence.state.redis;

import com.example.presence.state.port.PresenceEphemeralStatePort;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class RedisPresenceEphemeralStateStore implements PresenceEphemeralStatePort {

    private static final String USERS_ONLINE_KEY = "presence::users:online";
    private static final String ROOM_PREFIX = "presence::room:";
    private static final String USER_ROOMS_PREFIX = "presence::user:rooms:";

    private final StringRedisTemplate redis;

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
}
