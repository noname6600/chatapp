package com.example.chat.modules.message.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "room_pinned_messages",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uniq_room_message_pin",
                        columnNames = {"roomId", "messageId"}
                )
        },
        indexes = {
                @Index(name = "idx_pin_room_pinned_at", columnList = "roomId,pinnedAt")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomPinnedMessage {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID roomId;

    @Column(nullable = false)
    private UUID messageId;

    @Column(nullable = false)
    private UUID pinnedBy;

    @Column(nullable = false, updatable = false)
    private Instant pinnedAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (pinnedAt == null) {
            pinnedAt = Instant.now();
        }
    }
}
