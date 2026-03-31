package com.example.auth.entity;

import com.example.auth.enums.AuthProvider;
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
        name = "identity_providers",
        uniqueConstraints = {

                @UniqueConstraint(
                        name = "uk_identity_provider_provider_user",
                        columnNames = {"provider", "provider_user_id"}
                ),
                @UniqueConstraint(
                        name = "uk_identity_provider_account_provider",
                        columnNames = {"account_id", "provider"}
                )
        },
        indexes = {
                @Index(
                        name = "idx_identity_provider_account",
                        columnList = "account_id"
                ),
                @Index(
                        name = "idx_identity_provider_provider",
                        columnList = "provider"
                )
        }
)
public class IdentityProvider {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AuthProvider provider;

    @Column(name = "provider_user_id", nullable = false, length = 191)
    private String providerUserId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "account_id",
            nullable = false,
            foreignKey = @ForeignKey(
                    name = "fk_identity_provider_account"
            )
    )
    private Account account;

    @Column(nullable = false, updatable = false)
    private Instant linkedAt;


    @PrePersist
    protected void onCreate() {
        if (this.linkedAt == null) {
            this.linkedAt = Instant.now();
        }
    }
}

