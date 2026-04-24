package com.example.chat.modules.room.repository;

import com.example.chat.modules.room.entity.Room;
import com.example.chat.modules.room.repository.projection.RoomRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface RoomRepository extends JpaRepository<Room, UUID> {

    @Modifying
    @Transactional
    @Query("""
            update Room r
            set
                r.lastMessageId = :messageId,
                r.lastMessageSenderId = :senderId,
                r.lastMessageSenderName = :senderName,
                r.lastMessageAt = :createdAt,
                r.lastMessagePreview = :preview,
                r.lastSeq = :seq
            where r.id = :roomId
            """)
    void updateLastMessage(
            UUID roomId,
            UUID messageId,
            UUID senderId,
            String senderName,
            Instant createdAt,
            String preview,
            Long seq
    );

    @Modifying
    @Transactional
    @Query("""
            update Room r
            set
                r.lastMessageId = :messageId,
                r.lastMessageSenderId = :senderId,
                r.lastMessageSenderName = :senderName,
                r.lastMessageAt = :createdAt,
                r.lastMessagePreview = :preview,
                r.lastSeq = :seq
            where r.id = :roomId
                            and (r.lastSeq is null or r.lastSeq < :seq)
            """)
    int updateLastMessageSafe(
            UUID roomId,
            UUID messageId,
            UUID senderId,
            String senderName,
            Instant createdAt,
            String preview,
            Long seq
    );

    @Modifying
    @Transactional
    @Query("""
            update Room r
            set r.lastMessagePreview = :preview
            where r.id = :roomId
              and r.lastMessageId = :messageId
            """)
    int updatePreviewIfMatch(
            UUID roomId,
            UUID messageId,
            String preview
    );

    @Query("""
        SELECT
            r as room,
            me.role as role,
            (r.lastSeq - me.lastReadSeq) as unreadCount,

            me.userId as user1Id,
            me.displayName as user1Name,
            me.avatarUrl as user1Avatar,

            other.userId as user2Id,
            other.displayName as user2Name,
            other.avatarUrl as user2Avatar

        FROM Room r
        JOIN RoomMember me
            ON me.roomId = r.id
        LEFT JOIN RoomMember other
            ON other.roomId = r.id
            AND other.userId <> me.userId
            AND r.type = com.example.chat.modules.room.enums.RoomType.PRIVATE

        WHERE me.userId = :userId
    """)
    List<RoomRow> findRoomsOfUserAdvanced(UUID userId);
}