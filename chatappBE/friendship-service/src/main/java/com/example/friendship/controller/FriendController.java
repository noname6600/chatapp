package com.example.friendship.controller;

import com.example.common.web.controller.BaseController;
import com.example.common.web.response.ApiResponse;
import com.example.common.security.jwt.JwtHelper;
import com.example.common.core.exception.BusinessException;
import com.example.common.core.exception.CommonErrorCode;
import com.example.friendship.dto.SendFriendRequestByUsernameRequest;
import com.example.friendship.dto.UnreadCountResponse;
import com.example.friendship.enums.FriendshipStatus;
import com.example.friendship.service.IFriendCommandService;
import com.example.friendship.service.IFriendQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/friends")
@RequiredArgsConstructor
public class FriendController extends BaseController {
    private final IFriendCommandService commandService;
    private final IFriendQueryService queryService;


    @PostMapping("/request/username")
    public ResponseEntity<ApiResponse<Void>> sendRequestByUsername(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody SendFriendRequestByUsernameRequest request
    ) {
        commandService.sendRequestByUsername(JwtHelper.extractUserId(jwt).orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED, "Unauthorized")), request.getUsername().trim());
        return ok();
    }

    @PostMapping("/request/{userId:[0-9a-fA-F\\-]{36}}")
    public ResponseEntity<ApiResponse<Void>> sendRequest(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID userId
    ) {
        commandService.sendRequest(JwtHelper.extractUserId(jwt).orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED, "Unauthorized")), userId);
        return ok();
    }

    @PostMapping("/accept/{userId}")
    public ResponseEntity<ApiResponse<Void>> accept(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID userId
    ) {
        commandService.accept(JwtHelper.extractUserId(jwt).orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED, "Unauthorized")), userId);
        return ok();
    }

    @PostMapping("/decline/{userId}")
    public ResponseEntity<ApiResponse<Void>> decline(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID userId
    ) {
        commandService.decline(JwtHelper.extractUserId(jwt).orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED, "Unauthorized")), userId);
        return ok();
    }

    @PostMapping("/cancel/{userId}")
    public ResponseEntity<ApiResponse<Void>> cancel(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID userId
    ) {
        commandService.cancel(JwtHelper.extractUserId(jwt).orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED, "Unauthorized")), userId);
        return ok();
    }

    @PostMapping("/unfriend/{userId}")
    public ResponseEntity<ApiResponse<Void>> unfriend(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID userId
    ) {
        commandService.unfriend(JwtHelper.extractUserId(jwt).orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED, "Unauthorized")), userId);
        return ok();
    }

    @PostMapping("/block/{userId}")
    public ResponseEntity<ApiResponse<Void>> block(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID userId
    ) {
        commandService.block(JwtHelper.extractUserId(jwt).orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED, "Unauthorized")), userId);
        return ok();
    }

    @PostMapping("/unblock/{userId}")
    public ResponseEntity<ApiResponse<Void>> unblock(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID userId
    ) {
        commandService.unblock(JwtHelper.extractUserId(jwt).orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED, "Unauthorized")), userId);
        return ok();
    }


    @GetMapping
    public ResponseEntity<ApiResponse<List<UUID>>> friends(
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ok(queryService.getFriends(JwtHelper.extractUserId(jwt).orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED, "Unauthorized"))));
    }

    @GetMapping("/requests/incoming")
    public ResponseEntity<ApiResponse<List<UUID>>> incomingRequests(
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ok(queryService.getIncomingRequests(JwtHelper.extractUserId(jwt).orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED, "Unauthorized"))));
    }

    @GetMapping("/requests/outgoing")
    public ResponseEntity<ApiResponse<List<UUID>>> outgoingRequests(
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ok(queryService.getOutgoingRequests(JwtHelper.extractUserId(jwt).orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED, "Unauthorized"))));
    }

    @GetMapping("/blocks/me")
    public ResponseEntity<ApiResponse<List<UUID>>> blockedByMe(
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ok(queryService.getBlockedByMe(JwtHelper.extractUserId(jwt).orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED, "Unauthorized"))));
    }

    @GetMapping("/blocks/by-others")
    public ResponseEntity<ApiResponse<List<UUID>>> blockedMe(
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ok(queryService.getBlockedMe(JwtHelper.extractUserId(jwt).orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED, "Unauthorized"))));
    }

    @GetMapping("/status/{userId}")
    public ResponseEntity<ApiResponse<String>> status(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID userId
    ) {
        FriendshipStatus status = queryService.getStatus(JwtHelper.extractUserId(jwt).orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED, "Unauthorized")), userId);
        return ok(status != null ? status.name() : "NONE");
    }

    @GetMapping("/unread-count")
        @Operation(summary = "Get unread friend request count")
        @ApiResponses(value = {
                @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Unread friend request count returned successfully",
                content = @Content(schema = @Schema(implementation = UnreadCountResponse.class))
            )
        })
    public ResponseEntity<ApiResponse<UnreadCountResponse>> getUnreadCount(
            @AuthenticationPrincipal Jwt jwt
    ) {
        long unreadCount = queryService.getUnreadFriendRequestCount(JwtHelper.extractUserId(jwt).orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED, "Unauthorized")));
        return ok(UnreadCountResponse.builder()
                .unreadCount(unreadCount)
                .build());
    }
}








