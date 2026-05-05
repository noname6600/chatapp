package com.example.presence.service;

import com.example.common.integration.presence.PresenceMode;
import com.example.common.integration.presence.PresenceStatus;
import com.example.common.integration.presence.PresenceEventType;
import com.example.common.integration.presence.PresenceUserOfflinePayload;
import com.example.common.integration.presence.PresenceUserOnlinePayload;
import com.example.common.integration.presence.PresenceUserStatePayload;
import com.example.common.realtime.policy.RealtimeFlowId;
import com.example.presence.realtime.port.PresenceRealtimePort;
import com.example.presence.service.model.StoredPresenceState;
import com.example.presence.state.port.PresenceEphemeralStatePort;
import com.example.presence.state.port.PresenceTtlCachePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PresenceServiceTest {

    @Mock
    private PresenceTtlCachePort presenceTtlCachePort;

    @Mock
    private PresenceEphemeralStatePort presenceEphemeralStatePort;

    @Mock
    private PresenceRealtimePort presenceRealtimePort;

    private final Map<String, StoredPresenceState> presenceStates = new HashMap<>();
    private final Map<String, Set<String>> redisSets = new HashMap<>();

    private PresenceService service;

    @BeforeEach
    void setUp() {
        service = new PresenceService(presenceTtlCachePort, presenceEphemeralStatePort, presenceRealtimePort);

        when(presenceTtlCachePort.get(any())).thenAnswer(invocation -> presenceStates.get(invocation.getArgument(0).toString()));

        doAnswer(invocation -> {
            UUID userId = invocation.getArgument(0);
            StoredPresenceState state = invocation.getArgument(1);
            presenceStates.put(userId.toString(), state);
            return null;
        }).when(presenceTtlCachePort).put(any(), any());

        doAnswer(invocation -> {
            UUID userId = invocation.getArgument(0);
            presenceStates.remove(userId.toString());
            return null;
        }).when(presenceTtlCachePort).evict(any());

        doAnswer(invocation -> {
            UUID userId = invocation.getArgument(0);
            redisSets.computeIfAbsent("presence::users:online", ignored -> new HashSet<>()).add(userId.toString());
            return null;
        }).when(presenceEphemeralStatePort).addOnlineUser(any());

        doAnswer(invocation -> {
            UUID userId = invocation.getArgument(0);
            Set<String> users = redisSets.get("presence::users:online");
            if (users != null) {
                users.remove(userId.toString());
            }
            return null;
        }).when(presenceEphemeralStatePort).removeOnlineUser(any());

        when(presenceEphemeralStatePort.getOnlineUsers()).thenAnswer(invocation ->
                redisSets.getOrDefault("presence::users:online", Set.of()).stream()
                        .map(UUID::fromString)
                        .collect(java.util.stream.Collectors.toSet())
        );

        doAnswer(invocation -> {
            UUID roomId = invocation.getArgument(0);
            UUID userId = invocation.getArgument(1);
            redisSets.computeIfAbsent("presence::room:" + roomId, ignored -> new HashSet<>()).add(userId.toString());
            redisSets.computeIfAbsent("presence::user:rooms:" + userId, ignored -> new HashSet<>()).add(roomId.toString());
            return null;
        }).when(presenceEphemeralStatePort).addUserToRoom(any(), any());

        doAnswer(invocation -> {
            UUID roomId = invocation.getArgument(0);
            UUID userId = invocation.getArgument(1);
            Set<String> roomUsers = redisSets.get("presence::room:" + roomId);
            if (roomUsers != null) {
                roomUsers.remove(userId.toString());
            }
            Set<String> userRooms = redisSets.get("presence::user:rooms:" + userId);
            if (userRooms != null) {
                userRooms.remove(roomId.toString());
            }
            return null;
        }).when(presenceEphemeralStatePort).removeUserFromRoom(any(), any());

        when(presenceEphemeralStatePort.getRoomUsers(any())).thenAnswer(invocation -> {
            UUID roomId = invocation.getArgument(0);
            return redisSets.getOrDefault("presence::room:" + roomId, Set.of()).stream()
                    .map(UUID::fromString)
                    .collect(java.util.stream.Collectors.toSet());
        });

        when(presenceEphemeralStatePort.getUserRooms(any())).thenAnswer(invocation -> {
            UUID userId = invocation.getArgument(0);
            return redisSets.getOrDefault("presence::user:rooms:" + userId, Set.of()).stream()
                    .map(UUID::fromString)
                    .collect(java.util.stream.Collectors.toSet());
        });

        doAnswer(invocation -> {
            UUID userId = invocation.getArgument(0);
            redisSets.remove("presence::user:rooms:" + userId);
            return null;
        }).when(presenceEphemeralStatePort).clearUserRooms(any());
    }

    @Test
    void heartbeatTransitionsToAwayAndManualOverrideCanForceOffline() {
        UUID userId = UUID.randomUUID();

        service.online(userId);
        service.heartbeat(userId, false);
        service.updatePresence(userId, PresenceMode.MANUAL, PresenceStatus.OFFLINE);

        assertThat(service.getSelfPresence(userId).getEffectiveStatus()).isEqualTo(PresenceStatus.OFFLINE);
        assertThat(service.getSelfPresence(userId).getMode()).isEqualTo(PresenceMode.MANUAL);

        verify(presenceRealtimePort).publishUserEvent(eq(PresenceEventType.USER_ONLINE.value()), any(PresenceUserOnlinePayload.class));
        verify(presenceRealtimePort).publishUserEvent(eq(PresenceEventType.USER_STATUS_CHANGED.value()), any(PresenceUserStatePayload.class));
        verify(presenceRealtimePort).publishUserEvent(
            eq(PresenceEventType.USER_STATUS_CHANGED.value()),
            any(PresenceUserStatePayload.class),
            eq(RealtimeFlowId.PRESENCE_USER_STATUS_CHANGED)
        );
    }

    @Test
    void roomPresenceUsesEffectiveStatuses() {
        UUID roomId = UUID.randomUUID();
        UUID onlineUser = UUID.randomUUID();
        UUID awayUser = UUID.randomUUID();

        service.online(onlineUser);
        service.online(awayUser);
        service.updatePresence(awayUser, PresenceMode.MANUAL, PresenceStatus.AWAY);

        service.joinRoom(roomId, onlineUser);
        service.joinRoom(roomId, awayUser);

        List<PresenceUserStatePayload> roomPresence = service.getRoomPresence(roomId);

        assertThat(roomPresence)
                .extracting(PresenceUserStatePayload::getStatus)
                .containsExactlyInAnyOrder(PresenceStatus.ONLINE, PresenceStatus.AWAY);
    }

    @Test
    void offlineCleanupRemovesUserFromRoomPresence() {
        UUID roomId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        service.online(userId);
        service.joinRoom(roomId, userId);

        service.offline(userId);

        assertThat(service.getRoomPresence(roomId)).isEmpty();
        verify(presenceRealtimePort).publishUserEvent(eq(PresenceEventType.USER_OFFLINE.value()), any(PresenceUserOfflinePayload.class));
    }
}