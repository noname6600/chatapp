package com.example.auth.repository;

import com.example.auth.entity.RefreshToken;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository
        extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHashAndRevokedFalse(String tokenHash);

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findAllByAccountIdAndRevokedFalse(UUID accountId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        delete from RefreshToken rt
        where rt.expiresAt < :now
           or rt.revoked = true
    """)
    int deleteExpiredOrRevoked(@Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update RefreshToken rt
           set rt.revoked = true,
               rt.revokedAt = :now
         where rt.accountId = :accountId
           and rt.revoked = false
    """)
    int revokeAllByAccountId(
            @Param("accountId") UUID accountId,
            @Param("now") Instant now
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update RefreshToken rt
           set rt.revoked = true,
               rt.revokedAt = :now
         where rt.tokenHash = :hash
           and rt.revoked = false
    """)
    int revokeIfNotRevoked(
            @Param("hash") String hash,
            @Param("now") Instant now
    );
}