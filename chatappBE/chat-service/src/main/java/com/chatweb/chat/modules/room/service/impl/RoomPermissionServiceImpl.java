package com.chatweb.chat.modules.room.service.impl;

import com.chatweb.chat.modules.message.application.port.RoomPermissionService;
import com.chatweb.chat.modules.room.entity.Room;
import com.chatweb.chat.modules.room.repository.PrivateRoomRepository;
import com.chatweb.chat.modules.room.repository.RoomMemberRepository;
import com.chatweb.chat.modules.room.repository.RoomRepository;
import com.chatweb.chat.exception.ChatErrorCode;
import com.chatweb.common.core.exception.BusinessException;
import com.chatweb.common.core.exception.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RoomPermissionServiceImpl
        implements RoomPermissionService {

    private final RoomRepository roomRepository;
    private final RoomMemberRepository memberRepository;
    private final PrivateRoomRepository privateRoomRepository;

    @Override
    public boolean canSendMessage(UUID roomId, UUID userId) {

        Room room =
                roomRepository.findById(roomId)
                        .orElseThrow(() ->
                                new BusinessException(
                                        CommonErrorCode.RESOURCE_NOT_FOUND,
                                        "Room not found"
                                )
                        );

        switch (room.getType()) {

            case PRIVATE:
                return canSendPrivate(roomId, userId);

            case GROUP:
                if (!memberRepository.existsByRoomIdAndUserId(roomId, userId)) {
                    throw new BusinessException(
                            ChatErrorCode.REMOVED_FROM_GROUP,
                            "You have been removed from this group."
                    );
                }
                return true;

            default:
                return false;
        }
    }

    private boolean canSendPrivate(UUID roomId, UUID userId) {

        return privateRoomRepository
                .existsByRoomIdAndUser1Id(roomId, userId)
                ||
                privateRoomRepository
                        .existsByRoomIdAndUser2Id(roomId, userId);
    }
}


