package com.example.chat.realtime.websocket.handler;

import com.example.chat.modules.message.application.command.IMessageCommandService;
import com.example.chat.modules.message.application.command.IReactionCommandService;
import com.example.chat.modules.message.application.dto.request.DeleteMessageRequest;
import com.example.chat.modules.message.application.dto.request.EditMessageRequest;
import com.example.chat.modules.message.application.dto.request.SendMessageRequest;
import com.example.chat.realtime.websocket.WsCommandType;
import com.example.chat.realtime.websocket.WsIncomingMessage;
import com.example.chat.realtime.websocket.session.ChatSessionRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;

    private final ChatSessionRegistry sessionRegistry;

    private final IMessageCommandService messageCommandService;

    private final IReactionCommandService reactionCommandService;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {

        sessionRegistry.register(session);

        UUID userId = sessionRegistry.getUserId(session);

        if (userId == null) {
            log.warn("[WS] Missing userId for session={}", session.getId());
            return;
        }

        log.info(
                "[WS] Connected user={} session={}",
                userId,
                session.getId()
        );
    }

    @Override
    protected void handleTextMessage(
            WebSocketSession session,
            TextMessage message
    ) {

        try {

            WsIncomingMessage cmd =
                    objectMapper.readValue(
                            message.getPayload(),
                            WsIncomingMessage.class
                    );

            UUID userId =
                    sessionRegistry.getUserId(session);

            if (userId == null) {
                log.warn(
                        "[WS] user not found for session={}",
                        session.getId()
                );
                return;
            }

            dispatchCommand(session, userId, cmd);

        } catch (Exception e) {

            log.warn(
                    "[WS] Invalid payload session={} payload={}",
                    session.getId(),
                    message.getPayload(),
                    e
            );
        }
    }

    private void dispatchCommand(
            WebSocketSession session,
            UUID userId,
            WsIncomingMessage cmd
    ) {

        WsCommandType type = cmd.getType();

        switch (type) {

            case JOIN -> joinRoom(session, cmd);

            case LEAVE -> leaveRoom(session, cmd);

            case SEND -> sendMessage(userId, cmd);

            case EDIT -> editMessage(userId, cmd);

            case DELETE -> deleteMessage(userId, cmd);

            case REACTION -> react(userId, cmd);


            default -> log.warn(
                    "[WS] Unsupported command {}",
                    type
            );
        }
    }

    private void joinRoom(
            WebSocketSession session,
            WsIncomingMessage cmd
    ) {

        if (cmd.getRoomId() == null) return;

        sessionRegistry.joinRoom(
                cmd.getRoomId(),
                session
        );

        log.debug(
                "[WS] session={} joined room={}",
                session.getId(),
                cmd.getRoomId()
        );
    }

    private void leaveRoom(
            WebSocketSession session,
            WsIncomingMessage cmd
    ) {

        if (cmd.getRoomId() == null) return;

        sessionRegistry.leaveRoom(
                cmd.getRoomId(),
                session
        );

        log.debug(
                "[WS] session={} left room={}",
                session.getId(),
                cmd.getRoomId()
        );
    }

    private void sendMessage(
            UUID userId,
            WsIncomingMessage cmd
    ) {

        SendMessageRequest request =
                SendMessageRequest.builder()
                        .roomId(cmd.getRoomId())
                        .senderId(userId)
                        .content(cmd.getContent())
                        .replyToMessageId(
                                cmd.getReplyToMessageId()
                        )
                        .clientMessageId(cmd.getClientMessageId())
                        .build();

        messageCommandService.sendMessage(request);
    }

    private void editMessage(
            UUID userId,
            WsIncomingMessage cmd
    ) {

        EditMessageRequest request =
                EditMessageRequest.builder()
                        .messageId(cmd.getMessageId())
                        .actorId(userId)
                        .content(cmd.getContent())
                        .build();

        messageCommandService.editMessage(request);
    }

    private void deleteMessage(
            UUID userId,
            WsIncomingMessage cmd
    ) {

        DeleteMessageRequest request =
                DeleteMessageRequest.builder()
                        .messageId(cmd.getMessageId())
                        .actorId(userId)
                        .build();

        messageCommandService.deleteMessage(request);
    }

    private void react(
            UUID userId,
            WsIncomingMessage cmd
    ) {

        reactionCommandService.toggleReaction(
                cmd.getMessageId(),
                userId,
                cmd.getReaction()
        );
    }

    @Override
    public void afterConnectionClosed(
            WebSocketSession session,
            CloseStatus status
    ) {

        sessionRegistry.unregister(session);

        log.info(
                "[WS] Disconnected session={} status={}",
                session.getId(),
                status
        );
    }
}