package com.example.chat.modules.message.controller;

import com.example.chat.modules.message.application.command.IReactionCommandService;
import com.example.common.web.controller.BaseController;
import com.example.common.web.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/messages/{messageId}/reactions")
@RequiredArgsConstructor
public class MessageReactionController extends BaseController {

    private final IReactionCommandService reactionService;

    @PostMapping("/{emoji}")
    public ResponseEntity<ApiResponse<Void>> toggleReaction(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID messageId,
            @PathVariable String emoji
    ) {

        reactionService.toggleReaction(
                messageId,
                currentUserId(jwt),
                emoji
        );

        return ResponseEntity.ok(ApiResponse.success());
    }
}