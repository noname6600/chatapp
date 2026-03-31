package com.example.chat.modules.room.repository;

import com.example.chat.modules.room.entity.PrivateRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PrivateRoomRepository extends JpaRepository<PrivateRoom, UUID> {

    Optional<PrivateRoom> findByUser1IdAndUser2Id(UUID user1Id, UUID user2Id);
    boolean existsByRoomIdAndUser1Id(UUID roomId, UUID userId);

    boolean existsByRoomIdAndUser2Id(UUID roomId, UUID userId);
}
