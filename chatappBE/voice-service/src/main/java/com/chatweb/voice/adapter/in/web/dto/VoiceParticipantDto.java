package com.chatweb.voice.adapter.in.web.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class VoiceParticipantDto {
    private UUID userId;
    private String username;
    private String avatarUrl;
    private long joinedAt;
}
