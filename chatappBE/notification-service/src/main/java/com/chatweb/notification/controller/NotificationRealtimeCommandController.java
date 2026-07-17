package com.chatweb.notification.controller;

import com.chatweb.common.core.exception.BusinessException;
import com.chatweb.common.core.exception.CommonErrorCode;
import com.chatweb.common.security.jwt.JwtHelper;
import com.chatweb.common.web.controller.BaseController;
import com.chatweb.common.web.response.ApiResponse;
import com.chatweb.notification.dto.NotificationRealtimeCommandRequest;
import com.chatweb.notification.service.impl.NotificationCommandService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Minimal HTTP command endpoint used by realtime-edge-service during Phase B.
 *
 * This endpoint reuses existing notification command services and preserves
 * the current websocket handler and legacy REST behavior.
 */
@RestController
@RequestMapping("/api/v1/notifications/realtime")
@RequiredArgsConstructor
public class NotificationRealtimeCommandController extends BaseController {

    private final NotificationCommandService commandService;

    @PostMapping("/commands")
    public ResponseEntity<ApiResponse<Void>> handleCommand(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody NotificationRealtimeCommandRequest request
    ) {
        UUID userId = JwtHelper.extractUserId(jwt)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED, "Unauthorized"));

        String command = request.getCommand();
        if (command == null || command.isBlank()) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "command is required");
        }

        switch (command) {
            case "mark-read" -> {
                UUID notificationId = requireNotificationId(request.getNotificationId());
                commandService.markRead(notificationId, userId);
            }
            case "mark-all-read" -> commandService.markAllRead(userId);
            case "clear-room" -> {
                UUID roomId = requireRoomId(request.getRoomId());
                commandService.clearRoom(userId, roomId);
            }
            case "mark-read-by-room" -> {
                UUID roomId = requireRoomId(request.getRoomId());
                commandService.markReadByRoom(userId, roomId);
            }
            default -> throw new BusinessException(CommonErrorCode.BAD_REQUEST, "Unsupported notification command: " + command);
        }

        return ok();
    }

    private UUID requireNotificationId(UUID notificationId) {
        if (notificationId == null) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "notificationId is required");
        }
        return notificationId;
    }

    private UUID requireRoomId(UUID roomId) {
        if (roomId == null) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "roomId is required");
        }
        return roomId;
    }
}
