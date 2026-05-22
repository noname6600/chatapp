package com.chatweb.realtime.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Getter
@Setter
@NoArgsConstructor
public class RealtimeClientMessage {
    private String type;
    private String domain;
    private String command;
    private List<String> channels;
    private String requestId;
    private Map<String, Object> payload;

    public static RealtimeClientMessage subscribe(List<String> channels, String requestId) {
        RealtimeClientMessage message = new RealtimeClientMessage();
        message.setType("subscribe");
        message.setChannels(channels);
        message.setRequestId(requestId);
        return message;
    }

    public static RealtimeClientMessage unsubscribe(List<String> channels, String requestId) {
        RealtimeClientMessage message = new RealtimeClientMessage();
        message.setType("unsubscribe");
        message.setChannels(channels);
        message.setRequestId(requestId);
        return message;
    }

    public static RealtimeClientMessage ping() {
        RealtimeClientMessage message = new RealtimeClientMessage();
        message.setType("ping");
        return message;
    }

    public static RealtimeClientMessage notification(String command, Map<String, Object> payload, String requestId) {
        RealtimeClientMessage message = new RealtimeClientMessage();
        message.setType("notification.command");
        message.setDomain("notification");
        message.setCommand(command);
        message.setPayload(payload);
        message.setRequestId(requestId);
        return message;
    }
}
