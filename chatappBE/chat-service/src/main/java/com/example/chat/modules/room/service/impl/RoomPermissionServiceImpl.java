package com.example.chat.modules.room.service.impl;

import com.example.chat.modules.message.application.port.RoomPermissionService;
import com.example.chat.modules.room.entity.Room;
import com.example.chat.modules.room.repository.PrivateRoomRepository;
import com.example.chat.modules.room.repository.RoomMemberRepository;
import com.example.chat.modules.room.repository.RoomRepository;
import com.example.common.web.exception.BusinessException;
import com.example.common.web.exception.ErrorCode;
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
                                        ErrorCode.RESOURCE_NOT_FOUND,
                                        "Room not found"
                                )
                        );

        switch (room.getType()) {

            case PRIVATE:
                return canSendPrivate(roomId, userId);

            case GROUP:
                return memberRepository
                        .existsByRoomIdAndUserId(roomId, userId);

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
