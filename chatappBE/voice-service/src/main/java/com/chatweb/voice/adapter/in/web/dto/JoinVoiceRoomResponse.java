package com.chatweb.voice.adapter.in.web.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class JoinVoiceRoomResponse {
    private String token;
    private String liveKitUrl;
    private List<VoiceParticipantDto> participants;
}
