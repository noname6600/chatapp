package com.example.chat.modules.message.application.mapper;

import com.example.chat.modules.message.application.dto.response.MessageResponse;
import com.example.chat.modules.message.domain.entity.ChatMessage;
import com.example.chat.modules.message.domain.enums.MessageType;
import com.example.chat.modules.message.domain.repository.projection.MessageReactionSummaryProjection;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MessageMapperTest {

        private final MessageMapper mapper = new MessageMapper(
                        new MessageBlockMapper(new ObjectMapper())
        );

    @Test
    void toResponse_mapsClientMessageIdToResponse() {
        String clientMessageId = "client-xyz-99";
        ChatMessage message = ChatMessage.builder()
                .id(UUID.randomUUID())
                .roomId(UUID.randomUUID())
                .senderId(UUID.randomUUID())
                .seq(1L)
                .type(MessageType.TEXT)
                .content("hi")
                .clientMessageId(clientMessageId)
                .deleted(false)
                .build();

        MessageResponse response = mapper.toResponse(
                message,
                Collections.emptyList(),
                Collections.emptyList()
        );

        assertThat(response.getClientMessageId()).isEqualTo(clientMessageId);
    }

    @Test
    void toResponse_withNullClientMessageId_returnsNullClientMessageId() {
        ChatMessage message = ChatMessage.builder()
                .id(UUID.randomUUID())
                .roomId(UUID.randomUUID())
                .senderId(UUID.randomUUID())
                .seq(1L)
                .type(MessageType.TEXT)
                .content("hi")
                .deleted(false)
                .build();

        MessageResponse response = mapper.toResponse(
                message,
                Collections.emptyList(),
                Collections.emptyList()
        );

        assertThat(response.getClientMessageId()).isNull();
    }

        @Test
        void toResponse_mapsBlocksFromBlocksJson() {
                ChatMessage message = ChatMessage.builder()
                                .id(UUID.randomUUID())
                                .roomId(UUID.randomUUID())
                                .senderId(UUID.randomUUID())
                                .seq(1L)
                                .type(MessageType.MIXED)
                                .content("A [Image]")
                                .blocksJson("[{\"type\":\"TEXT\",\"text\":\"A\"},{\"type\":\"ASSET\",\"attachment\":{\"type\":\"IMAGE\",\"url\":\"https://example.com/a.jpg\",\"fileName\":\"a.jpg\"}}]")
                                .deleted(false)
                                .build();

                MessageResponse response = mapper.toResponse(
                                message,
                                Collections.emptyList(),
                                Collections.emptyList()
                );

                assertThat(response.getBlocks()).hasSize(2);
                assertThat(response.getBlocks().get(0).getType()).isNotNull();
                assertThat(response.getBlocks().get(0).getText()).isEqualTo("A");
                assertThat(response.getBlocks().get(1).getAttachment()).isNotNull();
                assertThat(response.getBlocks().get(1).getAttachment().getUrl()).isEqualTo("https://example.com/a.jpg");
        }

        @Test
        void toResponse_mapsReactionOwnershipTrue() {
                ChatMessage message = ChatMessage.builder()
                                .id(UUID.randomUUID())
                                .roomId(UUID.randomUUID())
                                .senderId(UUID.randomUUID())
                                .seq(1L)
                                .type(MessageType.TEXT)
                                .content("hi")
                                .deleted(false)
                                .build();

                MessageReactionSummaryProjection reaction = summaryProjection(
                                message.getId(),
                                "🔥",
                                2L,
                                true
                );

                MessageResponse response = mapper.toResponse(
                                message,
                                Collections.emptyList(),
                                List.of(reaction)
                );

                assertThat(response.getReactions()).hasSize(1);
                assertThat(response.getReactions().get(0).getEmoji()).isEqualTo("🔥");
                assertThat(response.getReactions().get(0).getCount()).isEqualTo(2L);
                assertThat(response.getReactions().get(0).isReactedByMe()).isTrue();
        }

        @Test
        void toResponse_mapsReactionOwnershipFalseWhenNull() {
                ChatMessage message = ChatMessage.builder()
                                .id(UUID.randomUUID())
                                .roomId(UUID.randomUUID())
                                .senderId(UUID.randomUUID())
                                .seq(1L)
                                .type(MessageType.TEXT)
                                .content("hi")
                                .deleted(false)
                                .build();

                MessageReactionSummaryProjection reaction = summaryProjection(
                                message.getId(),
                                "👍",
                                3L,
                                null
                );

                MessageResponse response = mapper.toResponse(
                                message,
                                Collections.emptyList(),
                                List.of(reaction)
                );

                assertThat(response.getReactions()).hasSize(1);
                assertThat(response.getReactions().get(0).getEmoji()).isEqualTo("👍");
                assertThat(response.getReactions().get(0).getCount()).isEqualTo(3L);
                assertThat(response.getReactions().get(0).isReactedByMe()).isFalse();
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
