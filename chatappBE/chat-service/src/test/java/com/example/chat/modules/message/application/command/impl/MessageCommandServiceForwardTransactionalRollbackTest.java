package com.example.chat.modules.message.application.command.impl;

import com.example.chat.modules.message.application.dto.request.ForwardMessageRequest;
import com.example.chat.modules.message.application.dto.response.MessageResponse;
import com.example.chat.modules.message.application.mapper.MessageMapper;
import com.example.chat.modules.message.application.pipeline.delete.DeleteMessagePipeline;
import com.example.chat.modules.message.application.pipeline.edit.EditMessagePipeline;
import com.example.chat.modules.message.application.pipeline.send.SendMessagePipeline;
import com.example.chat.modules.message.application.service.IMessageEventPublisher;
import com.example.chat.modules.message.domain.entity.ChatAttachment;
import com.example.chat.modules.message.domain.entity.ChatMessage;
import com.example.chat.modules.message.domain.enums.AttachmentType;
import com.example.chat.modules.message.domain.enums.MessageType;
import com.example.chat.modules.message.domain.repository.ChatAttachmentRepository;
import com.example.chat.modules.message.domain.repository.ChatMessageRepository;
import com.example.chat.modules.room.entity.Room;
import com.example.chat.modules.room.entity.RoomMember;
import com.example.chat.modules.room.enums.Role;
import com.example.chat.modules.room.enums.RoomType;
import com.example.chat.modules.room.repository.RoomMemberRepository;
import com.example.chat.modules.room.repository.RoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@DataJpaTest
@Import(MessageCommandService.class)
@ContextConfiguration(classes = MessageCommandServiceForwardTransactionalRollbackTest.TestApplication.class)
class MessageCommandServiceForwardTransactionalRollbackTest {

    @Autowired
    private MessageCommandService messageCommandService;
    @Autowired
    private ChatMessageRepository messageRepository;
    @Autowired
    private ChatAttachmentRepository attachmentRepository;
    @Autowired
    private RoomMemberRepository roomMemberRepository;
    @Autowired
    private RoomRepository roomRepository;

    @MockBean
    private SendMessagePipeline sendPipeline;
    @MockBean
    private EditMessagePipeline editPipeline;
    @MockBean
    private DeleteMessagePipeline deletePipeline;
    @MockBean
    private MessageMapper mapper;
    @MockBean
    private IMessageEventPublisher messageEventPublisher;

    @BeforeEach
    void setUp() {
        when(mapper.toResponse(any(ChatMessage.class), any(List.class), any(List.class)))
                .thenAnswer(invocation -> {
                    ChatMessage message = invocation.getArgument(0);
                    return MessageResponse.builder()
                            .messageId(message.getId())
                            .roomId(message.getRoomId())
                            .senderId(message.getSenderId())
                            .seq(message.getSeq())
                            .type(message.getType())
                            .content(message.getContent())
                            .forwardedFromMessageId(message.getForwardedFromMessageId())
                            .build();
                });
    }

    @Test
        @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void forwardMessage_rollsBack_whenPublishFails() {
        UUID actorId = UUID.randomUUID();
        UUID sourceRoomId = createRoomId();
        UUID targetRoomId = createRoomId();
        addMember(targetRoomId, actorId);

        UUID sourceMessageId = createMessage(sourceRoomId, UUID.randomUUID(), 10L, false, MessageType.ATTACHMENT, null);
        attachmentRepository.save(ChatAttachment.builder()
                .messageId(sourceMessageId)
                .type(AttachmentType.IMAGE)
                .url("https://res.cloudinary.com/demo/image/upload/v1/chat/attachments/a.jpg")
                .publicId("chat/attachments/a")
                .width(200)
                .height(120)
                .build());

        doThrow(new RuntimeException("kafka down"))
                .when(messageEventPublisher)
                .publishMessageCreated(any(ChatMessage.class), anyList(), anyList());

        ForwardMessageRequest request = ForwardMessageRequest.builder()
                .actorId(actorId)
                .sourceMessageId(sourceMessageId)
                .targetRoomId(targetRoomId)
                .build();

        assertThatThrownBy(() -> messageCommandService.forwardMessage(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("kafka down");

        assertThat(messageRepository.findRange(targetRoomId, 1L, 100L)).isEmpty();
    }

    private UUID createRoomId() {
        Room room = roomRepository.save(Room.builder()
                .type(RoomType.GROUP)
                .name("room")
                .createdBy(UUID.randomUUID())
                .build());
        return room.getId();
    }

    private void addMember(UUID roomId, UUID userId) {
        roomMemberRepository.save(RoomMember.builder()
                .roomId(roomId)
                .userId(userId)
                .role(Role.MEMBER)
                .build());
    }

    private UUID createMessage(UUID roomId, UUID senderId, long seq, boolean deleted, MessageType type, String content) {
        ChatMessage message = messageRepository.save(ChatMessage.builder()
                .id(UUID.randomUUID())
                .roomId(roomId)
                .senderId(senderId)
                .seq(seq)
                .type(type)
                .content(content)
                .deleted(deleted)
                .build());
        return message.getId();
    }

    @SpringBootConfiguration
    @EntityScan(basePackages = "com.example.chat.modules")
    @EnableJpaRepositories(basePackages = "com.example.chat.modules")
    static class TestApplication {

        @Bean
        JwtDecoder jwtDecoder() {
            return token -> Jwt.withTokenValue(token)
                    .header("alg", "none")
                    .claim("sub", "test-user")
                    .build();
        }
    }
}
