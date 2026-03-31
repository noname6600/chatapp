package com.example.chat.modules.message.application.query;

import com.example.chat.modules.message.application.dto.response.MessagePage;
import com.example.chat.modules.message.application.dto.response.MessageResponse;
import com.example.chat.modules.message.application.mapper.MessageMapper;
import com.example.chat.modules.message.domain.entity.ChatMessage;
import com.example.chat.modules.message.domain.enums.MessageType;
import com.example.chat.modules.message.domain.repository.ChatAttachmentRepository;
import com.example.chat.modules.message.domain.repository.ChatMessageRepository;
import com.example.chat.modules.message.domain.repository.ChatReactionRepository;
import com.example.chat.modules.message.domain.repository.projection.MessageReactionSummaryProjection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageQueryServiceTest {

    @Mock
    private ChatMessageRepository messageRepository;

    @Mock
    private ChatAttachmentRepository attachmentRepository;

    @Mock
    private ChatReactionRepository reactionRepository;

    @Mock
    private MessageMapper mapper;

    @InjectMocks
    private MessageQueryService service;

    @Test
    void getLatestMessages_passesCurrentUserIdToReactionSummaryQuery() {
        UUID currentUserId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();

        ChatMessage message = ChatMessage.builder()
                .id(UUID.randomUUID())
                .roomId(roomId)
                .senderId(UUID.randomUUID())
                .seq(10L)
                .type(MessageType.TEXT)
                .content("hello")
                .deleted(false)
                .build();

        MessageReactionSummaryProjection projection = summaryProjection(
                message.getId(),
                "🔥",
                1L,
                true
        );

        MessageResponse mapped = MessageResponse.builder()
                .messageId(message.getId())
                .roomId(roomId)
                .reactions(List.of())
                .build();

        when(messageRepository.findLatestByRoom(roomId, 51)).thenReturn(List.of(message));
        when(attachmentRepository.findByMessageIdIn(anyList())).thenReturn(List.of());
        when(reactionRepository.summarizeReactionsForMessages(anyList(), eq(currentUserId)))
                .thenReturn(List.of(projection));
        when(mapper.toResponse(eq(message), anyList(), ArgumentMatchers.<MessageReactionSummaryProjection>anyList()))
                .thenReturn(mapped);

        MessagePage page = service.getLatestMessages(currentUserId, roomId, 50);

        verify(reactionRepository).summarizeReactionsForMessages(anyList(), eq(currentUserId));
        assertThat(page.getMessages()).hasSize(1);
        assertThat(page.getMessages().get(0).getMessageId()).isEqualTo(message.getId());
    }

    @Test
    void getMessagesBefore_passesCurrentUserIdToReactionSummaryQuery() {
        UUID currentUserId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();

        ChatMessage message = ChatMessage.builder()
                .id(UUID.randomUUID())
                .roomId(roomId)
                .senderId(UUID.randomUUID())
                .seq(9L)
                .type(MessageType.TEXT)
                .content("older")
                .deleted(false)
                .build();

        MessageResponse mapped = MessageResponse.builder()
                .messageId(message.getId())
                .roomId(roomId)
                .reactions(List.of())
                .build();

        when(messageRepository.findBeforeSeq(roomId, 100L, 51)).thenReturn(List.of(message));
        when(attachmentRepository.findByMessageIdIn(anyList())).thenReturn(List.of());
        when(reactionRepository.summarizeReactionsForMessages(anyList(), eq(currentUserId)))
                .thenReturn(List.of());
        when(mapper.toResponse(eq(message), anyList(), ArgumentMatchers.<MessageReactionSummaryProjection>anyList()))
                .thenReturn(mapped);

        MessagePage page = service.getMessagesBefore(currentUserId, roomId, 100L, 50);

        verify(reactionRepository).summarizeReactionsForMessages(anyList(), eq(currentUserId));
        assertThat(page.getMessages()).hasSize(1);
        assertThat(page.getMessages().get(0).getMessageId()).isEqualTo(message.getId());
    }

    @Test
    void getLatestMessages_whenHasMore_keepsNewestTailInsteadOfDroppingLatest() {
        UUID currentUserId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();

        List<ChatMessage> messages = LongStream.rangeClosed(140, 190)
                .mapToObj(seq -> ChatMessage.builder()
                        .id(UUID.nameUUIDFromBytes(("m-" + seq).getBytes()))
                        .roomId(roomId)
                        .senderId(UUID.randomUUID())
                        .seq(seq)
                        .type(MessageType.TEXT)
                        .content("m" + seq)
                        .deleted(false)
                        .build())
                .toList();

        when(messageRepository.findLatestByRoom(roomId, 51)).thenReturn(messages);
        when(attachmentRepository.findByMessageIdIn(anyList())).thenReturn(List.of());
        when(reactionRepository.summarizeReactionsForMessages(anyList(), eq(currentUserId)))
                .thenReturn(List.of());
        when(mapper.toResponse(ArgumentMatchers.any(ChatMessage.class), anyList(), ArgumentMatchers.<MessageReactionSummaryProjection>anyList()))
                .thenAnswer(invocation -> {
                    ChatMessage m = invocation.getArgument(0);
                    return MessageResponse.builder()
                            .messageId(m.getId())
                            .roomId(m.getRoomId())
                            .build();
                });

        MessagePage page = service.getLatestMessages(currentUserId, roomId, 50);

        assertThat(page.getMessages()).hasSize(50);
        assertThat(page.isHasMore()).isTrue();
        // expected kept window is seq 141..190 (drops only oldest extra 140)
        verify(mapper).toResponse(eq(messages.get(1)), anyList(), ArgumentMatchers.<MessageReactionSummaryProjection>anyList());
        verify(mapper).toResponse(eq(messages.get(50)), anyList(), ArgumentMatchers.<MessageReactionSummaryProjection>anyList());
    }

    @Test
    void getMessagesBefore_whenHasMore_keepsNearestOlderTailInsteadOfDroppingNewestInWindow() {
        UUID currentUserId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();

        List<ChatMessage> messages = LongStream.rangeClosed(50, 100)
                .mapToObj(seq -> ChatMessage.builder()
                        .id(UUID.nameUUIDFromBytes(("b-" + seq).getBytes()))
                        .roomId(roomId)
                        .senderId(UUID.randomUUID())
                        .seq(seq)
                        .type(MessageType.TEXT)
                        .content("b" + seq)
                        .deleted(false)
                        .build())
                .toList();

        when(messageRepository.findBeforeSeq(roomId, 101L, 51)).thenReturn(messages);
        when(attachmentRepository.findByMessageIdIn(anyList())).thenReturn(List.of());
        when(reactionRepository.summarizeReactionsForMessages(anyList(), eq(currentUserId)))
                .thenReturn(List.of());
        when(mapper.toResponse(ArgumentMatchers.any(ChatMessage.class), anyList(), ArgumentMatchers.<MessageReactionSummaryProjection>anyList()))
                .thenAnswer(invocation -> {
                    ChatMessage m = invocation.getArgument(0);
                    return MessageResponse.builder()
                            .messageId(m.getId())
                            .roomId(m.getRoomId())
                            .build();
                });

        MessagePage page = service.getMessagesBefore(currentUserId, roomId, 101L, 50);

        assertThat(page.getMessages()).hasSize(50);
        assertThat(page.isHasMore()).isTrue();
        // expected kept window is seq 51..100 (drops only oldest extra 50)
        verify(mapper).toResponse(eq(messages.get(1)), anyList(), ArgumentMatchers.<MessageReactionSummaryProjection>anyList());
        verify(mapper).toResponse(eq(messages.get(50)), anyList(), ArgumentMatchers.<MessageReactionSummaryProjection>anyList());
    }

    private MessageReactionSummaryProjection summaryProjection(
            UUID messageId,
            String emoji,
            Long count,
            Boolean reactedByMe
    ) {
        return new MessageReactionSummaryProjection() {
            @Override
            public UUID getMessageId() {
                return messageId;
            }

            @Override
            public String getEmoji() {
                return emoji;
            }

            @Override
            public Long getCount() {
                return count;
            }

            @Override
            public Boolean getReactedByMe() {
                return reactedByMe;
            }
        };
    }
}
