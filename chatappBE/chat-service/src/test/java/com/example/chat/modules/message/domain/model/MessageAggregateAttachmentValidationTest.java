package com.example.chat.modules.message.domain.model;

import com.example.chat.modules.message.domain.enums.AttachmentType;
import com.example.chat.modules.message.domain.enums.MessageType;
import com.example.common.web.exception.BusinessException;
import com.example.common.web.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MessageAggregateAttachmentValidationTest {

    @Test
    void create_rejectsAttachmentWithoutPublicId() {
        AttachmentDraft draft = AttachmentDraft.builder()
                .type(AttachmentType.IMAGE)
                .url("https://res.cloudinary.com/demo/image/upload/v1/chat/attachments/a.jpg")
                .publicId(null)
                .size(1024L)
                .width(100)
                .height(100)
                .build();

        assertThatThrownBy(() -> MessageAggregate.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                1L,
                null,
                null,
                List.of(draft),
                null,
                null
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ATTACHMENT_INVALID);
    }

    @Test
    void create_rejectsNonCloudinaryUrl() {
        AttachmentDraft draft = AttachmentDraft.builder()
                .type(AttachmentType.FILE)
                .url("https://example.com/file.pdf")
                .publicId("chat/attachments/a")
                .size(1024L)
                .build();

        assertThatThrownBy(() -> MessageAggregate.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                1L,
                null,
                null,
                List.of(draft),
                null,
                null
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ATTACHMENT_INVALID);
    }

    @Test
    void create_acceptsCloudinaryAttachmentMetadata() {
        AttachmentDraft draft = AttachmentDraft.builder()
                .type(AttachmentType.IMAGE)
                .url("https://res.cloudinary.com/demo/image/upload/v1/chat/attachments/a.jpg")
                .publicId("chat/attachments/a")
                .size(1024L)
                .width(200)
                .height(120)
                .build();

        MessageAggregate aggregate = MessageAggregate.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                1L,
                null,
                null,
                List.of(draft),
                null,
                null
        );

        assertThat(aggregate.getAttachments()).hasSize(1);
        assertThat(aggregate.getAttachments().get(0).getPublicId()).isEqualTo("chat/attachments/a");
    }

    @Test
    void create_assetOnlyBlocksWithAttachment_isAttachmentType() {
        AttachmentDraft draft = AttachmentDraft.builder()
                .type(AttachmentType.IMAGE)
                .url("https://res.cloudinary.com/demo/image/upload/v1/chat/attachments/a.jpg")
                .publicId("chat/attachments/a")
                .size(1024L)
                .width(200)
                .height(120)
                .build();

        MessageAggregate aggregate = MessageAggregate.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                1L,
                "",
                null,
                List.of(draft),
                null,
                "[{\"type\":\"ASSET\",\"attachment\":{\"type\":\"IMAGE\",\"url\":\"https://res.cloudinary.com/demo/image/upload/v1/chat/attachments/a.jpg\",\"publicId\":\"chat/attachments/a\",\"width\":200,\"height\":120}}]"
        );

        assertThat(aggregate.getMessage().getType()).isEqualTo(MessageType.ATTACHMENT);
        assertThat(aggregate.getAttachments()).hasSize(1);
    }

    @Test
    void create_textWithAttachmentAndBlocks_isMixedType() {
        AttachmentDraft draft = AttachmentDraft.builder()
                .type(AttachmentType.IMAGE)
                .url("https://res.cloudinary.com/demo/image/upload/v1/chat/attachments/a.jpg")
                .publicId("chat/attachments/a")
                .size(1024L)
                .width(200)
                .height(120)
                .build();

        MessageAggregate aggregate = MessageAggregate.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                1L,
                "hello",
                null,
                List.of(draft),
                null,
                "[{\"type\":\"TEXT\",\"text\":\"hello\"}]"
        );

        assertThat(aggregate.getMessage().getType()).isEqualTo(MessageType.MIXED);
        assertThat(aggregate.getAttachments()).hasSize(1);
    }

    @Test
    void create_attachmentWithoutBlocksOrContent_isAttachmentType() {
        AttachmentDraft draft = AttachmentDraft.builder()
                .type(AttachmentType.IMAGE)
                .url("https://res.cloudinary.com/demo/image/upload/v1/chat/attachments/a.jpg")
                .publicId("chat/attachments/a")
                .size(1024L)
                .width(200)
                .height(120)
                .build();

        MessageAggregate aggregate = MessageAggregate.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                1L,
                null,
                null,
                List.of(draft),
                null,
                null
        );

        assertThat(aggregate.getMessage().getType()).isEqualTo(MessageType.ATTACHMENT);
        assertThat(aggregate.getAttachments()).hasSize(1);
    }
}
