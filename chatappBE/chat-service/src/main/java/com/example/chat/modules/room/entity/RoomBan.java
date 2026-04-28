package com.example.chat.modules.room.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "room_ban",
        indexes = {
                @Index(name = "idx_room_ban_room", columnList = "roomId"),
                @Index(name = "idx_room_ban_room_user", columnList = "roomId,userId")
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
public class RoomBan {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private UUID roomId;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private UUID bannedBy;

    @Column(nullable = false, updatable = false)
    private Instant bannedAt;

    @PrePersist
    protected void onCreate() {
        if (bannedAt == null) {
            bannedAt = Instant.now();
        }
    }
}
