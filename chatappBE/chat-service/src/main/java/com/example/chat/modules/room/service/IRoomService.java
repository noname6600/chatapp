package com.example.chat.modules.room.service;

import com.example.chat.modules.room.dto.RoomAvatarUploadResponse;
import com.example.chat.modules.room.dto.RoomResponse;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.UUID;

public interface IRoomService {

    RoomResponse createRoom(UUID creatorId, String name);

    void joinByCode(UUID userId, String code);

    void leaveRoom(UUID roomId, UUID userId);

    RoomResponse renameRoom(UUID roomId, UUID userId, String newName);

    String getRoomCode(UUID roomId);

    RoomAvatarUploadResponse uploadAvatar(UUID roomId, UUID userId, MultipartFile file);

    void markRoomRead(UUID roomId, UUID userId);

    void addMember(UUID roomId, UUID ownerId, UUID newUserId);

    void removeMember(UUID roomId, UUID ownerId, UUID targetUser);

    void updateLastMessage(
            UUID roomId,
            UUID messageId,
            UUID senderId,
            Instant createdAt,
            String preview,
            Long seq
    );

    void updateLastMessagePreviewIfMatch(
            UUID roomId,
            UUID messageId,
            String preview
    );

    void handleMessageDeleted(
            UUID roomId,
            UUID messageId
    );
}