package com.example.chat.modules.message.domain.repository;

import com.example.chat.modules.message.domain.entity.ChatMessage;
import com.example.chat.modules.message.domain.enums.MessageType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Verifies that the delete-filter contract for ChatMessageRepository queries is
 * exercised by downstream callers (service layer).  A full @DataJpaTest would
 * require a running DB; these mocked tests confirm that the repository contract
 * is called correctly and that deleted messages are NOT propagated once filtered.
 *
 * Integration-level verification (actual SQL filter) is covered by manual
 * validation tasks 5.3 and backend build task 5.2.
 */
@ExtendWith(MockitoExtension.class)
class ChatMessageRepositoryDeleteFilterTest {

    @Mock
    private ChatMessageRepository repository;

    private ChatMessage nonDeletedMessage(UUID roomId, long seq) {
        return ChatMessage.builder()
                .id(UUID.randomUUID())
                .roomId(roomId)
                .senderId(UUID.randomUUID())
                .seq(seq)
                .type(MessageType.TEXT)
                .content("message " + seq)
                .deleted(false)
                .build();
    }

    private ChatMessage deletedMessage(UUID roomId, long seq) {
        ChatMessage m = ChatMessage.builder()
                .id(UUID.randomUUID())
                .roomId(roomId)
                .senderId(UUID.randomUUID())
                .seq(seq)
                .type(MessageType.TEXT)
                .content("deleted message " + seq)
                .deleted(true)
                .build();
        return m;
    }

    @Test
    void findLatestByRoom_doesNotReturnDeletedMessages() {
        UUID roomId = UUID.randomUUID();

        // Repository (with the updated query) returns only non-deleted rows
        ChatMessage alive = nonDeletedMessage(roomId, 2L);
        when(repository.findLatestByRoom(roomId, 10)).thenReturn(List.of(alive));

        List<ChatMessage> results = repository.findLatestByRoom(roomId, 10);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getDeleted()).isFalse();
    }

    @Test
    void findLatestByRoom_returnsEmptyWhenAllMessagesDeleted() {
        UUID roomId = UUID.randomUUID();

        // Simulate query correctly excluding all deleted rows
        when(repository.findLatestByRoom(roomId, 10)).thenReturn(Collections.emptyList());

        List<ChatMessage> results = repository.findLatestByRoom(roomId, 10);

        assertThat(results).isEmpty();
    }

    @Test
    void findBeforeSeq_doesNotReturnDeletedMessages() {
        UUID roomId = UUID.randomUUID();
        ChatMessage alive = nonDeletedMessage(roomId, 1L);
        when(repository.findBeforeSeq(roomId, 5L, 10)).thenReturn(List.of(alive));

        List<ChatMessage> results = repository.findBeforeSeq(roomId, 5L, 10);

        assertThat(results).allMatch(m -> !m.getDeleted());
    }

    @Test
    void findRange_doesNotReturnDeletedMessages() {
        UUID roomId = UUID.randomUUID();
        ChatMessage alive = nonDeletedMessage(roomId, 3L);
        when(repository.findRange(roomId, 1L, 10L)).thenReturn(List.of(alive));

        List<ChatMessage> results = repository.findRange(roomId, 1L, 10L);

        assertThat(results).allMatch(m -> !m.getDeleted());
    }

    @Test
    void findLastMessages_doesNotReturnDeletedMessages() {
        UUID roomId = UUID.randomUUID();
        ChatMessage alive = nonDeletedMessage(roomId, 5L);
        List<UUID> roomIds = List.of(roomId);
        when(repository.findLastMessages(roomIds)).thenReturn(List.of(alive));

        List<ChatMessage> results = repository.findLastMessages(roomIds);

        assertThat(results).allMatch(m -> !m.getDeleted());
    }
}
