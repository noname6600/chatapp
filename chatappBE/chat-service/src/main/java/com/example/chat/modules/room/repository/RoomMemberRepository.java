package com.example.chat.modules.room.repository;

import com.example.chat.modules.room.entity.RoomMember;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Repository
public interface RoomMemberRepository extends JpaRepository<RoomMember, UUID> {

    boolean existsByRoomIdAndUserId(UUID roomId, UUID userId);

    Optional<RoomMember> findByRoomIdAndUserId(UUID roomId, UUID userId);

    List<RoomMember> findByRoomId(UUID roomId);

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

    @Query("""
        select rm
        from RoomMember rm
        where rm.roomId = :roomId
    """)
    List<RoomMember> findMembersOfRoom(UUID roomId);

    @Query("""
        select rm
        from RoomMember rm
        where rm.roomId in :roomIds
    """)
    List<RoomMember> findMembersOfRooms(List<UUID> roomIds);

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
    int deleteByRoomIdAndUserId(UUID roomId, UUID userId);
}