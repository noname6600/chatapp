package com.chatweb.presence.service;

import com.chatweb.common.integration.presence.PresenceMode;
import com.chatweb.common.integration.presence.PresenceStatus;
import com.chatweb.common.integration.presence.PresenceEventType;
import com.chatweb.common.integration.presence.PresenceRoomJoinPayload;
import com.chatweb.common.integration.presence.PresenceRoomLeavePayload;
import com.chatweb.common.integration.presence.PresenceUserOfflinePayload;
import com.chatweb.common.integration.presence.PresenceUserOnlinePayload;
import com.chatweb.common.integration.presence.PresenceUserStatePayload;
import com.chatweb.common.integration.presence.RoomOnlineUsersPayload;
import com.chatweb.common.realtime.policy.RealtimeFlowId;
import com.chatweb.presence.dto.PresenceSelfResponse;
import com.chatweb.presence.realtime.port.PresenceRealtimePort;
import com.chatweb.presence.service.model.StoredPresenceState;
import com.chatweb.presence.state.port.PresenceEphemeralStatePort;
import com.chatweb.presence.state.port.PresencePreferencePort;
import com.chatweb.presence.state.port.PresenceTtlCachePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PresenceService implements IPresenceService {

    private static final PresenceMode DEFAULT_MODE = PresenceMode.AUTO;

    private final PresenceTtlCachePort presenceTtlCachePort;
    private final PresenceEphemeralStatePort presenceEphemeralStatePort;
    private final PresenceRealtimePort presenceRealtimePort;
    private final PresencePreferencePort presencePreferencePort;

    private StoredPresenceState getStoredPresenceState(UUID userId) {
        return presenceTtlCachePort.get(userId);
    }

    private void saveStoredPresenceState(UUID userId, StoredPresenceState state) {
        presenceTtlCachePort.put(userId, state);
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

        presenceEphemeralStatePort.incrementConnectionCount(userId);
        StoredPresenceState existingState = getStoredPresenceState(userId);

        // When no active session exists, restore the user's saved preference so
        // their chosen mode/status persists across reconnects and logouts.
        StoredPresenceState baseState = existingState != null
                ? existingState
                : resolveFreshState(userId);

        StoredPresenceState nextState = baseState.toBuilder().active(true).build();

        saveStoredPresenceState(userId, nextState);
        presenceEphemeralStatePort.addOnlineUser(userId);

        // Always publish USER_ONLINE so every connected client learns about this
        // user's current status. This ensures users with stale OFFLINE data in
        // their local stores get refreshed on every (re)connect.
        presenceRealtimePort.publishUserEvent(
                PresenceEventType.USER_ONLINE.value(),
                PresenceUserOnlinePayload.builder()
                        .userId(userId)
                        .roomId(null)
                        .status(effectiveStatusOf(nextState))
                        .build()
        );
    }

    private StoredPresenceState resolveFreshState(UUID userId) {
        StoredPresenceState saved = presencePreferencePort.get(userId);
        return saved != null ? saved : defaultState();
    }

    public void heartbeat(UUID userId, boolean active) {
        StoredPresenceState currentState = getStoredPresenceState(userId);
        PresenceStatus previousStatus = effectiveStatusOf(currentState);
        StoredPresenceState nextState = (currentState == null ? defaultState() : currentState)
                .toBuilder()
                .active(active)
                .build();

        saveStoredPresenceState(userId, nextState);
    presenceEphemeralStatePort.addOnlineUser(userId);

        PresenceStatus nextStatus = effectiveStatusOf(nextState);
        if (nextStatus != previousStatus) {
            presenceRealtimePort.publishUserEvent(
                    PresenceEventType.USER_STATUS_CHANGED.value(),
                    PresenceUserStatePayload.builder()
                            .userId(userId)
                            .status(nextStatus)
                            .build()
            );
        }
    }

    public void offline(UUID userId) {
        long remainingConnections = presenceEphemeralStatePort.decrementConnectionCount(userId);
        if (remainingConnections > 0) {
            return;
        }

        presenceTtlCachePort.evict(userId);

        cleanupUserEverywhere(userId);

        presenceRealtimePort.publishUserEvent(
            PresenceEventType.USER_OFFLINE.value(),
            PresenceUserOfflinePayload.builder()
                .userId(userId)
                .roomId(null)
                .status(PresenceStatus.OFFLINE)
                .build()
        );
    }

    public void handleUserOfflineByTTL(UUID userId) {
        presenceEphemeralStatePort.clearConnectionCount(userId);
        presenceTtlCachePort.evict(userId);

        cleanupUserEverywhere(userId);

        presenceRealtimePort.publishUserEvent(
            PresenceEventType.USER_OFFLINE.value(),
            PresenceUserOfflinePayload.builder()
                .userId(userId)
                .roomId(null)
                .status(PresenceStatus.OFFLINE)
                .build()
        );
    }

    // ================= ROOM MEMBERSHIP =================

    public void joinRoom(UUID roomId, UUID userId) {

        if (getStoredPresenceState(userId) == null) return;

        presenceEphemeralStatePort.addUserToRoom(roomId, userId);

        presenceRealtimePort.publishRoomEvent(
            roomId,
            PresenceEventType.ROOM_JOIN.value(),
            PresenceRoomJoinPayload.builder()
                .userId(userId)
                .roomId(roomId)
                .build()
            ,
            RealtimeFlowId.PRESENCE_ROOM_ACTIVITY
        );
    }

    public void leaveRoom(UUID roomId, UUID userId) {
        presenceEphemeralStatePort.removeUserFromRoom(roomId, userId);

        presenceRealtimePort.publishRoomEvent(
            roomId,
            PresenceEventType.ROOM_LEAVE.value(),
            PresenceRoomLeavePayload.builder()
                .userId(userId)
                .roomId(roomId)
                .build()
            ,
            RealtimeFlowId.PRESENCE_ROOM_ACTIVITY
        );
    }

    private void cleanupUserEverywhere(UUID userId) {
        presenceEphemeralStatePort.removeOnlineUser(userId);

        Set<UUID> rooms = presenceEphemeralStatePort.getUserRooms(userId);
        for (UUID roomId : rooms) {
            presenceEphemeralStatePort.removeUserFromRoom(roomId, userId);
        }

        presenceEphemeralStatePort.clearUserRooms(userId);
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
        presenceEphemeralStatePort.addOnlineUser(userId);

        // Persist so this mode/status is restored on the next session.
        presencePreferencePort.save(userId, nextState.toBuilder().active(false).build());

        PresenceStatus nextStatus = effectiveStatusOf(nextState);
        presenceRealtimePort.publishUserEvent(
                PresenceEventType.USER_STATUS_CHANGED.value(),
                PresenceUserStatePayload.builder()
                        .userId(userId)
                        .status(nextStatus)
                        .build(),
                RealtimeFlowId.PRESENCE_USER_STATUS_CHANGED
        );
    }

    public PresenceSelfResponse getSelfPresence(UUID userId) {
        StoredPresenceState state = getStoredPresenceState(userId);

        if (state == null) {
            // Not currently connected — show saved preference so the UI reflects
            // the mode the user last chose rather than defaulting to AUTO every time.
            StoredPresenceState preference = presencePreferencePort.get(userId);
            if (preference != null) {
                return PresenceSelfResponse.builder()
                        .mode(preference.getMode())
                        .manualStatus(preference.getManualStatus())
                        .effectiveStatus(PresenceStatus.OFFLINE)
                        .connected(false)
                        .build();
            }
        }

        return PresenceSelfResponse.builder()
                .mode(state != null ? state.getMode() : PresenceMode.AUTO)
                .manualStatus(state != null ? state.getManualStatus() : null)
                .effectiveStatus(effectiveStatusOf(state))
                .connected(state != null)
                .build();
    }

    public List<PresenceUserStatePayload> getAllPresenceUsers() {
        // Skip users whose TTL-cache entry is null — they are between heartbeats or
        // being cleaned up by the expiry listener. Returning them as OFFLINE would
        // incorrectly downgrade their status in clients' local stores.
        return presenceEphemeralStatePort.getOnlineUsers().stream()
                .filter(userId -> getStoredPresenceState(userId) != null)
                .map(this::toUserState)
                .sorted(Comparator.comparing(payload -> payload.getUserId().toString()))
                .toList();
    }

    public List<PresenceUserStatePayload> getRoomPresence(UUID roomId) {
        return presenceEphemeralStatePort.getRoomUsers(roomId).stream()
                .filter(userId -> getStoredPresenceState(userId) != null)
                .map(this::toUserState)
                .sorted(Comparator.comparing(payload -> payload.getUserId().toString()))
                .toList();
    }

    // ================= NOTIFY =================

    public void notifyRoomOnlineUsers(UUID roomId) {
        presenceRealtimePort.publishRoomEvent(
                roomId,
                PresenceEventType.ROOM_ONLINE_USERS.value(),
                RoomOnlineUsersPayload.builder()
                        .roomId(roomId)
                        .users(getRoomPresence(roomId))
                        .build()
            ,
            RealtimeFlowId.PRESENCE_ROOM_ACTIVITY
        );
    }
}