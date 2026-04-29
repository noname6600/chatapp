package com.example.chat.modules.message.controller;

import com.example.chat.modules.message.application.command.IMessageCommandService;
import com.example.chat.modules.message.application.dto.request.DeleteMessageRequest;
import com.example.chat.modules.message.application.dto.request.EditMessageRequest;
import com.example.chat.modules.message.application.dto.request.ForwardMessageRequest;
import com.example.chat.modules.message.application.dto.request.SendMessageRequest;
import com.example.chat.modules.message.application.dto.response.MessageResponse;
import com.example.common.web.controller.BaseController;
import com.example.common.web.response.ApiResponse;
import com.example.common.security.jwt.JwtHelper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/messages")
@RequiredArgsConstructor
public class MessageCommandController extends BaseController {

    private final IMessageCommandService messageCommandService;

    @PostMapping
    public ResponseEntity<ApiResponse<MessageResponse>> sendMessage(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody @Valid SendMessageRequest request
    ) {
        request.setSenderId(JwtHelper.extractUserId(jwt));
        MessageResponse response =
                messageCommandService.sendMessage(request);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/{messageId}")
    public ResponseEntity<ApiResponse<MessageResponse>> editMessage(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID messageId,
            @RequestBody @Valid EditMessageRequest request
    ) {

        request.setMessageId(messageId);
        request.setActorId(JwtHelper.extractUserId(jwt));

        MessageResponse response =
                messageCommandService.editMessage(request);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @DeleteMapping("/{messageId}")
    public ResponseEntity<ApiResponse<Void>> deleteMessage(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID messageId,
            @RequestBody @Valid DeleteMessageRequest request
    ) {
                request.setActorId(JwtHelper.extractUserId(jwt));
        request.setMessageId(messageId);
        messageCommandService.deleteMessage(request);

        return ResponseEntity.ok(ApiResponse.success());
    }

        @PostMapping("/forward")
        public ResponseEntity<ApiResponse<MessageResponse>> forwardMessage(
                        @AuthenticationPrincipal Jwt jwt,
                        @RequestBody @Valid ForwardMessageRequest request
        ) {
                request.setActorId(JwtHelper.extractUserId(jwt));

                MessageResponse response =
                                messageCommandService.forwardMessage(request);

                return ResponseEntity.ok(ApiResponse.success(response));
        }
}


