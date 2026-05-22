package com.chatweb.presence.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PresenceHeartbeatCommandRequest {
    private Boolean active;
}
