package com.example.chat.modules.message.domain.entity;

import com.example.chat.modules.message.domain.enums.MessageType;
import com.example.chat.modules.message.domain.enums.SystemEventType;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "chat_messages",
        indexes = {
                @Index(name = "idx_msg_room_created", columnList = "roomId,createdAt"),
                @Index(name = "idx_msg_room_id", columnList = "roomId"),
                @Index(name = "idx_msg_room_seq", columnList = "roomId,seq"),
                @Index(name = "idx_reply_to", columnList = "replyToMessageId"),
                @Index(name = "idx_message_sender", columnList = "senderId"),
                @Index(name = "idx_msg_client_id", columnList = "clientMessageId")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessage {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID roomId;

    @Column(nullable = false)
    private UUID senderId;

    @Column(nullable = false)
    private Long seq;

    @Column(length = 100)
    private String clientMessageId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MessageType type;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(columnDefinition = "TEXT")
    private String blocksJson;

    private UUID forwardedFromMessageId;

    @Enumerated(EnumType.STRING)
    private SystemEventType systemEventType;

    private UUID actorUserId;

    private UUID targetMessageId;

    private UUID replyToMessageId;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant editedAt;

    @Column(nullable = false)
    private Boolean deleted;

    private Instant deletedAt;

    private UUID deletedBy;

    @PrePersist
    void onCreate() {

        if (createdAt == null) {
            createdAt = Instant.now();
        }

        if (deleted == null) {
            deleted = false;
        }
    }
}

