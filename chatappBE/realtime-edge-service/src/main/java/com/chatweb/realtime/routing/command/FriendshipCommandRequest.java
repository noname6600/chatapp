package com.chatweb.realtime.routing.command;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Getter
@Setter
@NoArgsConstructor
public class FriendshipCommandRequest {
    private String command;
    private UUID targetUserId;
    private String requestId;
}
