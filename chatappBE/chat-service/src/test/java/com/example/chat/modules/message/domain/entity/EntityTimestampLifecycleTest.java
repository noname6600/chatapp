package com.example.chat.modules.message.domain.entity;

import com.example.chat.modules.message.domain.enums.AttachmentType;
import com.example.chat.modules.message.domain.enums.MessageType;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EntityTimestampLifecycleTest {

    @Test
    void chatMessage_onCreate_setsCreatedAtAndDeletedDefault() {
        ChatMessage message = ChatMessage.builder()
                .id(UUID.randomUUID())
                .roomId(UUID.randomUUID())
                .senderId(UUID.randomUUID())
                .seq(1L)
                .type(MessageType.TEXT)
                .content("hello")
                .build();

        message.onCreate();

        assertThat(message.getCreatedAt()).isNotNull();
        assertThat(message.getDeleted()).isFalse();
    }

    @Test
    void chatAttachment_onCreate_setsCreatedAt() {
        ChatAttachment attachment = ChatAttachment.builder()
                .messageId(UUID.randomUUID())
                .type(AttachmentType.IMAGE)
                .url("https://cdn.local/x.png")
                .build();

        attachment.onCreate();

        assertThat(attachment.getCreatedAt()).isNotNull();
    }

    @Test
    void chatMessageMention_onCreate_setsCreatedAt() {
        ChatMessageMention mention = ChatMessageMention.builder()
                .messageId(UUID.randomUUID())
                .mentionedUserId(UUID.randomUUID())
                .build();

        mention.onCreate();

        assertThat(mention.getCreatedAt()).isNotNull();
    }

    @Test
    void chatReaction_onCreate_setsCreatedAt() {
        ChatReaction reaction = ChatReaction.builder()
                .messageId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .emoji("👍")
                .build();

        reaction.onCreate();

        assertThat(reaction.getCreatedAt()).isNotNull();
    }
}
