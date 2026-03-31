package com.example.common.integration.websocket;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Builder
public class WsEvent {
    private String type;
    private Object payload;
}