package com.example.chat.config;
import com.example.chat.modules.room.dto.RoomMessagePinEventPayload;
import com.example.common.integration.chat.*;
import com.example.common.redis.registry.IRedisMessageRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class ChatRedisEventConfig {

    private final IRedisMessageRegistry registry;

    @PostConstruct
    public void registerEvents() {

        registry.register(
                ChatEventType.MESSAGE_SENT.value(),
                ChatMessagePayload.class
        );

        registry.register(
                ChatEventType.REACTION_UPDATED.value(),
                ReactionPayload.class
        );

        registry.register(
                ChatEventType.MESSAGE_EDITED.value(),
                MessageUpdatedPayload.class
        );

        registry.register(
                ChatEventType.MESSAGE_DELETED.value(),
                MessageDeletedPayload.class
        );

        registry.register(
                ChatEventType.MESSAGE_PINNED.value(),
                RoomMessagePinEventPayload.class
        );

        registry.register(
                ChatEventType.MESSAGE_UNPINNED.value(),
                RoomMessagePinEventPayload.class
        );
    }
}