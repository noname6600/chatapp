package com.example.chat.realtime;

import com.example.chat.constants.ChatRedisChannels;
import com.example.chat.modules.message.infrastructure.redis.ChatRedisPublisher;
import com.example.chat.modules.message.infrastructure.redis.RedisMessageFactory;
import com.example.chat.modules.room.dto.RoomMessagePinEventPayload;
import com.example.chat.realtime.subscriber.ChatMessagePinnedRedisSubscriber;
import com.example.chat.realtime.subscriber.ChatMessageSentRedisSubscriber;
import com.example.chat.realtime.subscriber.ChatMessageUnpinnedRedisSubscriber;
import com.example.chat.realtime.subscriber.RealtimeEventDedupeGuard;
import com.example.common.integration.chat.ChatEventType;
import com.example.common.integration.chat.ChatMessagePayload;
import com.example.common.integration.enums.MessageType;
import com.example.common.integration.websocket.WsEvent;
import com.example.common.redis.api.IRedisPublisher;
import com.example.common.redis.message.RedisMessage;
import com.example.common.websocket.session.IRoomBroadcaster;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CrossInstanceRealtimeFanoutIntegrationTest {

    @Mock
    private IRedisPublisher redisPublisher;

    @Mock
    private IRoomBroadcaster instanceABroadcaster;

    @Mock
    private IRoomBroadcaster instanceBBroadcaster;

    private ChatRedisPublisher chatRedisPublisher;

    private final RedisMessageFactory redisMessageFactory = new RedisMessageFactory();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(redisMessageFactory, "sourceService", "chat-service-test");
        chatRedisPublisher = new ChatRedisPublisher(redisPublisher, redisMessageFactory);
    }

    @Test
    void pinAndUnpinEventsReachBothInstancesAcrossSharedBrokerBoundary() {
        UUID roomId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        RoomMessagePinEventPayload pinPayload = RoomMessagePinEventPayload.builder()
                .eventId(UUID.randomUUID())
                .roomId(roomId)
                .messageId(messageId)
                .actorId(actorId)
                .occurredAt(Instant.now())
                .build();

        RedisMessage<RoomMessagePinEventPayload> publishedPinMessage = capturePublishedPinMessage(pinPayload, true);

        ChatMessagePinnedRedisSubscriber instanceAPinSubscriber =
                new ChatMessagePinnedRedisSubscriber(instanceABroadcaster, new RealtimeEventDedupeGuard());
        ChatMessagePinnedRedisSubscriber instanceBPinSubscriber =
                new ChatMessagePinnedRedisSubscriber(instanceBBroadcaster, new RealtimeEventDedupeGuard());

        instanceAPinSubscriber.onMessage(publishedPinMessage);
        instanceBPinSubscriber.onMessage(publishedPinMessage);

        assertPinOrUnpinDelivered(instanceABroadcaster, roomId, ChatEventType.MESSAGE_PINNED.value(), messageId);
        assertPinOrUnpinDelivered(instanceBBroadcaster, roomId, ChatEventType.MESSAGE_PINNED.value(), messageId);

        clearInvocations(instanceABroadcaster, instanceBBroadcaster, redisPublisher);

        RoomMessagePinEventPayload unpinPayload = RoomMessagePinEventPayload.builder()
                .eventId(UUID.randomUUID())
                .roomId(roomId)
                .messageId(messageId)
                .actorId(actorId)
                .occurredAt(Instant.now())
                .build();

        RedisMessage<RoomMessagePinEventPayload> publishedUnpinMessage = capturePublishedPinMessage(unpinPayload, false);

        ChatMessageUnpinnedRedisSubscriber instanceAUnpinSubscriber =
                new ChatMessageUnpinnedRedisSubscriber(instanceABroadcaster, new RealtimeEventDedupeGuard());
        ChatMessageUnpinnedRedisSubscriber instanceBUnpinSubscriber =
                new ChatMessageUnpinnedRedisSubscriber(instanceBBroadcaster, new RealtimeEventDedupeGuard());

        instanceAUnpinSubscriber.onMessage(publishedUnpinMessage);
        instanceBUnpinSubscriber.onMessage(publishedUnpinMessage);

        assertPinOrUnpinDelivered(instanceABroadcaster, roomId, ChatEventType.MESSAGE_UNPINNED.value(), messageId);
        assertPinOrUnpinDelivered(instanceBBroadcaster, roomId, ChatEventType.MESSAGE_UNPINNED.value(), messageId);
    }

    @Test
    void systemJoinAndPinMessagesReachBothInstancesAcrossSharedBrokerBoundary() {
        UUID roomId = UUID.randomUUID();
        UUID actorUserId = UUID.randomUUID();
        UUID targetMessageId = UUID.randomUUID();

        ChatMessagePayload joinPayload = ChatMessagePayload.builder()
                .messageId(UUID.randomUUID())
                .roomId(roomId)
                .senderId(actorUserId)
                .seq(1L)
                .type(MessageType.SYSTEM)
                .content("Alice joined the group.")
                .systemEventType("JOIN")
                .actorUserId(actorUserId)
                .createdAt(Instant.now())
                .build();

        ChatMessagePayload pinPayload = ChatMessagePayload.builder()
                .messageId(UUID.randomUUID())
                .roomId(roomId)
                .senderId(actorUserId)
                .seq(2L)
                .type(MessageType.SYSTEM)
                .content("Alice pinned a message.")
                .systemEventType("PIN")
                .actorUserId(actorUserId)
                .targetMessageId(targetMessageId)
                .createdAt(Instant.now())
                .build();

        assertMessageSentDeliveredToBothInstances(joinPayload, roomId, MessageType.SYSTEM, null, "JOIN", actorUserId, null);
        assertMessageSentDeliveredToBothInstances(pinPayload, roomId, MessageType.SYSTEM, null, "PIN", actorUserId, targetMessageId);
    }

    @Test
    void forwardedMessageReachesBothInstancesAcrossSharedBrokerBoundary() {
        UUID roomId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        UUID sourceMessageId = UUID.randomUUID();

        ChatMessagePayload forwardedPayload = ChatMessagePayload.builder()
                .messageId(UUID.randomUUID())
                .roomId(roomId)
                .senderId(senderId)
                .seq(3L)
                .type(MessageType.TEXT)
                .content("Forwarded content")
                .forwardedFromMessageId(sourceMessageId)
                .createdAt(Instant.now())
                .build();

        assertMessageSentDeliveredToBothInstances(forwardedPayload, roomId, MessageType.TEXT, sourceMessageId, null, null, null);
    }

    private RedisMessage<RoomMessagePinEventPayload> capturePublishedPinMessage(
            RoomMessagePinEventPayload payload,
            boolean pinEvent
    ) {
        if (pinEvent) {
            chatRedisPublisher.publishMessagePinned(payload);
        } else {
            chatRedisPublisher.publishMessageUnpinned(payload);
        }

        ArgumentCaptor<RedisMessage<?>> messageCaptor = ArgumentCaptor.forClass(RedisMessage.class);
        verify(redisPublisher).publish(eq(ChatRedisChannels.CHAT_ROOM + payload.getRoomId()), messageCaptor.capture());

        @SuppressWarnings("unchecked")
        RedisMessage<RoomMessagePinEventPayload> captured =
                (RedisMessage<RoomMessagePinEventPayload>) messageCaptor.getValue();

        return captured;
    }

    private void assertMessageSentDeliveredToBothInstances(
            ChatMessagePayload payload,
            UUID roomId,
            MessageType expectedType,
            UUID expectedForwardedFromMessageId,
            String expectedSystemEventType,
            UUID expectedActorUserId,
            UUID expectedTargetMessageId
    ) {
        RedisMessage<ChatMessagePayload> publishedMessage = capturePublishedMessageSent(payload);

        ChatMessageSentRedisSubscriber instanceASubscriber = new ChatMessageSentRedisSubscriber(instanceABroadcaster);
        ChatMessageSentRedisSubscriber instanceBSubscriber = new ChatMessageSentRedisSubscriber(instanceBBroadcaster);

        instanceASubscriber.onMessage(publishedMessage);
        instanceBSubscriber.onMessage(publishedMessage);

        assertMessageSentDelivered(instanceABroadcaster, roomId, expectedType, expectedForwardedFromMessageId, expectedSystemEventType, expectedActorUserId, expectedTargetMessageId);
        assertMessageSentDelivered(instanceBBroadcaster, roomId, expectedType, expectedForwardedFromMessageId, expectedSystemEventType, expectedActorUserId, expectedTargetMessageId);

        clearInvocations(instanceABroadcaster, instanceBBroadcaster, redisPublisher);
    }

    private RedisMessage<ChatMessagePayload> capturePublishedMessageSent(ChatMessagePayload payload) {
        chatRedisPublisher.publishMessageSent(payload);

        ArgumentCaptor<RedisMessage<?>> messageCaptor = ArgumentCaptor.forClass(RedisMessage.class);
        verify(redisPublisher).publish(eq(ChatRedisChannels.CHAT_ROOM + payload.getRoomId()), messageCaptor.capture());

        @SuppressWarnings("unchecked")
        RedisMessage<ChatMessagePayload> captured = (RedisMessage<ChatMessagePayload>) messageCaptor.getValue();
        return captured;
    }

    private void assertPinOrUnpinDelivered(
            IRoomBroadcaster broadcaster,
            UUID roomId,
            String expectedEventType,
            UUID expectedMessageId
    ) {
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(broadcaster).sendToRoom(eq(roomId), eventCaptor.capture());

        WsEvent event = (WsEvent) eventCaptor.getValue();
        assertThat(event.getType()).isEqualTo(expectedEventType);

        RoomMessagePinEventPayload payload = (RoomMessagePinEventPayload) event.getPayload();
        assertThat(payload.getRoomId()).isEqualTo(roomId);
        assertThat(payload.getMessageId()).isEqualTo(expectedMessageId);
    }

    private void assertMessageSentDelivered(
            IRoomBroadcaster broadcaster,
            UUID roomId,
            MessageType expectedType,
            UUID expectedForwardedFromMessageId,
            String expectedSystemEventType,
            UUID expectedActorUserId,
            UUID expectedTargetMessageId
    ) {
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(broadcaster).sendToRoom(eq(roomId), eventCaptor.capture());

        WsEvent event = (WsEvent) eventCaptor.getValue();
        assertThat(event.getType()).isEqualTo(ChatEventType.MESSAGE_SENT.value());

        ChatMessagePayload deliveredPayload = (ChatMessagePayload) event.getPayload();
        assertThat(deliveredPayload.getRoomId()).isEqualTo(roomId);
        assertThat(deliveredPayload.getType()).isEqualTo(expectedType);
        assertThat(deliveredPayload.getForwardedFromMessageId()).isEqualTo(expectedForwardedFromMessageId);
        assertThat(deliveredPayload.getSystemEventType()).isEqualTo(expectedSystemEventType);
        assertThat(deliveredPayload.getActorUserId()).isEqualTo(expectedActorUserId);
        assertThat(deliveredPayload.getTargetMessageId()).isEqualTo(expectedTargetMessageId);
    }
}