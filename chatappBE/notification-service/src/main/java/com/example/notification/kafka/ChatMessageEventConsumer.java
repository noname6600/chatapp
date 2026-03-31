package com.example.notification.kafka;

import com.example.common.kafka.Topics;
import com.example.common.kafka.event.ChatMessageSentEvent;
import com.example.notification.service.impl.NotificationDomainService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class ChatMessageEventConsumer {

    private final NotificationDomainService notificationService;

    @KafkaListener(topics = Topics.CHAT_MESSAGE_SENT)
    public void listen(ChatMessageSentEvent event) {
        var payload = event.getPayload();

        log.info("[NOTI] Chat message received: {}", payload.getContent());

        UUID fakeReceiverId = payload.getRoomId();

        notificationService.notifyChatMessage(
                fakeReceiverId,
                payload.getContent(),
                payload.getSenderId()
        );
    }
}

