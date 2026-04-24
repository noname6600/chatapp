package com.example.notification.repository;

import com.example.notification.entity.Notification;
import com.example.notification.entity.NotificationType;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.time.Instant;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    List<Notification> findByUserIdAndIsReadFalse(UUID userId);

    @Query("""
        SELECT n
        FROM Notification n
        WHERE n.userId = :userId
        ORDER BY
          CASE
            WHEN n.type = com.example.notification.entity.NotificationType.FRIEND_REQUEST AND n.isRead = false THEN 0
            WHEN n.actionRequired = true AND n.isRead = false THEN 1
            ELSE 2
          END,
          n.createdAt DESC,
          n.id DESC
    """)
    List<Notification> findInboxOrdered(UUID userId, Pageable pageable);

    @Query("""
        SELECT n
        FROM Notification n
        WHERE n.userId = :userId
          AND (:beforeCreatedAt IS NULL OR n.createdAt <= :beforeCreatedAt)
        ORDER BY
          CASE
            WHEN n.type = com.example.notification.entity.NotificationType.FRIEND_REQUEST AND n.isRead = false THEN 0
            WHEN n.actionRequired = true AND n.isRead = false THEN 1
            ELSE 2
          END,
          n.createdAt DESC,
          n.id DESC
    """)
    List<Notification> findInboxOrderedBefore(UUID userId, Instant beforeCreatedAt, Pageable pageable);

    @Query("""
        SELECT n
        FROM Notification n
        WHERE n.userId = :userId
        ORDER BY
          CASE
            WHEN n.type = com.example.notification.entity.NotificationType.FRIEND_REQUEST AND n.isRead = false THEN 0
            WHEN n.actionRequired = true AND n.isRead = false THEN 1
            ELSE 2
          END,
          n.createdAt DESC,
          n.id DESC
    """)
    List<Notification> findInboxOrdered(UUID userId);

    List<Notification> findByUserIdOrderByCreatedAtAsc(UUID userId);

    long countByUserIdAndIsReadFalse(UUID userId);

    long countByUserIdAndIsReadFalseAndActionRequiredFalse(UUID userId);

    Optional<Notification> findFirstByUserIdAndTypeAndReferenceIdAndIsReadFalse(
            UUID userId,
            NotificationType type,
            UUID referenceId
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
        UPDATE Notification n
        SET n.isRead = true
        WHERE n.userId = :userId
          AND n.isRead = false
          AND n.type <> com.example.notification.entity.NotificationType.FRIEND_REQUEST
    """)
    int markAllReadByUserId(@Param("userId") UUID userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
      UPDATE Notification n
      SET n.isRead = true
      WHERE n.userId = :userId
        AND n.roomId = :roomId
        AND n.isRead = false
    """)
    int markReadByUserIdAndRoomId(@Param("userId") UUID userId, @Param("roomId") UUID roomId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
        UPDATE Notification n
        SET n.isRead = true
        WHERE n.userId = :userId
          AND n.roomId = :roomId
          AND n.isRead = false
          AND n.actionRequired = false
    """)
    int clearRoomByUserId(@Param("userId") UUID userId, @Param("roomId") UUID roomId);
}



