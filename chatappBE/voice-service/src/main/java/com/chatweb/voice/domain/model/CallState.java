package com.chatweb.voice.domain.model;

import java.util.UUID;

public record CallState(
        UUID callId,
        UUID callerId,
        UUID calleeId,
        CallStatus status,
        String lkRoomName,
        String callerLkToken,
        long createdAt
) {}
