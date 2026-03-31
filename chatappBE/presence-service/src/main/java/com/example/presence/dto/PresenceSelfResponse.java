package com.example.presence.dto;

import com.example.common.integration.presence.PresenceMode;
import com.example.common.integration.presence.PresenceStatus;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PresenceSelfResponse {

    private final PresenceMode mode;

    private final PresenceStatus manualStatus;

    private final PresenceStatus effectiveStatus;

    private final boolean connected;
}