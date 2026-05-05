package com.example.presence.state.port;

import com.example.presence.service.model.StoredPresenceState;

import java.util.UUID;

public interface PresenceTtlCachePort {

    StoredPresenceState get(UUID userId);

    void put(UUID userId, StoredPresenceState state);

    void evict(UUID userId);
}
