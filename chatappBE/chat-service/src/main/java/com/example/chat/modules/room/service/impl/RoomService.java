package com.example.chat.modules.room.service.impl;

import com.example.chat.config.InviteCodeGenerator;
import com.example.chat.modules.message.application.service.ISystemMessageService;
import com.example.chat.modules.message.domain.enums.SystemEventType;
import com.example.chat.modules.room.cache.policy.RoomCacheInvalidationPolicy;
import com.example.chat.modules.message.infrastructure.client.UserBasicProfile;
import com.example.chat.modules.message.infrastructure.client.UserClient;
import com.example.chat.modules.room.dto.CloudinaryUploadResult;
import com.example.chat.modules.room.dto.LastMessagePreview;
import com.example.chat.modules.room.dto.RoomAvatarUploadResponse;
import com.example.chat.modules.room.dto.RoomMemberJoinedPayload;
import com.example.chat.modules.room.dto.RoomMemberLeftPayload;
import com.example.chat.modules.room.dto.RoomResponse;
import com.example.chat.realtime.port.ChatRealtimePort;
import com.example.common.integration.chat.ChatEventType;
import com.example.common.realtime.policy.RealtimeFlowId;
import com.example.chat.modules.room.entity.Room;
import com.example.chat.modules.room.entity.RoomBan;
import com.example.chat.modules.room.entity.RoomMember;
import com.example.chat.modules.room.enums.Role;
import com.example.chat.modules.room.enums.RoomType;
import com.example.chat.modules.room.repository.RoomBanRepository;
import com.example.chat.modules.room.repository.RoomMemberRepository;
import com.example.chat.modules.room.repository.RoomRepository;
import com.example.chat.modules.room.service.IRoomService;
import com.example.common.core.exception.BusinessException;
import com.example.common.core.exception.CommonErrorCode;
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
    private final RoomBanRepository roomBanRepository;
    private final UserClient userClient;
    private final InviteCodeGenerator inviteCodeGenerator;
    private final GroupAvatarGenerator avatarGenerator;
    private final CloudinaryService cloudinaryService;
    private final RoomCacheInvalidationPolicy roomCacheInvalidationPolicy;
    private final ChatRealtimePort chatRealtimePort;
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
                .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND, "Room not found"));

        if (room.getType() == RoomType.PRIVATE) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "Cannot join private room");
        }

        if (memberRepo.existsByRoomIdAndUserId(roomId, userId)) {
            return;
        }

        if (roomBanRepository.existsByRoomIdAndUserId(roomId, userId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "You are banned from this room");
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

        chatRealtimePort.publishRoomEvent(
            roomId,
            ChatEventType.MEMBER_JOINED.value(),
            RoomMemberJoinedPayload.builder()
                .roomId(roomId)
                .userId(userId)
                .role(Role.MEMBER.name())
                .joinedAt(saved.getJoinedAt())
                .build()
            ,
            RealtimeFlowId.CHAT_ROOM_MEMBER_ADD
        );

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
                .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND, "Membership not found"));

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

        chatRealtimePort.publishRoomEvent(
            roomId,
            ChatEventType.MEMBER_LEFT.value(),
            RoomMemberLeftPayload.builder()
                .roomId(roomId)
                .userId(userId)
                .build()
            ,
            RealtimeFlowId.CHAT_ROOM_MEMBER_REMOVE
        );
    }

    @Override
    public RoomResponse renameRoom(UUID roomId, UUID userId, String newName) {
        Room room = roomRepo.findById(roomId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND, "Room not found"));

        RoomMember member = memberRepo.findByRoomIdAndUserId(roomId, userId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.FORBIDDEN, "Not a member"));

        if (member.getRole() != Role.OWNER) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "Only owner can rename");
        }

        room.setName(newName);

        evictRoomMembers(roomId);

        return toResponse(room, member);
    }

    @Override
    public void addMember(UUID roomId, UUID ownerId, UUID newUserId) {
        assertOwner(roomId, ownerId, "Only owner can invite");

        if (memberRepo.existsByRoomIdAndUserId(roomId, newUserId)) {
            return;
        }

        if (roomBanRepository.existsByRoomIdAndUserId(roomId, newUserId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "User is banned from this room");
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
        assertOwner(roomId, ownerId, "Only owner can remove");

        if (ownerId.equals(targetUser)) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "Owner cannot remove themselves");
        }

        memberRepo.deleteByRoomIdAndUserId(roomId, targetUser);

        evictRoomsCache(targetUser);
        evictRoomMembers(roomId);

        chatRealtimePort.publishRoomEvent(
            roomId,
            ChatEventType.MEMBER_REMOVED.value(),
            RoomMemberLeftPayload.builder()
                .roomId(roomId)
                .userId(targetUser)
                .build()
            ,
            RealtimeFlowId.CHAT_ROOM_MEMBER_REMOVE
        );
    }

    @Override
    public void banMember(UUID roomId, UUID ownerId, UUID targetUser) {
        assertOwner(roomId, ownerId, "Only owner can ban");

        if (ownerId.equals(targetUser)) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "Owner cannot ban themselves");
        }

        if (!roomBanRepository.existsByRoomIdAndUserId(roomId, targetUser)) {
            roomBanRepository.save(RoomBan.builder()
                    .roomId(roomId)
                    .userId(targetUser)
                    .bannedBy(ownerId)
                    .build());
        }

        memberRepo.deleteByRoomIdAndUserId(roomId, targetUser);

        evictRoomsCache(targetUser);
        evictRoomMembers(roomId);

        chatRealtimePort.publishRoomEvent(
            roomId,
            ChatEventType.MEMBER_REMOVED.value(),
            RoomMemberLeftPayload.builder()
                .roomId(roomId)
                .userId(targetUser)
                .build()
            ,
            RealtimeFlowId.CHAT_ROOM_MEMBER_REMOVE
        );
    }

    @Override
    public void unbanMember(UUID roomId, UUID ownerId, UUID targetUser) {
        assertOwner(roomId, ownerId, "Only owner can unban");
        roomBanRepository.deleteByRoomIdAndUserId(roomId, targetUser);
    }

    @Override
    public void transferOwnership(UUID roomId, UUID ownerId, UUID newOwnerId) {
        if (ownerId.equals(newOwnerId)) {
            return;
        }

        RoomMember owner = assertOwner(roomId, ownerId, "Only owner can transfer ownership");

        RoomMember newOwner = memberRepo.findByRoomIdAndUserId(roomId, newOwnerId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.BAD_REQUEST, "New owner must be a room member"));

        owner.setRole(Role.MEMBER);
        newOwner.setRole(Role.OWNER);

        memberRepo.save(owner);
        memberRepo.save(newOwner);

        evictRoomMembers(roomId);
        evictRoomsCache(ownerId);
        evictRoomsCache(newOwnerId);
    }

    @Override
    public void bulkBanMembers(UUID roomId, UUID ownerId, List<UUID> targetUsers) {
        assertOwner(roomId, ownerId, "Only owner can ban");

        if (targetUsers == null || targetUsers.isEmpty()) {
            return;
        }

        targetUsers.stream()
                .filter(targetUser -> targetUser != null && !ownerId.equals(targetUser))
                .distinct()
                .forEach(targetUser -> banMember(roomId, ownerId, targetUser));
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
                .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND, "Room not found"));

        RoomMember member = memberRepo.findByRoomIdAndUserId(roomId, userId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.FORBIDDEN, "Not a member"));

        if (member.getRole() != Role.OWNER) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "Only owner");
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
        roomCacheInvalidationPolicy.evictRoomsForUser(userId);
    }

    private void evictRoomMembers(UUID roomId) {
        roomCacheInvalidationPolicy.evictRoomsForRoomMembers(roomId);
    }

    private long safe(Long v) {
        return v == null ? 0 : v;
    }

    private RoomMember assertOwner(UUID roomId, UUID ownerId, String unauthorizedMessage) {
        RoomMember owner = memberRepo.findByRoomIdAndUserId(roomId, ownerId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.FORBIDDEN, "Not a member"));

        if (owner.getRole() != Role.OWNER) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN, unauthorizedMessage);
        }

        return owner;
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


