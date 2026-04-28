package com.example.chat.modules.room.repository;

import com.example.chat.modules.room.entity.RoomBan;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Repository
public interface RoomBanRepository extends JpaRepository<RoomBan, UUID> {

    boolean existsByRoomIdAndUserId(UUID roomId, UUID userId);

        @Modifying
        @Transactional
        @Query("""
                delete from RoomBan rb
                where rb.roomId = :roomId
                    and rb.userId = :userId
        """)
        int deleteByRoomIdAndUserId(UUID roomId, UUID userId);

    Page<RoomBan> findByRoomId(UUID roomId, Pageable pageable);
}
