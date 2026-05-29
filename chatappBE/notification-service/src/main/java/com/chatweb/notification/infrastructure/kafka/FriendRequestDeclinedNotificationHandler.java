package com.chatweb.notification.infrastructure.kafka;

import com.chatweb.common.event.EventEnvelope;
import com.chatweb.common.integration.friendship.FriendRequestPayload;
import com.chatweb.common.kafka.consumer.KafkaEventHandler;
import com.chatweb.notification.application.NotificationKafkaEventApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FriendRequestDeclinedNotificationHandler implements KafkaEventHandler<FriendRequestPayload> {

    private final NotificationKafkaEventApplicationService applicationService;

    @Override
    public String eventType() {
        return "friend.request.declined";
    }

    @Override
    public void handle(EventEnvelope<FriendRequestPayload> event) {
        applicationService.handleFriendRequestEvent(
                event.metadata().getEventId(),
                event.metadata().getEventType(),
                event.payload()
        );
    }
}
