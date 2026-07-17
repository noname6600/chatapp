package com.chatweb.voice.adapter.in.web;

import com.chatweb.common.core.exception.BusinessException;
import com.chatweb.common.core.exception.CommonErrorCode;
import com.chatweb.common.security.jwt.JwtHelper;
import com.chatweb.common.web.response.ApiResponse;
import com.chatweb.voice.adapter.in.web.dto.AcceptCallResponse;
import com.chatweb.voice.adapter.in.web.dto.InitiateCallRequest;
import com.chatweb.voice.adapter.in.web.dto.InitiateCallResponse;
import com.chatweb.voice.application.CallService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/voice/calls")
@RequiredArgsConstructor
public class CallController {

    private final CallService callService;

    @PostMapping
    public ResponseEntity<ApiResponse<InitiateCallResponse>> initiate(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody InitiateCallRequest request
    ) {
        UUID callerId = extractUserId(jwt);
        InitiateCallResponse resp = callService.initiate(callerId, request.targetUserId());
        return ResponseEntity.ok(ApiResponse.success(resp));
    }

    @PostMapping("/{callId}/accept")
    public ResponseEntity<ApiResponse<AcceptCallResponse>> accept(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID callId
    ) {
        UUID calleeId = extractUserId(jwt);
        AcceptCallResponse resp = callService.accept(callId, calleeId);
        return ResponseEntity.ok(ApiResponse.success(resp));
    }

    @PostMapping("/{callId}/decline")
    public ResponseEntity<ApiResponse<Void>> decline(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID callId
    ) {
        UUID calleeId = extractUserId(jwt);
        callService.decline(callId, calleeId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/{callId}/cancel")
    public ResponseEntity<ApiResponse<Void>> cancel(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID callId
    ) {
        UUID callerId = extractUserId(jwt);
        callService.cancel(callId, callerId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/{callId}/end")
    public ResponseEntity<ApiResponse<Void>> end(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID callId
    ) {
        UUID requesterId = extractUserId(jwt);
        callService.end(callId, requesterId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    private UUID extractUserId(Jwt jwt) {
        return JwtHelper.extractUserId(jwt)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED, "Unauthorized"));
    }
}
