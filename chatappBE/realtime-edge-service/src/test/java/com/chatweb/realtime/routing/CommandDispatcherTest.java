package com.chatweb.realtime.routing;

import com.chatweb.realtime.adapter.out.friendship.RestFriendshipCommandRouter;
import com.chatweb.realtime.adapter.out.notification.RestNotificationCommandRouter;
import com.chatweb.realtime.connection.RealtimeSession;
import com.chatweb.realtime.protocol.RealtimeClientMessage;
import com.chatweb.realtime.routing.command.FriendshipCommandRequest;
import com.chatweb.realtime.routing.command.NotificationCommandRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommandDispatcherTest {

    @Mock
    private RestNotificationCommandRouter notificationCommandRouter;

    @Mock
    private RestFriendshipCommandRouter friendshipCommandRouter;

    @InjectMocks
    private CommandDispatcher dispatcher;

    @Test
    void notificationCommand_isForwardedToRouter() {
        UUID userId = UUID.randomUUID();
        RealtimeSession session = RealtimeSession.create(userId);
        RealtimeClientMessage message = RealtimeClientMessage.notification(
                "mark-read",
                Map.of("notificationId", UUID.randomUUID().toString(), "roomId", UUID.randomUUID().toString()),
                "request-1"
        );

        boolean routed = dispatcher.dispatch(session, "access-token", message);

        assertThat(routed).isTrue();
        ArgumentCaptor<NotificationCommandRequest> requestCaptor = ArgumentCaptor.forClass(NotificationCommandRequest.class);
        verify(notificationCommandRouter).route(org.mockito.ArgumentMatchers.eq("access-token"), requestCaptor.capture());
        assertThat(requestCaptor.getValue().getCommand()).isEqualTo("mark-read");
        assertThat(requestCaptor.getValue().getRequestId()).isEqualTo("request-1");
        assertThat(requestCaptor.getValue().getNotificationId()).isNotNull();
        assertThat(requestCaptor.getValue().getRoomId()).isNotNull();
    }

    @Test
    void nonNotificationCommand_isIgnored() {
        RealtimeSession session = RealtimeSession.create(UUID.randomUUID());
        RealtimeClientMessage message = new RealtimeClientMessage();
        message.setType("subscribe");

        boolean routed = dispatcher.dispatch(session, "access-token", message);

        assertThat(routed).isFalse();
    }

    @Test
    void friendshipCommand_isForwardedToRouter() {
        UUID userId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();
        RealtimeSession session = RealtimeSession.create(userId);
        RealtimeClientMessage message = new RealtimeClientMessage();
        message.setType("friendship.command");
        message.setDomain("friendship");
        message.setCommand("block");
        message.setRequestId("request-2");
        message.setPayload(Map.of("targetUserId", targetUserId.toString()));

        boolean routed = dispatcher.dispatch(session, "access-token", message);

        assertThat(routed).isTrue();
        ArgumentCaptor<FriendshipCommandRequest> requestCaptor = ArgumentCaptor.forClass(FriendshipCommandRequest.class);
        verify(friendshipCommandRouter).route(org.mockito.ArgumentMatchers.eq("access-token"), requestCaptor.capture());
        assertThat(requestCaptor.getValue().getCommand()).isEqualTo("block");
        assertThat(requestCaptor.getValue().getRequestId()).isEqualTo("request-2");
        assertThat(requestCaptor.getValue().getTargetUserId()).isEqualTo(targetUserId);
    }
}
