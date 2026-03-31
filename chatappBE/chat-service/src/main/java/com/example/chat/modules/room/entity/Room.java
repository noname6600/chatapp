package com.example.chat.modules.room.entity;

import com.example.chat.modules.room.enums.RoomType;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "rooms",
        indexes = {
                @Index(name = "idx_rooms_last_message_at", columnList = "lastMessageAt"),
                @Index(name = "idx_rooms_created_at", columnList = "createdAt"),
                @Index(name = "idx_rooms_type", columnList = "type")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Room {

    @Id
    @GeneratedValue
    private UUID id;

    @Column
    private String name;

    @Column
    private String avatarUrl;

    @Column(name = "avatar_public_id")
    private String avatarPublicId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RoomType type;

    @Column(nullable = false, updatable = false)
    private UUID createdBy;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private UUID lastMessageId;

    private UUID lastMessageSenderId;

    private String lastMessageSenderName;

    @Column(length = 200)
    private String lastMessagePreview;

    private Instant lastMessageAt;

    @Column(nullable = false)
    private Long lastSeq;

    @PrePersist
    protected void onCreate() {

        if (createdAt == null) {
            createdAt = Instant.now();
        }

        if (lastSeq == null) {
            lastSeq = 0L;
        }
    }
}
