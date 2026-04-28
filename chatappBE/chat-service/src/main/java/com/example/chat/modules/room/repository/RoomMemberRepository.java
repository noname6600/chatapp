package com.example.chat.modules.room.repository;

import com.example.chat.modules.room.entity.RoomMember;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Repository
public interface RoomMemberRepository extends JpaRepository<RoomMember, UUID> {

    boolean existsByRoomIdAndUserId(UUID roomId, UUID userId);

    Optional<RoomMember> findByRoomIdAndUserId(UUID roomId, UUID userId);

    List<RoomMember> findByRoomId(UUID roomId);

    Page<RoomMember> findByRoomId(UUID roomId, Pageable pageable);

    Page<RoomMember> findByRoomIdAndDisplayNameContainingIgnoreCase(UUID roomId, String query, Pageable pageable);

    List<RoomMember> findByRoomIdIn(List<UUID> roomIds);

    @Query("""
        select rm.roomId
        from RoomMember rm
        where rm.userId = :userId
    """)
    List<UUID> findRoomIdsByUserId(UUID userId);

    @Query("""
        select rm.userId
        from RoomMember rm
        where rm.roomId = :roomId
    """)
    List<UUID> findUserIdsByRoomId(UUID roomId);

    long countByRoomId(UUID roomId);

    @Modifying
    @Transactional
    @Query("""
        update RoomMember rm
        set rm.lastReadSeq = :seq
        where rm.roomId = :roomId
          and rm.userId = :userId
          and rm.lastReadSeq < :seq
    """)
    int markAsRead(UUID roomId, UUID userId, Long seq);

    @Modifying
    @Transactional
        @Query("""
                delete from RoomMember rm
                where rm.roomId = :roomId
                    and rm.userId = :userId
        """)
        int deleteByRoomIdAndUserId(UUID roomId, UUID userId);

}