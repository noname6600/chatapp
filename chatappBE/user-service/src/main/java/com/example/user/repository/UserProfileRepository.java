package com.example.user.repository;

import com.example.user.entity.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserProfileRepository extends JpaRepository<UserProfile, UUID> {
    Optional<UserProfile> findByUsername(String username);
    Optional<UserProfile> findByUsernameIgnoreCase(String username);
    boolean existsByUsername(String username);

    @Query("SELECT u FROM UserProfile u WHERE u.accountId IN :ids")
    List<UserProfile> findAllByAccountIds(@Param("ids") List<UUID> ids);
}



