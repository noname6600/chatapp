package com.example.friendship.controller;

import com.example.common.web.controller.BaseController;
import com.example.common.web.response.ApiResponse;
import com.example.friendship.enums.FriendshipStatus;
import com.example.friendship.service.IFriendCommandService;
import com.example.friendship.service.IFriendQueryService;
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


    @PostMapping("/request/{userId}")
    public ResponseEntity<ApiResponse<Void>> sendRequest(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID userId
    ) {
        commandService.sendRequest(currentUserId(jwt), userId);
        return ok();
    }

    @PostMapping("/accept/{userId}")
    public ResponseEntity<ApiResponse<Void>> accept(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID userId
    ) {
        commandService.accept(currentUserId(jwt), userId);
        return ok();
    }

    @PostMapping("/decline/{userId}")
    public ResponseEntity<ApiResponse<Void>> decline(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID userId
    ) {
        commandService.decline(currentUserId(jwt), userId);
        return ok();
    }

    @PostMapping("/cancel/{userId}")
    public ResponseEntity<ApiResponse<Void>> cancel(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID userId
    ) {
        commandService.cancel(currentUserId(jwt), userId);
        return ok();
    }

    @PostMapping("/unfriend/{userId}")
    public ResponseEntity<ApiResponse<Void>> unfriend(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID userId
    ) {
        commandService.unfriend(currentUserId(jwt), userId);
        return ok();
    }

    @PostMapping("/block/{userId}")
    public ResponseEntity<ApiResponse<Void>> block(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID userId
    ) {
        commandService.block(currentUserId(jwt), userId);
        return ok();
    }

    @PostMapping("/unblock/{userId}")
    public ResponseEntity<ApiResponse<Void>> unblock(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID userId
    ) {
        commandService.unblock(currentUserId(jwt), userId);
        return ok();
    }


    @GetMapping
    public ResponseEntity<ApiResponse<List<UUID>>> friends(
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ok(queryService.getFriends(currentUserId(jwt)));
    }

    @GetMapping("/requests/incoming")
    public ResponseEntity<ApiResponse<List<UUID>>> incomingRequests(
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ok(queryService.getIncomingRequests(currentUserId(jwt)));
    }

    @GetMapping("/requests/outgoing")
    public ResponseEntity<ApiResponse<List<UUID>>> outgoingRequests(
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ok(queryService.getOutgoingRequests(currentUserId(jwt)));
    }

    @GetMapping("/blocks/me")
    public ResponseEntity<ApiResponse<List<UUID>>> blockedByMe(
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ok(queryService.getBlockedByMe(currentUserId(jwt)));
    }

    @GetMapping("/blocks/by-others")
    public ResponseEntity<ApiResponse<List<UUID>>> blockedMe(
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ok(queryService.getBlockedMe(currentUserId(jwt)));
    }

    @GetMapping("/status/{userId}")
    public ResponseEntity<ApiResponse<String>> status(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID userId
    ) {
        FriendshipStatus status = queryService.getStatus(currentUserId(jwt), userId);
        return ok(status != null ? status.name() : "NONE");
    }
}







