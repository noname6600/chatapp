package com.chatweb.presence.dto;

import com.chatweb.common.integration.presence.PresenceMode;
import com.chatweb.common.integration.presence.PresenceStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UpdatePresenceStatusRequest {

    @NotNull
    private PresenceMode mode;

    private PresenceStatus status;
}