package com.example.notification.controller;

import com.example.common.web.controller.BaseController;
import com.example.common.web.response.ApiResponse;
import com.example.notification.dto.RoomSettingsResponse;
import com.example.notification.dto.RoomSettingsUpdateRequest;
import com.example.notification.entity.RoomNotificationMode;
import com.example.notification.service.impl.RoomMuteSettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping({"/api/v1/rooms", "/api/v1/notifications/rooms"})
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
        RoomNotificationMode mode = roomMuteSettingService.getMode(currentUserId(jwt), roomId);
        return ResponseEntity.ok(
                ApiResponse.success(
                        RoomSettingsResponse.builder()
                                .isMuted(mode == RoomNotificationMode.NOTHING)
                                .mode(mode.name())
                                .build()
                )
        );
    }

    @PutMapping("/{roomId}/settings")
    public ResponseEntity<ApiResponse<RoomSettingsResponse>> updateSettings(
            @PathVariable UUID roomId,
            @RequestBody RoomSettingsUpdateRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        RoomNotificationMode mode = parseMode(request == null ? null : request.getMode());
        RoomNotificationMode updatedMode = roomMuteSettingService.setMode(currentUserId(jwt), roomId, mode);

        return ResponseEntity.ok(
                ApiResponse.success(
                        RoomSettingsResponse.builder()
                                .isMuted(updatedMode == RoomNotificationMode.NOTHING)
                                .mode(updatedMode.name())
                                .build()
                )
        );
    }

    private RoomNotificationMode parseMode(String rawMode) {
        if (rawMode == null || rawMode.isBlank()) {
            return RoomNotificationMode.NO_RESTRICT;
        }

        return RoomNotificationMode.valueOf(rawMode.trim().toUpperCase());
    }
}
