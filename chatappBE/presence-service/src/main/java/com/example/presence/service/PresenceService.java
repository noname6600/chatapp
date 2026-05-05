package com.example.presence.service;

import com.example.common.integration.presence.PresenceMode;
import com.example.common.integration.presence.PresenceStatus;
import com.example.common.integration.presence.PresenceEventType;
import com.example.common.integration.presence.PresenceRoomJoinPayload;
import com.example.common.integration.presence.PresenceRoomLeavePayload;
import com.example.common.integration.presence.PresenceUserOfflinePayload;
import com.example.common.integration.presence.PresenceUserOnlinePayload;
import com.example.common.integration.presence.PresenceUserStatePayload;
import com.example.common.integration.presence.RoomOnlineUsersPayload;
import com.example.common.realtime.policy.RealtimeFlowId;
import com.example.presence.dto.PresenceSelfResponse;
import com.example.presence.realtime.port.PresenceRealtimePort;
import com.example.presence.service.model.StoredPresenceState;
import com.example.presence.state.port.PresenceEphemeralStatePort;
import com.example.presence.state.port.PresenceTtlCachePort;
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

        StoredPresenceState existingState = getStoredPresenceState(userId);
        boolean wasConnected = existingState != null;
        StoredPresenceState nextState = (existingState == null ? defaultState() : existingState)
                .toBuilder()
                .active(true)
                .build();

        saveStoredPresenceState(userId, nextState);

        presenceEphemeralStatePort.addOnlineUser(userId);

        if (!wasConnected) {
            presenceRealtimePort.publishUserEvent(
                    PresenceEventType.USER_ONLINE.value(),
                    PresenceUserOnlinePayload.builder()
                            .userId(userId)
                            .roomId(null)
                            .status(effectiveStatusOf(nextState))
                            .build()
            );
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

        PresenceStatus nextStatus = effectiveStatusOf(nextState);
        if (nextStatus != previousStatus) {
            presenceRealtimePort.publishUserEvent(
                    PresenceEventType.USER_STATUS_CHANGED.value(),
                    PresenceUserStatePayload.builder()
                            .userId(userId)
                            .status(nextStatus)
                            .build()
                    ,
                    RealtimeFlowId.PRESENCE_USER_STATUS_CHANGED
            );
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
        return presenceEphemeralStatePort.getOnlineUsers().stream()
                .map(this::toUserState)
                .sorted(Comparator.comparing(payload -> payload.getUserId().toString()))
                .toList();
    }

    public List<PresenceUserStatePayload> getRoomPresence(UUID roomId) {
        return presenceEphemeralStatePort.getRoomUsers(roomId).stream()
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