package com.example.presence.service;

import com.example.common.integration.presence.PresenceMode;
import com.example.common.integration.presence.PresenceStatus;
import com.example.common.integration.presence.PresenceUserStatePayload;
import com.example.common.redis.api.ITimeRedisCacheManager;
import com.example.common.redis.exception.CreateCacheException;
import com.example.presence.dto.PresenceSelfResponse;
import com.example.presence.redis.PresenceRedisPublisher;
import com.example.presence.service.model.StoredPresenceState;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PresenceService implements IPresenceService {

    private static final String CACHE = "presence";
    private static final Duration USER_TTL = Duration.ofSeconds(30);
    private static final PresenceMode DEFAULT_MODE = PresenceMode.AUTO;

    private static final String USER_PREFIX = "presence::user:";
    private static final String USERS_ONLINE_KEY = "presence::users:online";
    private static final String ROOM_PREFIX = "presence::room:";
    private static final String USER_ROOMS_PREFIX = "presence::user:rooms:";

    private final ITimeRedisCacheManager cacheManager;
    private final PresenceRedisPublisher redisPublisher;
    private final StringRedisTemplate redis;

    private String userKey(UUID userId) {
        return USER_PREFIX + userId;
    }

    private String roomKey(UUID roomId) {
        return ROOM_PREFIX + roomId;
    }

    private String userRoomsKey(UUID userId) {
        return USER_ROOMS_PREFIX + userId;
    }

    private StoredPresenceState getStoredPresenceState(UUID userId) {
        try {
            return cacheManager.get(CACHE, userKey(userId), StoredPresenceState.class);
        } catch (CreateCacheException e) {
            return null;
        }
    }

    private void saveStoredPresenceState(UUID userId, StoredPresenceState state) {
        try {
            cacheManager.put(CACHE, userKey(userId), state, USER_TTL);
        } catch (CreateCacheException ignored) {}
    }

    private StoredPresenceState defaultState() {
        return StoredPresenceState.builder()
                .mode(DEFAULT_MODE)
                .manualStatus(null)
                .active(true)
                .build();
    }

    private PresenceStatus effectiveStatusOf(StoredPresenceState state) {
        if (state == null) {
            return PresenceStatus.OFFLINE;
        }

        if (state.getMode() == PresenceMode.MANUAL && state.getManualStatus() != null) {
            return state.getManualStatus();
        }

        return state.isActive() ? PresenceStatus.ONLINE : PresenceStatus.AWAY;
    }

    private PresenceUserStatePayload toUserState(UUID userId) {
        return PresenceUserStatePayload.builder()
                .userId(userId)
                .status(effectiveStatusOf(getStoredPresenceState(userId)))
                .build();
    }

    // ================= USER PRESENCE =================

    public void online(UUID userId) {

        StoredPresenceState existingState = getStoredPresenceState(userId);
        boolean wasConnected = existingState != null;
        StoredPresenceState nextState = (existingState == null ? defaultState() : existingState)
                .toBuilder()
                .active(true)
                .build();

        saveStoredPresenceState(userId, nextState);

        redis.opsForSet().add(USERS_ONLINE_KEY, userId.toString());

        if (!wasConnected) {
            redisPublisher.online(userId, effectiveStatusOf(nextState));
        }
    }

    public void heartbeat(UUID userId, boolean active) {
        StoredPresenceState currentState = getStoredPresenceState(userId);
        PresenceStatus previousStatus = effectiveStatusOf(currentState);
        StoredPresenceState nextState = (currentState == null ? defaultState() : currentState)
                .toBuilder()
                .active(active)
                .build();

        saveStoredPresenceState(userId, nextState);
        redis.opsForSet().add(USERS_ONLINE_KEY, userId.toString());

        PresenceStatus nextStatus = effectiveStatusOf(nextState);
        if (nextStatus != previousStatus) {
            redisPublisher.statusChanged(userId, nextStatus);
        }
    }

    public void offline(UUID userId) {

        try {
            cacheManager.evict(CACHE, userKey(userId));
        } catch (Exception ignored) {}

        cleanupUserEverywhere(userId);

        redisPublisher.offline(userId);
    }

    public void handleUserOfflineByTTL(UUID userId) {

        cleanupUserEverywhere(userId);

        redisPublisher.offline(userId);
    }

    // ================= ROOM MEMBERSHIP =================

    public void joinRoom(UUID roomId, UUID userId) {

        if (getStoredPresenceState(userId) == null) return;

        String userIdStr = userId.toString();

        redis.opsForSet().add(roomKey(roomId), userIdStr);
        redis.opsForSet().add(userRoomsKey(userId), roomId.toString());

        redisPublisher.roomJoin(userId, roomId);
    }

    public void leaveRoom(UUID roomId, UUID userId) {

        String userIdStr = userId.toString();

        redis.opsForSet().remove(roomKey(roomId), userIdStr);
        redis.opsForSet().remove(userRoomsKey(userId), roomId.toString());

        redisPublisher.roomLeave(userId, roomId);
    }

    private void cleanupUserEverywhere(UUID userId) {

        String userIdStr = userId.toString();

        redis.opsForSet().remove(USERS_ONLINE_KEY, userIdStr);

        Set<String> rooms = redis.opsForSet().members(userRoomsKey(userId));

        if (rooms != null) {
            for (String roomId : rooms) {
                redis.opsForSet().remove(roomKey(UUID.fromString(roomId)), userIdStr);
            }
        }

        redis.delete(userRoomsKey(userId));
    }

    // ================= QUERY =================

    private Set<UUID> getUsersFromSet(String key) {

        Set<String> values = redis.opsForSet().members(key);

        if (values == null || values.isEmpty()) {
            return Set.of();
        }

        return values.stream()
                .map(UUID::fromString)
                .collect(Collectors.toSet());
    }

    public void updatePresence(UUID userId, PresenceMode mode, PresenceStatus status) {
        StoredPresenceState currentState = getStoredPresenceState(userId);
        PresenceStatus previousStatus = effectiveStatusOf(currentState);
        StoredPresenceState baseState = currentState == null ? defaultState() : currentState;

        StoredPresenceState nextState = baseState.toBuilder()
                .mode(mode)
                .manualStatus(mode == PresenceMode.MANUAL ? status : null)
                .build();

        saveStoredPresenceState(userId, nextState);
        redis.opsForSet().add(USERS_ONLINE_KEY, userId.toString());

        PresenceStatus nextStatus = effectiveStatusOf(nextState);
        if (nextStatus != previousStatus) {
            redisPublisher.statusChanged(userId, nextStatus);
        }
    }

    public PresenceSelfResponse getSelfPresence(UUID userId) {
        StoredPresenceState state = getStoredPresenceState(userId);

        return PresenceSelfResponse.builder()
                .mode(state != null ? state.getMode() : PresenceMode.AUTO)
                .manualStatus(state != null ? state.getManualStatus() : null)
                .effectiveStatus(effectiveStatusOf(state))
                .connected(state != null)
                .build();
    }

    public List<PresenceUserStatePayload> getAllPresenceUsers() {
        return getUsersFromSet(USERS_ONLINE_KEY).stream()
                .map(this::toUserState)
                .sorted(Comparator.comparing(payload -> payload.getUserId().toString()))
                .toList();
    }

    public List<PresenceUserStatePayload> getRoomPresence(UUID roomId) {
        return getUsersFromSet(roomKey(roomId)).stream()
                .map(this::toUserState)
                .sorted(Comparator.comparing(payload -> payload.getUserId().toString()))
                .toList();
    }

    // ================= NOTIFY =================

    public void notifyRoomOnlineUsers(UUID roomId) {

        redisPublisher.roomOnlineUsers(roomId, getRoomPresence(roomId));
    }
}