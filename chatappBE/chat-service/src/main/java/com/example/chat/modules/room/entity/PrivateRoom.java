package com.example.chat.modules.room.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(
        name = "private_rooms",
        indexes = {
                @Index(name = "idx_private_room_room", columnList = "roomId")
        },
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_private_room_users",
                        columnNames = {"user1Id", "user2Id"}
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PrivateRoom {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private UUID user1Id;

    @Column(nullable = false)
    private UUID user2Id;

    @Column(nullable = false, unique = true)
    private UUID roomId;
}