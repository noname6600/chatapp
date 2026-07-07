package com.chatweb.voice.domain.port.out;

import java.util.Set;
import java.util.UUID;

public interface VoiceRoomStatePort {

    void addParticipant(UUID chatRoomId, UUID userId);

    void removeParticipant(UUID chatRoomId, UUID userId);

    Set<String> getParticipantIds(UUID chatRoomId);

    long getParticipantCount(UUID chatRoomId);

    void addActiveRoom(UUID userId, UUID chatRoomId);

    void removeActiveRoom(UUID userId, UUID chatRoomId);

    Set<String> getActiveRooms(UUID userId);
}
