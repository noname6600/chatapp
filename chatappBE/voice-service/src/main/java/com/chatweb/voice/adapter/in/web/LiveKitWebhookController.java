package com.chatweb.voice.adapter.in.web;

import com.chatweb.voice.application.VoiceRoomService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/internal/livekit")
@RequiredArgsConstructor
@Slf4j
public class LiveKitWebhookController {

    private final VoiceRoomService voiceRoomService;
    private final ObjectMapper objectMapper;

    @PostMapping("/webhook")
    public ResponseEntity<Void> handleWebhook(
            @RequestBody String rawBody,
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        try {
            JsonNode event = objectMapper.readTree(rawBody);
            String eventName = event.path("event").asText(null);

            if (eventName == null) {
                return ResponseEntity.ok().build();
            }

            log.debug("[LK-WEBHOOK] event={}", eventName);

            switch (eventName) {
                case "participant_left" -> handleParticipantLeft(event);
                case "room_finished" -> handleRoomFinished(event);
                default -> log.debug("[LK-WEBHOOK] Unhandled event={}", eventName);
            }
        } catch (Exception ex) {
            log.warn("[LK-WEBHOOK] Failed to process webhook body", ex);
        }

        return ResponseEntity.ok().build();
    }

    private void handleParticipantLeft(JsonNode event) {
        String roomName = event.path("room").path("name").asText(null);
        String identity = event.path("participant").path("identity").asText(null);

        if (roomName == null || identity == null) return;
        if (!roomName.startsWith("voice-")) return;

        try {
            UUID chatRoomId = UUID.fromString(roomName.substring("voice-".length()));
            UUID userId = UUID.fromString(identity);
            voiceRoomService.handleWebhookLeave(chatRoomId, userId);
        } catch (Exception ex) {
            log.warn("[LK-WEBHOOK] Failed to process participant_left roomName={} identity={}", roomName, identity, ex);
        }
    }

    private void handleRoomFinished(JsonNode event) {
        String roomName = event.path("room").path("name").asText(null);
        if (roomName == null || !roomName.startsWith("voice-")) return;
        log.info("[LK-WEBHOOK] Room finished roomName={}", roomName);
    }
}
