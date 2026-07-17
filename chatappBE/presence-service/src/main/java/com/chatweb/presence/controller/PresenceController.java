package com.chatweb.presence.controller;


import com.chatweb.common.integration.presence.PresenceUserStatePayload;
import com.chatweb.common.web.controller.BaseController;
import com.chatweb.common.web.response.ApiResponse;
import com.chatweb.common.security.jwt.JwtHelper;
import com.chatweb.common.core.exception.BusinessException;
import com.chatweb.common.core.exception.CommonErrorCode;
import com.chatweb.presence.authorization.RoomAuthorizationService;
import com.chatweb.presence.dto.PresenceSelfResponse;
import com.chatweb.presence.dto.UpdatePresenceStatusRequest;
import com.chatweb.presence.service.IPresenceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/presence")
@RequiredArgsConstructor
public class PresenceController extends BaseController {

    private final IPresenceService presenceService;
        private final RoomAuthorizationService roomAuthorizationService;


    @GetMapping("/me")
    public ResponseEntity<ApiResponse<PresenceSelfResponse>> me(
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(presenceService.getSelfPresence(JwtHelper.extractUserId(jwt).orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED, "Unauthorized"))))
        );
    }

    @PutMapping("/me/status")
    public ResponseEntity<ApiResponse<PresenceSelfResponse>> updateStatus(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdatePresenceStatusRequest request
    ) {
        UUID userId = JwtHelper.extractUserId(jwt).orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED, "Unauthorized"));
        presenceService.updatePresence(userId, request.getMode(), request.getStatus());

        return ResponseEntity.ok(
                ApiResponse.success(presenceService.getSelfPresence(userId))
        );
    }

    @GetMapping("/global")
    public ResponseEntity<ApiResponse<List<PresenceUserStatePayload>>> globalPresence() {
        return ResponseEntity.ok(
                ApiResponse.success(presenceService.getAllPresenceUsers())
        );
    }

    @GetMapping("/room/{roomId}")
    public ResponseEntity<ApiResponse<List<PresenceUserStatePayload>>> roomOnline(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID roomId
    ) {
        roomAuthorizationService.ensureRoomMember(roomId, jwt != null ? jwt.getTokenValue() : null);
        return ResponseEntity.ok(
                ApiResponse.success(presenceService.getRoomPresence(roomId))
        );
    }
}





