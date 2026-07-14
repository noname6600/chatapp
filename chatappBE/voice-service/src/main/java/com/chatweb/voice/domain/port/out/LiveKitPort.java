package com.chatweb.voice.domain.port.out;

import java.util.Set;

public interface LiveKitPort {

    void createRoom(String roomName);

    void deleteRoom(String roomName);

    String generateToken(String roomName, String participantIdentity, boolean canPublish, boolean canSubscribe);

    /**
     * Identities LiveKit currently reports as connected to the room, or null if this could not
     * be determined (LiveKit disabled, or the lookup failed) — callers must treat null as
     * "unknown" and skip reconciliation for that room rather than treating it as empty.
     */
    Set<String> listLiveParticipantIdentities(String roomName);
}
