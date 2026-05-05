package com.example.common.websocket.protocol;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RealtimeWsEvent {

    private String type;

    @JsonAlias("data")
    private Object payload;
}
