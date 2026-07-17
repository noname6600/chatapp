package com.chatweb.voice.adapter.in.web.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class ActiveVoiceRoomResponse {
    private UUID chatRoomId;
    // Named without an "is" prefix so Lombok's generated boolean getter
    // (isLiveElsewhere()) and Jackson's property-name stripping agree on the
    // same JSON key ("liveElsewhere") instead of silently disagreeing.
    private boolean liveElsewhere;
}
