package com.example.auth.repository;

import com.example.auth.entity.IdentityProvider;
import com.example.auth.enums.AuthProvider;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IdentityProviderRepository
        extends JpaRepository<IdentityProvider, UUID> {

    @EntityGraph(attributePaths = "account")
    Optional<IdentityProvider> findByProviderAndProviderUserId(
            AuthProvider provider,
            String providerUserId
    );

    Optional<IdentityProvider> findByProviderAndAccount_Id(
            AuthProvider provider,
            UUID accountId
    );

    List<IdentityProvider> findByAccount_Id(UUID accountId);
}

