package com.example.notification.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID userId;

    @Enumerated(EnumType.STRING)
    private NotificationType type;

    private UUID referenceId;

    private UUID roomId;

    private UUID actorId;

    private String actorDisplayName;

    private String senderName;

    @Column(length = 1000)
    private String preview;

    private boolean isRead;

    /**
     * Marks notifications that require user action. Read-all preserves unread FRIEND_REQUEST items,
     * while other action-required types can still be marked read.
     */
    private boolean actionRequired;

    private Instant createdAt;
}

