package com.example.chat.modules.message.domain.model;

import com.example.chat.modules.message.domain.enums.AttachmentType;
import com.example.chat.modules.message.domain.enums.MessageType;
import com.example.common.core.exception.BusinessException;
import com.example.common.core.exception.CommonErrorCode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MessageAggregateEditValidationTest {

    @Test
        void editText_allowsStructuredMixedMessageAndNormalizesBlocks() {
        UUID senderId = UUID.randomUUID();

        AttachmentDraft imageDraft = AttachmentDraft.builder()
                .type(AttachmentType.IMAGE)
                .url("https://res.cloudinary.com/demo/image/upload/v1/chat/attachments/a.jpg")
                .publicId("chat/attachments/a")
                .size(1024L)
                .width(200)
                .height(120)
                .build();

        MessageAggregate aggregate = MessageAggregate.create(
                UUID.randomUUID(),
                senderId,
                1L,
                "text + image",
                null,
                List.of(imageDraft),
                "client-1",
                null
        );

        aggregate.getMessage().setBlocksJson("[{\"type\":\"ASSET\"}]");

        assertThat(aggregate.getMessage().getType()).isEqualTo(MessageType.MIXED);

        aggregate.editText(senderId, "edited text");

        assertThat(aggregate.getMessage().getContent()).isEqualTo("edited text");
        assertThat(aggregate.getMessage().getBlocksJson()).isNull();
        assertThat(aggregate.getMessage().getType()).isEqualTo(MessageType.MIXED);
    }

    @Test
    void editText_allowsStaleMixedMessageWithoutAttachments() {
        UUID senderId = UUID.randomUUID();

        MessageAggregate aggregate = MessageAggregate.create(
                UUID.randomUUID(),
                senderId,
                2L,
                "legacy content",
                null,
                List.of(),
                "client-2",
                null
        );

        aggregate.getMessage().setType(MessageType.MIXED);

        aggregate.editText(senderId, "edited legacy content");

        assertThat(aggregate.getMessage().getContent()).isEqualTo("edited legacy content");
        assertThat(aggregate.getMessage().getType()).isEqualTo(MessageType.TEXT);
        assertThat(aggregate.getMessage().getEditedAt()).isNotNull();
    }

        @Test
        void editText_allowsLegacyMixedMessageWithAttachmentsWhenNotStructured() {
                UUID senderId = UUID.randomUUID();

                AttachmentDraft imageDraft = AttachmentDraft.builder()
                                .type(AttachmentType.IMAGE)
                                .url("https://res.cloudinary.com/demo/image/upload/v1/chat/attachments/b.jpg")
                                .publicId("chat/attachments/b")
                                .size(1024L)
                                .width(200)
                                .height(120)
                                .build();

                MessageAggregate aggregate = MessageAggregate.create(
                                UUID.randomUUID(),
                                senderId,
                                3L,
                                "legacy mixed",
                                null,
                                List.of(imageDraft),
                                "client-3",
                                null
                );

                aggregate.editText(senderId, "legacy mixed edited");

                assertThat(aggregate.getMessage().getContent()).isEqualTo("legacy mixed edited");
                assertThat(aggregate.getMessage().getType()).isEqualTo(MessageType.MIXED);
                assertThat(aggregate.getMessage().getEditedAt()).isNotNull();
        }

    @Test
    void editText_preservesBlocksJsonWhenProvided() {
        UUID senderId = UUID.randomUUID();

        MessageAggregate aggregate = MessageAggregate.create(
                UUID.randomUUID(),
                senderId,
                4L,
                "before",
                null,
                List.of(),
                "client-4",
                null
        );

        String blocksJson = "[{\"type\":\"TEXT\",\"text\":\"hello\"},{\"type\":\"ASSET\",\"attachment\":{\"type\":\"IMAGE\",\"url\":\"https://example.com/a.jpg\"}}]";

        aggregate.editText(senderId, "hello", blocksJson);

        assertThat(aggregate.getMessage().getBlocksJson()).isEqualTo(blocksJson);
        assertThat(aggregate.getMessage().getType()).isEqualTo(MessageType.MIXED);
    }
}


