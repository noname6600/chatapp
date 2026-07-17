package com.chatweb.presence.state.port;

import com.chatweb.presence.service.model.StoredPresenceState;

import java.util.UUID;

public interface PresencePreferencePort {
    StoredPresenceState get(UUID userId);
    void save(UUID userId, StoredPresenceState preference);
}
