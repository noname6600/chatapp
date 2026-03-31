package com.example.presence.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class HeartbeatPayload {
    private UUID userId;
    private long lastSeenAt;
}
