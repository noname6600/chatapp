package com.chatweb.presence.state.port;

import com.chatweb.presence.service.model.StoredPresenceState;

import java.util.UUID;

public interface PresenceTtlCachePort {

    StoredPresenceState get(UUID userId);

    void put(UUID userId, StoredPresenceState state);

    void evict(UUID userId);
}
