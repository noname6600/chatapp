package com.example.user.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "user_profiles",
        indexes = {
                @Index(name = "idx_username", columnList = "username")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserProfile {

    @Id
    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Column(nullable = false, unique = true, length = 30)
    private String username;

    @Column(name = "display_name", length = 64)
    private String displayName;

    @Column(name = "avatar_url", nullable = false, length = 255)
    private String avatarUrl;

    @Column(name = "avatar_public_id", nullable = false)
    private String avatarPublicId;

    @Column(name = "about_me", length = 160)
    private String aboutMe;

    @Column(name = "background_color", length = 7)
    private String backgroundColor;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}