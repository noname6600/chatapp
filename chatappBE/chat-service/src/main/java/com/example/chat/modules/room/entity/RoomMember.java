package com.example.chat.modules.room.entity;

import com.example.chat.modules.room.enums.Role;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "room_member",
        indexes = {
                @Index(name = "idx_room_member_room", columnList = "roomId"),
                @Index(name = "idx_room_member_user_room", columnList = "userId,roomId"),
                @Index(name = "idx_room_member_room_user", columnList = "roomId,userId")
        },
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"roomId", "userId"})
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomMember {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private UUID roomId;

    @Column(nullable = false)
    private UUID userId;

    @Column(length = 120)
    private String displayName;

    @Column(length = 500)
    private String avatarUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(nullable = false, updatable = false)
    private Instant joinedAt;

    private UUID lastReadMessageId;

    private Instant lastReadAt;

    @Column(nullable = false)
    private Long lastReadSeq;

    @PrePersist
    protected void onJoin() {

        if (joinedAt == null) {
            joinedAt = Instant.now();
        }

        if (lastReadAt == null) {
            lastReadAt = joinedAt;
        }

        if (lastReadSeq == null) {
            lastReadSeq = 0L;
        }
    }
}