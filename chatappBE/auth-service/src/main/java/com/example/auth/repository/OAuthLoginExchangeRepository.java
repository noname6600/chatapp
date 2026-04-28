package com.example.auth.repository;

import com.example.auth.entity.OAuthLoginExchange;
import com.example.auth.enums.AuthProvider;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OAuthLoginExchangeRepository extends JpaRepository<OAuthLoginExchange, UUID> {

    Optional<OAuthLoginExchange> findByCodeHashAndProvider(String codeHash, AuthProvider provider);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
        update OAuthLoginExchange exchange
           set exchange.consumedAt = :now
         where exchange.codeHash = :codeHash
           and exchange.provider = :provider
           and exchange.consumedAt is null
           and exchange.expiresAt > :now
    """)
    int consumeIfAvailable(
            @Param("codeHash") String codeHash,
            @Param("provider") AuthProvider provider,
            @Param("now") Instant now
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
        delete from OAuthLoginExchange exchange
         where exchange.expiresAt < :now
            or exchange.consumedAt is not null
    """)
    int deleteExpiredOrConsumed(@Param("now") Instant now);
}