package com.example.presence.configuration;

import com.example.common.integration.presence.*;
import com.example.common.redis.registry.IRedisMessageRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class PresenceRedisRegistryConfig {

    private final IRedisMessageRegistry registry;

    @PostConstruct
    public void register() {

        registry.register(
                PresenceEventType.USER_ONLINE.value(),
                PresenceUserOnlinePayload.class
        );

        registry.register(
                PresenceEventType.USER_OFFLINE.value(),
                PresenceUserOfflinePayload.class
        );

        registry.register(
                PresenceEventType.USER_STATUS_CHANGED.value(),
                PresenceUserStatePayload.class
        );


        registry.register(
                PresenceEventType.ROOM_TYPING.value(),
                PresenceTypingPayload.class
        );

        registry.register(
                PresenceEventType.ROOM_STOP_TYPING.value(),
                PresenceStopTypingPayload.class
        );

        registry.register(
                PresenceEventType.ROOM_JOIN.value(),
                PresenceRoomJoinPayload.class
        );

        registry.register(
                PresenceEventType.ROOM_LEAVE.value(),
                PresenceRoomLeavePayload.class
        );

        registry.register(
                PresenceEventType.ROOM_ONLINE_USERS.value(),
                RoomOnlineUsersPayload.class
        );

        registry.register(
                PresenceEventType.GLOBAL_ONLINE_USERS.value(),
                GlobalOnlineUsersPayload.class
        );
    }
}