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

    /** All chat room ids that currently have at least one recorded participant. */
    Set<UUID> getRoomsWithParticipants();

    /** Epoch millis the participant was recorded as joined, or null if not present. */
    Long getParticipantJoinedAt(UUID chatRoomId, UUID userId);
}
