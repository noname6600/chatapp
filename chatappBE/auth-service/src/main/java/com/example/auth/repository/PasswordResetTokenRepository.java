package com.example.auth.repository;

import com.example.auth.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    Optional<PasswordResetToken> findByAccountIdAndUsedFalse(UUID accountId);

    @Modifying
    @Query("""
            update PasswordResetToken prt
            set prt.used = true, prt.usedAt = :now
            where prt.id = :id and prt.used = false
            """)
    int markAsUsed(
            @Param("id") UUID id,
            @Param("now") Instant now
    );

    @Modifying
    @Query("""
            delete from PasswordResetToken prt
            where prt.expiresAt < :now
            """)
    int deleteExpired(@Param("now") Instant now);
}
