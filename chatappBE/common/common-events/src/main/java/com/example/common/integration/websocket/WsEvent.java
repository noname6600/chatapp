package com.example.common.integration.websocket;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Builder
public class WsEvent {
    private String type;

    @JsonAlias("data")
    private Object payload;
}