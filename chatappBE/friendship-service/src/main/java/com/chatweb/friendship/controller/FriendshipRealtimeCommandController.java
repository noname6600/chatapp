package com.chatweb.friendship.controller;

import com.chatweb.common.core.exception.BusinessException;
import com.chatweb.common.core.exception.CommonErrorCode;
import com.chatweb.common.security.jwt.JwtHelper;
import com.chatweb.common.web.controller.BaseController;
import com.chatweb.common.web.response.ApiResponse;
import com.chatweb.friendship.dto.FriendshipRealtimeCommandRequest;
import com.chatweb.friendship.service.IFriendCommandService;
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
 * Additive HTTP command endpoint for realtime-edge friendship command forwarding.
 */
@RestController
@RequestMapping("/api/v1/friends/realtime")
@RequiredArgsConstructor
public class FriendshipRealtimeCommandController extends BaseController {

    private final IFriendCommandService commandService;

    @PostMapping("/commands")
    public ResponseEntity<ApiResponse<Void>> handleCommand(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody FriendshipRealtimeCommandRequest request
    ) {
        UUID userId = JwtHelper.extractUserId(jwt)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED, "Unauthorized"));

        String command = request == null ? null : request.getCommand();
        if (command == null || command.isBlank()) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "command is required");
        }

        UUID targetUserId = requireTargetUserId(request);
        switch (command) {
            case "send-request", "send-friend-request" -> commandService.sendRequest(userId, targetUserId);
            case "accept-request" -> commandService.accept(userId, targetUserId);
            case "decline-request" -> commandService.decline(userId, targetUserId);
            case "cancel-request" -> commandService.cancel(userId, targetUserId);
            case "unfriend" -> commandService.unfriend(userId, targetUserId);
            case "block" -> commandService.block(userId, targetUserId);
            case "unblock" -> commandService.unblock(userId, targetUserId);
            default -> throw new BusinessException(CommonErrorCode.BAD_REQUEST, "Unsupported friendship command: " + command);
        }

        return ok();
    }

    private UUID requireTargetUserId(FriendshipRealtimeCommandRequest request) {
        if (request == null || request.getTargetUserId() == null) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "targetUserId is required");
        }
        return request.getTargetUserId();
    }
}
