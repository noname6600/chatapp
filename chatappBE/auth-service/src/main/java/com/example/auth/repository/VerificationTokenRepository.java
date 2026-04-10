package com.example.auth.repository;

import com.example.auth.entity.VerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VerificationTokenRepository extends JpaRepository<VerificationToken, UUID> {

    Optional<VerificationToken> findByTokenHash(String tokenHash);

    Optional<VerificationToken> findByAccountIdAndUsedFalse(UUID accountId);

    @Modifying
    @Query("""
            update VerificationToken vt
            set vt.used = true, vt.usedAt = :now
            where vt.id = :id and vt.used = false
            """)
    int markAsUsed(
            @Param("id") UUID id,
            @Param("now") Instant now
    );

    @Modifying
    @Query("""
            delete from VerificationToken vt
            where vt.expiresAt < :now
            """)
    int deleteExpired(@Param("now") Instant now);
}
