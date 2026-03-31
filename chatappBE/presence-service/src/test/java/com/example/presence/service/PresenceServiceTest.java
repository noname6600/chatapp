package com.example.presence.service;

import com.example.common.integration.presence.PresenceMode;
import com.example.common.integration.presence.PresenceStatus;
import com.example.common.integration.presence.PresenceUserStatePayload;
import com.example.common.redis.api.ITimeRedisCacheManager;
import com.example.common.redis.exception.CreateCacheException;
import com.example.presence.redis.PresenceRedisPublisher;
import com.example.presence.service.model.StoredPresenceState;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PresenceServiceTest {

    @Mock
    private ITimeRedisCacheManager cacheManager;

    @Mock
    private PresenceRedisPublisher redisPublisher;

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private SetOperations<String, String> setOperations;

    private final Map<String, StoredPresenceState> presenceStates = new HashMap<>();
    private final Map<String, Set<String>> redisSets = new HashMap<>();

    private PresenceService service;

    @BeforeEach
    void setUp() throws CreateCacheException {
        service = new PresenceService(cacheManager, redisPublisher, redis);

        when(redis.opsForSet()).thenReturn(setOperations);

        when(cacheManager.get(eq("presence"), any(), eq(StoredPresenceState.class)))
                .thenAnswer(invocation -> presenceStates.get(invocation.getArgument(1).toString()));

        doAnswer(invocation -> {
            String key = invocation.getArgument(1).toString();
            StoredPresenceState state = invocation.getArgument(2);
            presenceStates.put(key, state);
            return null;
        }).when(cacheManager).put(eq("presence"), any(), any(), any(Duration.class));

        doAnswer(invocation -> {
            presenceStates.remove(invocation.getArgument(1).toString());
            return null;
        }).when(cacheManager).evict(eq("presence"), any());

        when(setOperations.add(anyString(), anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            String value = invocation.getArgument(1);
            redisSets.computeIfAbsent(key, ignored -> new HashSet<>()).add(value);
            return 1L;
        });

        when(setOperations.remove(anyString(), anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            String value = invocation.getArgument(1);
            Set<String> values = redisSets.get(key);
            if (values != null) {
                values.remove(value);
            }
            return 1L;
        });

        when(setOperations.members(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            return redisSets.containsKey(key) ? new HashSet<>(redisSets.get(key)) : Set.of();
        });
    }

    @Test
    void heartbeatTransitionsToAwayAndManualOverrideCanForceOffline() {
        UUID userId = UUID.randomUUID();

        service.online(userId);
        service.heartbeat(userId, false);
        service.updatePresence(userId, PresenceMode.MANUAL, PresenceStatus.OFFLINE);

        assertThat(service.getSelfPresence(userId).getEffectiveStatus()).isEqualTo(PresenceStatus.OFFLINE);
        assertThat(service.getSelfPresence(userId).getMode()).isEqualTo(PresenceMode.MANUAL);

        verify(redisPublisher).online(userId, PresenceStatus.ONLINE);
        verify(redisPublisher).statusChanged(userId, PresenceStatus.AWAY);
        verify(redisPublisher).statusChanged(userId, PresenceStatus.OFFLINE);
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
        verify(redisPublisher).offline(userId);
    }
}