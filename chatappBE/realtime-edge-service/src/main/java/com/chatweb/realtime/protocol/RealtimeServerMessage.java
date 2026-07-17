package com.chatweb.realtime.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Outbound message types sent to realtime clients.
 *
 * Encapsulates subscription acks, events, and system messages.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RealtimeServerMessage {
    private String type;
    private String channel;
    private String eventType;
    private String eventId;
    private Object payload;
    private String requestId;
    private Boolean success;
    private String message;

    public static RealtimeServerMessage subscribeAck(String requestId, Boolean success, String message) {
        return RealtimeServerMessage.builder()
                .type("subscribe_ack")
                .requestId(requestId)
                .success(success)
                .message(message)
                .build();
    }

    public static RealtimeServerMessage unsubscribeAck(String requestId, Boolean success, String message) {
        return RealtimeServerMessage.builder()
                .type("unsubscribe_ack")
                .requestId(requestId)
                .success(success)
                .message(message)
                .build();
    }

    public static RealtimeServerMessage event(String channel, String eventType, String eventId, Object payload) {
        return RealtimeServerMessage.builder()
                .type("event")
                .channel(channel)
                .eventType(eventType)
                .eventId(eventId)
                .payload(payload)
                .build();
    }

    public static RealtimeServerMessage pong() {
        return RealtimeServerMessage.builder()
                .type("pong")
                .build();
    }

    public static RealtimeServerMessage error(String message) {
        return RealtimeServerMessage.builder()
                .type("error")
                .success(false)
                .message(message)
                .build();
    }
}
