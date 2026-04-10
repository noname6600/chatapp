package com.example.auth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
        name = "accounts",
        indexes = {
                @Index(name = "idx_account_email", columnList = "email")
        }
)
public class  Account {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, unique = true, length = 191)
    private String email;

    @Column
    private String passwordHash;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
        if (!this.enabled) {
            this.enabled = true;
        }
        this.emailVerified = false;
    }
}



