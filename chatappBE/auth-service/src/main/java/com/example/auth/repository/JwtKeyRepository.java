package com.example.auth.repository;

import com.example.auth.entity.JwtKeyEntity;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JwtKeyRepository
        extends JpaRepository<JwtKeyEntity, UUID> {

    Optional<JwtKeyEntity> findByKid(String kid);

    @Query("""
        select k
          from JwtKeyEntity k
         where k.active = true
         order by k.createdAt desc
    """)
    List<JwtKeyEntity> findActiveKeys();

    List<JwtKeyEntity> findByExpiredAtAfter(Instant now);

    @Modifying
    @Transactional
    @Query("""
        update JwtKeyEntity k
           set k.active = false
         where k.active = true
    """)
    int clearAllActive();

    @Modifying
    @Transactional
    @Query("""
        delete from JwtKeyEntity k
         where k.expiredAt < :now
    """)
    int deleteExpired(Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select k
          from JwtKeyEntity k
         where k.active = true
    """)
    Optional<JwtKeyEntity> findActiveKeyForUpdate();

    @Query("""
    select k
      from JwtKeyEntity k
     where k.active = true
     order by k.createdAt desc
""")
    Optional<JwtKeyEntity> findLatestActive();
}
