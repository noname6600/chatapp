package com.example.chat.modules.message.controller;

import com.example.chat.modules.message.application.dto.response.MessagePage;
import com.example.chat.modules.message.application.dto.response.MessageResponse;
import com.example.chat.modules.message.application.query.IMessageQueryService;
import com.example.common.web.controller.BaseController;
import com.example.common.web.response.ApiResponse;
import com.example.common.security.jwt.JwtHelper;
import com.example.common.core.exception.BusinessException;
import com.example.common.core.exception.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/messages")
@RequiredArgsConstructor
public class MessageQueryController extends BaseController {

    private final IMessageQueryService messageQueryService;

    @GetMapping("/latest")
    public ResponseEntity<ApiResponse<MessagePage>> getLatestMessages(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam UUID roomId,
            @RequestParam(defaultValue = "50") int limit
    ) {

        MessagePage response =
                messageQueryService.getLatestMessages(
                        JwtHelper.extractUserId(jwt).orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED, "Unauthorized")),
                        roomId,
                        limit
                );

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/before")
    public ResponseEntity<ApiResponse<MessagePage>> getMessagesBefore(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam UUID roomId,
            @RequestParam long beforeSeq,
            @RequestParam(defaultValue = "50") int limit
    ) {

        MessagePage response =
                messageQueryService.getMessagesBefore(
                        JwtHelper.extractUserId(jwt).orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED, "Unauthorized")),
                        roomId,
                        beforeSeq,
                        limit
                );

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/range")
    public ResponseEntity<ApiResponse<List<MessageResponse>>> getMessageRange(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam UUID roomId,
            @RequestParam long startSeq,
            @RequestParam long endSeq
    ) {

        List<MessageResponse> response =
                messageQueryService.getMessageRange(
                        JwtHelper.extractUserId(jwt).orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED, "Unauthorized")),
                        roomId,
                        startSeq,
                        endSeq
                );

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/around")
    public ResponseEntity<ApiResponse<List<MessageResponse>>> getMessagesAround(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam UUID roomId,
            @RequestParam UUID messageId,
            @RequestParam(defaultValue = "25") int halfWindow
    ) {

        List<MessageResponse> response =
                messageQueryService.getMessagesAround(
                        JwtHelper.extractUserId(jwt).orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED, "Unauthorized")),
                        roomId,
                        messageId,
                        halfWindow
                );

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}


