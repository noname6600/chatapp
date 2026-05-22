package com.chatweb.chat.modules.room.service;

import com.chatweb.chat.modules.room.dto.RoomAvatarMetadataRequest;
import com.chatweb.chat.modules.room.dto.RoomAvatarUploadResponse;
import com.chatweb.chat.modules.room.dto.RoomResponse;
import java.time.Instant;
import java.util.UUID;

public interface IRoomService {

    RoomResponse createRoom(UUID creatorId, String name);

    void joinByCode(UUID userId, String code);

        void joinByInviteRoomId(UUID userId, UUID roomId);

    void leaveRoom(UUID roomId, UUID userId);

    RoomResponse renameRoom(UUID roomId, UUID userId, String newName);

    String getRoomCode(UUID roomId);

    /**
     * Canonical metadata-based avatar upload (from upload-service confirm flow).
     * Validates that publicId matches room_avatars/ folder policy.
     */
    RoomAvatarUploadResponse applyAvatarMetadata(UUID roomId, UUID userId, RoomAvatarMetadataRequest request);

    void markRoomRead(UUID roomId, UUID userId);

    void addMember(UUID roomId, UUID ownerId, UUID newUserId);

    void removeMember(UUID roomId, UUID ownerId, UUID targetUser);

        void banMember(UUID roomId, UUID ownerId, UUID targetUser);

        void unbanMember(UUID roomId, UUID ownerId, UUID targetUser);

        void transferOwnership(UUID roomId, UUID ownerId, UUID newOwnerId);

        void bulkBanMembers(UUID roomId, UUID ownerId, java.util.List<UUID> targetUsers);

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