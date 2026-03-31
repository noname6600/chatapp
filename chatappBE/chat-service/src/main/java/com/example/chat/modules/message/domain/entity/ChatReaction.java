package com.example.chat.modules.message.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "chat_reactions",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uniq_user_message_emoji",
                        columnNames = {"messageId", "userId", "emoji"}
                )
        },
        indexes = {
                @Index(name = "idx_reaction_message", columnList = "messageId"),
                @Index(name = "idx_reaction_message_emoji", columnList = "messageId,emoji"),
                @Index(name = "idx_reaction_user", columnList = "userId")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatReaction {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private UUID messageId;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 32)
    private String emoji;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {

        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}