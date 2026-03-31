package com.example.chat.modules.message.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "chat_message_mentions",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uniq_message_user_mention",
                        columnNames = {"messageId", "mentionedUserId"}
                )
        },
        indexes = {
                @Index(name = "idx_mention_message", columnList = "messageId"),
                @Index(name = "idx_mention_user", columnList = "mentionedUserId"),
                @Index(name = "idx_mention_message_user", columnList = "messageId,mentionedUserId")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessageMention {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private UUID messageId;

    @Column(nullable = false)
    private UUID mentionedUserId;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}