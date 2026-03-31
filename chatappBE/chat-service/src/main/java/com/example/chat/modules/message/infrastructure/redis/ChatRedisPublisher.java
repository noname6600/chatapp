package com.example.chat.modules.message.infrastructure.redis;


import com.example.chat.constants.ChatRedisChannels;
import com.example.common.integration.chat.*;
import com.example.common.redis.api.IRedisPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;


@Component
@RequiredArgsConstructor
public class ChatRedisPublisher {

    private final IRedisPublisher redisPublisher;
    private final RedisMessageFactory redisMessageFactory;

    public void publishMessageSent(ChatMessagePayload payload) {

        publish(
                payload.getRoomId(),
                ChatEventType.MESSAGE_SENT.value(),
                payload
        );
    }

    public void publishMessageEdited(MessageUpdatedPayload payload) {

        publish(
                payload.getRoomId(),
                ChatEventType.MESSAGE_EDITED.value(),
                payload
        );
    }

    public void publishMessageDeleted(MessageDeletedPayload payload) {

        publish(
                payload.getRoomId(),
                ChatEventType.MESSAGE_DELETED.value(),
                payload
        );
    }

    public void publishReactionUpdated(
            ReactionPayload payload
    ) {

        publish(
                payload.getRoomId(),
                ChatEventType.REACTION_UPDATED.value(),
                payload
        );
    }

    private void publish(
            UUID roomId,
            String eventType,
            Object payload
    ) {

        String channel =
                ChatRedisChannels.CHAT_ROOM + roomId;

        redisPublisher.publish(
                channel,
                redisMessageFactory.create(
                        eventType,
                        payload
                )
        );
    }
}