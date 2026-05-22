package com.chatweb.chat.modules.room.service.impl;

import com.chatweb.chat.modules.room.repository.RoomMemberRepository;
import com.chatweb.common.core.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoomMembershipGuardImplTest {

    @Mock
    private RoomMemberRepository roomMemberRepository;

    @InjectMocks
    private RoomMembershipGuardImpl roomMembershipGuard;

    @Test
    void ensureRoomMember_passesWhenMembershipExists() {
        UUID roomId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(roomMemberRepository.existsByRoomIdAndUserId(roomId, userId)).thenReturn(true);

        assertThatCode(() -> roomMembershipGuard.ensureRoomMember(roomId, userId))
                .doesNotThrowAnyException();
    }

    @Test
    void ensureRoomMember_throwsForbiddenWhenMembershipMissing() {
        UUID roomId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(roomMemberRepository.existsByRoomIdAndUserId(roomId, userId)).thenReturn(false);

        assertThatThrownBy(() -> roomMembershipGuard.ensureRoomMember(roomId, userId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Not a room member");
    }
}
