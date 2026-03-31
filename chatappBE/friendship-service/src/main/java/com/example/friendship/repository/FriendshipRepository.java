package com.example.friendship.repository;

import com.example.friendship.entity.Friendship;
import com.example.friendship.enums.FriendshipStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FriendshipRepository extends JpaRepository<Friendship, UUID> {

    @Query("""
        SELECT f FROM Friendship f
        WHERE (f.userLow = :u1 AND f.userHigh = :u2)
           OR (f.userLow = :u2 AND f.userHigh = :u1)
    """)
    Optional<Friendship> findBetweenUsers(UUID u1, UUID u2);


    @Query("""
        SELECT f FROM Friendship f
        WHERE f.status = 'ACCEPTED'
          AND (f.userLow = :userId OR f.userHigh = :userId)
    """)
    List<Friendship> findAllFriendsOf(UUID userId);



    @Query("""
        SELECT f FROM Friendship f
        WHERE f.status = 'PENDING'
          AND (f.userLow = :userId OR f.userHigh = :userId)
    """)
    List<Friendship> findAllPendingOf(UUID userId);


    @Query("""
        SELECT f FROM Friendship f
        WHERE f.status = 'BLOCKED'
          AND (f.userLow = :userId OR f.userHigh = :userId)
    """)
    List<Friendship> findAllBlockedOf(UUID userId);



    @Query("""
        SELECT (COUNT(f) > 0) FROM Friendship f
        WHERE f.status = 'BLOCKED'
          AND ((f.userLow = :u1 AND f.userHigh = :u2)
            OR (f.userLow = :u2 AND f.userHigh = :u1))
    """)
    boolean existsBlockBetween(UUID u1, UUID u2);
}



