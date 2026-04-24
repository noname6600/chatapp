package com.example.chat.modules.room.service.impl;

import com.example.chat.config.InviteCodeGenerator;
import com.example.chat.modules.message.application.service.ISystemMessageService;
import com.example.chat.modules.message.domain.enums.SystemEventType;
import com.example.chat.modules.message.infrastructure.cache.CacheNames;
import com.example.chat.modules.message.infrastructure.client.UserBasicProfile;
import com.example.chat.modules.message.infrastructure.client.UserClient;
import com.example.chat.modules.room.dto.CloudinaryUploadResult;
import com.example.chat.modules.room.dto.LastMessagePreview;
import com.example.chat.modules.room.dto.RoomAvatarUploadResponse;
import com.example.chat.modules.room.dto.RoomMemberJoinedPayload;
import com.example.chat.modules.room.dto.RoomMemberLeftPayload;
import com.example.chat.modules.room.dto.RoomResponse;
import com.example.common.integration.chat.ChatEventType;
import com.example.common.integration.websocket.WsEvent;
import com.example.common.websocket.session.IRoomBroadcaster;
import com.example.chat.modules.room.entity.Room;
import com.example.chat.modules.room.entity.RoomMember;
import com.example.chat.modules.room.enums.Role;
import com.example.chat.modules.room.enums.RoomType;
import com.example.chat.modules.room.repository.RoomMemberRepository;
import com.example.chat.modules.room.repository.RoomRepository;
import com.example.chat.modules.room.service.IRoomService;
import com.example.common.redis.api.ITimeRedisCacheManager;
import com.example.common.redis.exception.CreateCacheException;
import com.example.common.web.exception.BusinessException;
import com.example.common.web.exception.ErrorCode;
import org.springframework.dao.DataIntegrityViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class RoomService implements IRoomService {

    private final RoomRepository roomRepo;
    private final RoomMemberRepository memberRepo;
    private final UserClient userClient;
    private final InviteCodeGenerator inviteCodeGenerator;
    private final GroupAvatarGenerator avatarGenerator;
    private final CloudinaryService cloudinaryService;
    private final ITimeRedisCacheManager cacheManager;
    private final IRoomBroadcaster roomBroadcaster;
    private final ISystemMessageService systemMessageService;

    @Override
    public RoomResponse createRoom(UUID creatorId, String name) {
        Room room = Room.builder()
                .type(RoomType.GROUP)
                .name(name)
                .avatarUrl(defaultAvatar())
                .createdBy(creatorId)
                .build();

        roomRepo.save(room);

        UserBasicProfile creatorProfile = safeGetBasicProfile(creatorId);

        RoomMember member = RoomMember.builder()
                .roomId(room.getId())
                .userId(creatorId)
                .displayName(creatorProfile != null ? creatorProfile.getDisplayName() : null)
                .avatarUrl(creatorProfile != null ? creatorProfile.getAvatarUrl() : null)
                .role(Role.OWNER)
                .build();

        memberRepo.save(member);

        try {
            room.setAvatarUrl(avatarGenerator.generate(room.getId(), name));
        } catch (Exception e) {
            log.warn("Avatar generation failed for room {}", room.getId());
        }

        evictRoomsCache(creatorId);

        return toResponse(room, member);
    }

    @Override
    @Transactional(readOnly = true)
    public String getRoomCode(UUID roomId) {
        return inviteCodeGenerator.encode(roomId);
    }

    @Override
    public void joinByCode(UUID userId, String code) {
        UUID roomId = inviteCodeGenerator.decode(code);

        joinByInviteRoomId(userId, roomId);
    }

    @Override
    public void joinByInviteRoomId(UUID userId, UUID roomId) {

        Room room = roomRepo.findById(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Room not found"));

        if (room.getType() == RoomType.PRIVATE) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Cannot join private room");
        }

        if (memberRepo.existsByRoomIdAndUserId(roomId, userId)) {
            return;
        }

        UserBasicProfile joinerProfile = safeGetBasicProfile(userId);

        RoomMember saved;
        try {
            saved = memberRepo.save(RoomMember.builder()
                .roomId(roomId)
                .userId(userId)
                .displayName(joinerProfile != null ? joinerProfile.getDisplayName() : null)
                .avatarUrl(joinerProfile != null ? joinerProfile.getAvatarUrl() : null)
                .role(Role.MEMBER)
                .build());
        } catch (DataIntegrityViolationException ex) {
            log.info(
                "Concurrent join ignored as duplicate membership roomId={}, userId={}",
                roomId,
                userId
            );
            return;
        }

        evictRoomsCache(userId);

        roomBroadcaster.sendToRoom(roomId, WsEvent.builder()
                .type(ChatEventType.MEMBER_JOINED.value())
                .payload(RoomMemberJoinedPayload.builder()
                        .roomId(roomId)
                        .userId(userId)
                        .role(Role.MEMBER.name())
                        .joinedAt(saved.getJoinedAt())
                        .build())
                .build());

                systemMessageService.sendSystemMessage(
                    roomId,
                    SystemEventType.JOIN,
                    userId,
                    null
                );
    }

    @Override
    public void leaveRoom(UUID roomId, UUID userId) {
        RoomMember leaving = memberRepo.findByRoomIdAndUserId(roomId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Membership not found"));

        memberRepo.delete(leaving);

        if (leaving.getRole() == Role.OWNER) {
            List<RoomMember> remaining = memberRepo.findByRoomId(roomId);

            if (remaining.isEmpty()) {
                roomRepo.deleteById(roomId);
                return;
            }

            RoomMember newOwner = remaining.stream()
                    .min(Comparator.comparing(RoomMember::getJoinedAt))
                    .orElseThrow();

            newOwner.setRole(Role.OWNER);
            memberRepo.save(newOwner);

            evictRoomsCache(newOwner.getUserId());
        }

        evictRoomsCache(userId);
        evictRoomMembers(roomId);

        roomBroadcaster.sendToRoom(roomId, WsEvent.builder()
                .type(ChatEventType.MEMBER_LEFT.value())
                .payload(RoomMemberLeftPayload.builder()
                        .roomId(roomId)
                        .userId(userId)
                        .build())
                .build());
    }

    @Override
    public RoomResponse renameRoom(UUID roomId, UUID userId, String newName) {
        Room room = roomRepo.findById(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Room not found"));

        RoomMember member = memberRepo.findByRoomIdAndUserId(roomId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN, "Not a member"));

        if (member.getRole() != Role.OWNER) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Only owner can rename");
        }

        room.setName(newName);

        evictRoomMembers(roomId);

        return toResponse(room, member);
    }

    @Override
    public void addMember(UUID roomId, UUID ownerId, UUID newUserId) {
        RoomMember owner = memberRepo.findByRoomIdAndUserId(roomId, ownerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN, "Not a member"));

        if (owner.getRole() != Role.OWNER) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Only owner can invite");
        }

        if (memberRepo.existsByRoomIdAndUserId(roomId, newUserId)) {
            return;
        }

        try {
            memberRepo.save(RoomMember.builder()
                .roomId(roomId)
                .userId(newUserId)
                .role(Role.MEMBER)
                .build());
        } catch (DataIntegrityViolationException ex) {
            log.info(
                "Concurrent add-member ignored as duplicate membership roomId={}, userId={}",
                roomId,
                newUserId
            );
            return;
        }

        evictRoomsCache(newUserId);
        evictRoomMembers(roomId);
    }

    @Override
    public void removeMember(UUID roomId, UUID ownerId, UUID targetUser) {
        RoomMember owner = memberRepo.findByRoomIdAndUserId(roomId, ownerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN, "Not a member"));

        if (owner.getRole() != Role.OWNER) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Only owner can remove");
        }

        memberRepo.deleteByRoomIdAndUserId(roomId, targetUser);

        evictRoomsCache(targetUser);
        evictRoomMembers(roomId);
    }

    @Override
    public void markRoomRead(UUID roomId, UUID userId) {
        Room room = roomRepo.getReferenceById(roomId);

        Long seq = room.getLastSeq();
        if (seq == null || seq == 0) return;

        memberRepo.markAsRead(roomId, userId, seq);

        evictRoomsCache(userId);
    }

    @Override
    public void updateLastMessage(
            UUID roomId,
            UUID messageId,
            UUID senderId,
            Instant createdAt,
            String preview,
            Long seq
    ) {
        String senderName = memberRepo.findByRoomIdAndUserId(roomId, senderId)
                .map(RoomMember::getDisplayName)
                .orElse("");

        int updated = roomRepo.updateLastMessageSafe(
                roomId,
                messageId,
                senderId,
                senderName,
                createdAt,
                preview,
                seq
        );

        if (updated == 0) {
            log.info(
                "Skip last-message update for roomId={}, messageId={}, seq={} (likely stale seq)",
                roomId,
                messageId,
                seq
            );
            return;
        }

        log.info(
                "Updated room last-message roomId={}, messageId={}, senderId={}, seq={}",
                roomId,
                messageId,
                senderId,
                seq
        );

        evictRoomMembers(roomId);
    }

    @Override
    public void updateLastMessagePreviewIfMatch(
            UUID roomId,
            UUID messageId,
            String preview
    ) {
        int updated = roomRepo.updatePreviewIfMatch(roomId, messageId, preview);

        if (updated > 0) {
            evictRoomMembers(roomId);
        }
    }

    @Override
    public void handleMessageDeleted(UUID roomId, UUID messageId) {
        Room room = roomRepo.findById(roomId).orElse(null);
        if (room == null) return;

        if (!messageId.equals(room.getLastMessageId())) return;

        room.setLastMessagePreview("Message deleted");

        evictRoomMembers(roomId);
    }

    @Override
    public RoomAvatarUploadResponse uploadAvatar(UUID roomId, UUID userId, MultipartFile file) {
        Room room = roomRepo.findById(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Room not found"));

        RoomMember member = memberRepo.findByRoomIdAndUserId(roomId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN, "Not a member"));

        if (member.getRole() != Role.OWNER) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Only owner");
        }

        CloudinaryUploadResult uploaded =
                cloudinaryService.uploadAvatar(file, "room_" + roomId);

        room.setAvatarUrl(uploaded.getSecureUrl());
        room.setAvatarPublicId(uploaded.getPublicId());

        evictRoomMembers(roomId);

        return new RoomAvatarUploadResponse(uploaded.getSecureUrl());
    }

    private RoomResponse toResponse(Room room, RoomMember member) {
        long lastSeq = safe(room.getLastSeq());
        long lastRead = safe(member.getLastReadSeq());

        return RoomResponse.builder()
                .id(room.getId())
                .type(room.getType())
                .name(room.getName())
                .avatarUrl(room.getAvatarUrl())
                .createdBy(room.getCreatedBy())
                .createdAt(room.getCreatedAt())
                .myRole(member.getRole())
                .unreadCount((int) Math.max(0, lastSeq - lastRead))
                .lastMessage(buildPreview(room))
                .build();
    }

    private LastMessagePreview buildPreview(Room room) {
        if (room.getLastMessageId() == null) return null;

        return LastMessagePreview.builder()
                .id(room.getLastMessageId())
                .senderId(room.getLastMessageSenderId())
                .content(room.getLastMessagePreview())
                .createdAt(room.getLastMessageAt())
                .build();
    }

    private void evictRoomsCache(UUID userId) {
        try {
            cacheManager.evict(CacheNames.ROOMS, roomsKey(userId));
        } catch (CreateCacheException e) {
            log.warn("Evict rooms cache failed for user {}", userId);
        }
    }

    private void evictRoomMembers(UUID roomId) {
        List<UUID> members = memberRepo.findUserIdsByRoomId(roomId);
        for (UUID id : members) {
            evictRoomsCache(id);
        }
    }

    private String roomsKey(UUID userId) {
        return "rooms:user:" + userId;
    }

    private long safe(Long v) {
        return v == null ? 0 : v;
    }

    private String defaultAvatar() {
        return "https://cdn.myapp.com/group/default.png";
    }

    private UserBasicProfile safeGetBasicProfile(UUID userId) {
        try {
            if (userId == null) return null;
            var response = userClient.getUsersBulk(List.of(userId));
            if (response == null || response.getData() == null) return null;
            return response.getData().stream()
                    .filter(p -> p != null && userId.equals(p.getAccountId()))
                    .findFirst()
                    .orElse(null);
        } catch (Exception ex) {
            log.warn("Failed to resolve user profile for room membership userId={}", userId);
            return null;
        }
    }
}
