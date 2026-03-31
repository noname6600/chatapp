package com.example.chat.modules.message.application.mapper;

import com.example.chat.modules.message.application.dto.request.AttachmentRequest;
import com.example.chat.modules.message.application.dto.request.MessageBlockRequest;
import com.example.chat.modules.message.domain.enums.AttachmentType;
import com.example.chat.modules.message.domain.enums.MessageBlockType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MessageBlockMapperTest {

    private final MessageBlockMapper mapper = new MessageBlockMapper(new ObjectMapper());

    @Test
    void buildPreviewText_includesAttachmentLabelsInOrder() {
        String encoded = mapper.encodeBlocks(List.of(
                MessageBlockRequest.builder()
                        .type(MessageBlockType.TEXT)
                        .text("Before")
                        .build(),
                MessageBlockRequest.builder()
                        .type(MessageBlockType.ASSET)
                        .attachment(AttachmentRequest.builder()
                                .type(AttachmentType.IMAGE)
                                .url("https://res.cloudinary.com/demo/image/upload/v1/a.jpg")
                                .publicId("chat/attachments/a")
                                .build())
                        .build(),
                MessageBlockRequest.builder()
                        .type(MessageBlockType.TEXT)
                        .text("After")
                        .build()
        ));

        assertThat(mapper.buildPreviewText(encoded)).isEqualTo("Before [Image] After");
    }

    @Test
    void toAttachmentDrafts_extractsOnlyAssetBlocks() {
        List<?> drafts = mapper.toAttachmentDrafts(List.of(
                MessageBlockRequest.builder()
                        .type(MessageBlockType.TEXT)
                        .text("Hello")
                        .build(),
                MessageBlockRequest.builder()
                        .type(MessageBlockType.ASSET)
                        .attachment(AttachmentRequest.builder()
                                .type(AttachmentType.FILE)
                                .url("https://res.cloudinary.com/demo/raw/upload/v1/a.pdf")
                                .publicId("chat/attachments/a")
                                .fileName("a.pdf")
                                .size(2048L)
                                .build())
                        .build()
        ));

        assertThat(drafts).hasSize(1);
    }
}