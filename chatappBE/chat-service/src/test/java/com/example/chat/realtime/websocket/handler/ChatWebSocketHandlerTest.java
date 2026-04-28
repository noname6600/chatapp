package com.example.chat.realtime.websocket.handler;

import com.example.chat.modules.message.application.command.IMessageCommandService;
import com.example.chat.modules.message.application.command.IReactionCommandService;
import com.example.chat.modules.message.application.dto.request.SendMessageRequest;
import com.example.chat.realtime.websocket.session.ChatSessionRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatWebSocketHandlerTest {

    @Mock
    private ChatSessionRegistry sessionRegistry;

    @Mock
    private IMessageCommandService messageCommandService;

    @Mock
    private IReactionCommandService reactionCommandService;

    @Mock
    private WebSocketSession session;

    private ChatWebSocketHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ChatWebSocketHandler(
                new ObjectMapper(),
                sessionRegistry,
                messageCommandService,
                reactionCommandService
        );
    }

    @Test
    void handleTextMessage_sendCommand_routesToMessageCommandService() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        UUID replyTo = UUID.randomUUID();

        when(sessionRegistry.getUserId(session)).thenReturn(userId);

        String payload = """
                {
                  "type": "SEND",
                  "roomId": "%s",
                  "content": "hello",
                  "replyToMessageId": "%s",
                  "clientMessageId": "cid-123"
                }
                """.formatted(roomId, replyTo);

        handler.handleTextMessage(session, new TextMessage(payload));

        ArgumentCaptor<SendMessageRequest> requestCaptor = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(messageCommandService).sendMessage(requestCaptor.capture());

        SendMessageRequest request = requestCaptor.getValue();
        assertThat(request.getSenderId()).isEqualTo(userId);
        assertThat(request.getRoomId()).isEqualTo(roomId);
        assertThat(request.getContent()).isEqualTo("hello");
        assertThat(request.getReplyToMessageId()).isEqualTo(replyTo);
        assertThat(request.getClientMessageId()).isEqualTo("cid-123");
    }
}
