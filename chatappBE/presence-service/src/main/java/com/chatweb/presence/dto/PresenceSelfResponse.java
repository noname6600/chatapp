package com.chatweb.presence.dto;

import com.chatweb.common.integration.presence.PresenceMode;
import com.chatweb.common.integration.presence.PresenceStatus;
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