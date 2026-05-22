package com.chatweb.chat.modules.room.service.impl;

import com.chatweb.chat.modules.room.repository.RoomMemberRepository;
import com.chatweb.chat.modules.room.service.RoomMembershipGuard;
import com.chatweb.common.core.exception.BusinessException;
import com.chatweb.common.core.exception.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class RoomMembershipGuardImpl implements RoomMembershipGuard {

    private final RoomMemberRepository roomMemberRepository;

    @Override
    public void ensureRoomMember(UUID roomId, UUID userId) {
        if (!roomMemberRepository.existsByRoomIdAndUserId(roomId, userId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "Not a room member");
        }
    }
}