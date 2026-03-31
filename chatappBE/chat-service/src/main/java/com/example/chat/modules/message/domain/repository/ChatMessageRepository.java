package com.example.chat.modules.message.domain.repository;

import com.example.chat.modules.message.domain.entity.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChatMessageRepository
        extends JpaRepository<ChatMessage, UUID> {

    @Query("""
        select max(m.seq)
        from ChatMessage m
        where m.roomId = :roomId
    """)
    Long findMaxSeqByRoomId(
            @Param("roomId") UUID roomId
    );

    @Query(value = """
        SELECT * FROM (
            SELECT *
            FROM chat_messages
            WHERE room_id = :roomId
              AND deleted = false
            ORDER BY seq DESC
            LIMIT :limit
        ) sub
        ORDER BY seq ASC
    """, nativeQuery = true)
    List<ChatMessage> findLatestByRoom(
            @Param("roomId") UUID roomId,
            @Param("limit") int limit
    );

    @Query(value = """
        SELECT * FROM (
            SELECT *
            FROM chat_messages
            WHERE room_id = :roomId
              AND seq < :beforeSeq
              AND deleted = false
            ORDER BY seq DESC
            LIMIT :limit
        ) sub
        ORDER BY seq ASC
    """, nativeQuery = true)
    List<ChatMessage> findBeforeSeq(
            @Param("roomId") UUID roomId,
            @Param("beforeSeq") Long beforeSeq,
            @Param("limit") int limit
    );

    @Query("""
        select m
        from ChatMessage m
        where m.roomId = :roomId
          and m.seq between :start and :end
          and m.deleted = false
        order by m.seq asc
    """)
    List<ChatMessage> findRange(
            @Param("roomId") UUID roomId,
            @Param("start") Long start,
            @Param("end") Long end
    );

    @Query("""
        select m
        from ChatMessage m
        where m.roomId in :roomIds
          and m.deleted = false
          and m.seq = (
            select max(m2.seq)
            from ChatMessage m2
            where m2.roomId = m.roomId
              and m2.deleted = false
        )
    """)
    List<ChatMessage> findLastMessages(
            @Param("roomIds") Collection<UUID> roomIds
    );

    Optional<ChatMessage> findByRoomIdAndClientMessageId(
            UUID roomId,
            String clientMessageId
    );
}
