package com.example.chat.modules.message.domain.repository;

import com.example.chat.modules.message.domain.entity.RoomPinnedMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoomPinnedMessageRepository
        extends JpaRepository<RoomPinnedMessage, UUID> {

    List<RoomPinnedMessage> findByRoomIdOrderByPinnedAtDesc(UUID roomId);

    Optional<RoomPinnedMessage> findByRoomIdAndMessageId(
            UUID roomId,
            UUID messageId
    );
}
