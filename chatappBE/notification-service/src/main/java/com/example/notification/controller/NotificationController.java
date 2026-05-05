package com.example.notification.controller;

import com.example.common.web.controller.BaseController;
import com.example.common.web.response.ApiResponse;
import com.example.common.security.jwt.JwtHelper;
import com.example.common.core.exception.BusinessException;
import com.example.common.core.exception.CommonErrorCode;
import com.example.notification.dto.NotificationListResponse;
import com.example.notification.dto.NotificationResponse;
import com.example.notification.dto.UnreadCountResponse;
import com.example.notification.service.impl.NotificationCommandService;
import com.example.notification.service.impl.NotificationQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController extends BaseController {

    private final NotificationQueryService queryService;
    private final NotificationCommandService commandService;

    @GetMapping
    public ResponseEntity<ApiResponse<NotificationListResponse>> getMyNotifications(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "50") int size,
            @RequestParam(name = "beforeCreatedAt", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant beforeCreatedAt
    ) {
        return ResponseEntity.ok(
                                ApiResponse.success(queryService.getNotificationsForUser(JwtHelper.extractUserId(jwt).orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED, "Unauthorized")), page, size, beforeCreatedAt))
        );
    }

    @GetMapping("/unread")
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> getUnread(
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(queryService.getUnreadNotifications(JwtHelper.extractUserId(jwt).orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED, "Unauthorized"))))
        );
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<ApiResponse<Void>> markRead(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt
    ) {
                commandService.markRead(id, JwtHelper.extractUserId(jwt).orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED, "Unauthorized")));
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/read-all")
    public ResponseEntity<ApiResponse<Void>> markAllRead(
            @AuthenticationPrincipal Jwt jwt
    ) {
                commandService.markAllRead(JwtHelper.extractUserId(jwt).orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED, "Unauthorized")));
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/rooms/{roomId}/clear")
    public ResponseEntity<ApiResponse<Void>> clearRoomNotifications(
            @PathVariable UUID roomId,
            @AuthenticationPrincipal Jwt jwt
    ) {
                commandService.clearRoom(JwtHelper.extractUserId(jwt).orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED, "Unauthorized")), roomId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

        @PostMapping("/mark-read-by-room/{roomId}")
        public ResponseEntity<ApiResponse<Void>> markReadByRoom(
                        @PathVariable UUID roomId,
                        @AuthenticationPrincipal Jwt jwt
        ) {
                commandService.markReadByRoom(JwtHelper.extractUserId(jwt).orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED, "Unauthorized")), roomId);
                return ResponseEntity.ok(ApiResponse.success(null));
        }

    @GetMapping("/unread/count")
    public ResponseEntity<ApiResponse<UnreadCountResponse>> getUnreadCount(
            @AuthenticationPrincipal Jwt jwt
    ) {
        long count = queryService.countUnread(JwtHelper.extractUserId(jwt).orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED, "Unauthorized")));

        UnreadCountResponse response = UnreadCountResponse.builder()
                .unreadCount(count)
                .build();

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}







