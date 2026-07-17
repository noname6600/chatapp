package com.chatweb.voice.adapter.in.web;

import com.chatweb.common.core.exception.BusinessException;
import com.chatweb.common.core.exception.CommonErrorCode;
import com.chatweb.common.security.jwt.JwtHelper;
import com.chatweb.common.web.response.ApiResponse;
import com.chatweb.voice.adapter.in.web.dto.ActiveVoiceRoomResponse;
import com.chatweb.voice.adapter.in.web.dto.JoinVoiceRoomResponse;
import com.chatweb.voice.adapter.in.web.dto.VoiceParticipantDto;
import com.chatweb.voice.application.VoiceRoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/voice/rooms")
@RequiredArgsConstructor
public class VoiceRoomController {

    private final VoiceRoomService voiceRoomService;

    @PostMapping("/{chatRoomId}/join")
    public ResponseEntity<ApiResponse<JoinVoiceRoomResponse>> join(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID chatRoomId
    ) {
        UUID userId = JwtHelper.extractUserId(jwt)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED, "Unauthorized"));
        JoinVoiceRoomResponse response = voiceRoomService.join(chatRoomId, userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{chatRoomId}/leave")
    public ResponseEntity<ApiResponse<Void>> leave(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID chatRoomId
    ) {
        UUID userId = JwtHelper.extractUserId(jwt)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED, "Unauthorized"));
        voiceRoomService.leave(chatRoomId, userId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/{chatRoomId}/participants")
    public ResponseEntity<ApiResponse<List<VoiceParticipantDto>>> getParticipants(
            @PathVariable UUID chatRoomId
    ) {
        return ResponseEntity.ok(ApiResponse.success(voiceRoomService.getParticipants(chatRoomId)));
    }

    @GetMapping("/active")
    public ResponseEntity<ApiResponse<ActiveVoiceRoomResponse>> getMyActiveRoom(
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID userId = JwtHelper.extractUserId(jwt)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED, "Unauthorized"));
        return ResponseEntity.ok(ApiResponse.success(voiceRoomService.getMyActiveRoom(userId)));
    }
}
