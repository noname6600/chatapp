package com.example.notification.controller;

import com.example.common.web.controller.BaseController;
import com.example.common.web.response.ApiResponse;
import com.example.notification.dto.RoomSettingsResponse;
import com.example.notification.service.impl.RoomMuteSettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/rooms")
@RequiredArgsConstructor
public class RoomMuteController extends BaseController {

    private final RoomMuteSettingService roomMuteSettingService;

    @PostMapping("/{roomId}/mute")
    public ResponseEntity<ApiResponse<Void>> mute(
            @PathVariable UUID roomId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        roomMuteSettingService.mute(currentUserId(jwt), roomId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @DeleteMapping("/{roomId}/mute")
    public ResponseEntity<ApiResponse<Void>> unmute(
            @PathVariable UUID roomId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        roomMuteSettingService.unmute(currentUserId(jwt), roomId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/{roomId}/settings")
    public ResponseEntity<ApiResponse<RoomSettingsResponse>> settings(
            @PathVariable UUID roomId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        boolean muted = roomMuteSettingService.isMuted(currentUserId(jwt), roomId);
        return ResponseEntity.ok(ApiResponse.success(RoomSettingsResponse.builder().isMuted(muted).build()));
    }
}
