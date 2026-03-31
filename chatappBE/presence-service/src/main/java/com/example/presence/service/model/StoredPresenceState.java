package com.example.presence.service.model;

import com.example.common.integration.presence.PresenceMode;
import com.example.common.integration.presence.PresenceStatus;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class StoredPresenceState {

    private PresenceMode mode;

    private PresenceStatus manualStatus;

    private boolean active;
}