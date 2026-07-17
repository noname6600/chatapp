package com.chatweb.voice.domain.port.out;

import com.chatweb.voice.domain.model.CallState;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CallStatePort {
    void save(CallState state);
    Optional<CallState> findById(UUID callId);
    void delete(UUID callId);

    void setActiveCall(UUID userId, UUID callId);
    Optional<UUID> getActiveCall(UUID userId);
    void clearActiveCall(UUID userId);

    boolean tryLockMissed(UUID callId);

    void addToRingingIndex(UUID callId, long createdAt);
    void removeFromRingingIndex(UUID callId);
    List<String> findRingingCallIdsBefore(long epochMs);
}
