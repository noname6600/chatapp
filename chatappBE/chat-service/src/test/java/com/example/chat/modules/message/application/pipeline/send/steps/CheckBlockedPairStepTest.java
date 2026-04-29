package com.example.chat.modules.message.application.pipeline.send.steps;

import com.example.chat.modules.message.application.pipeline.send.SendMessageContext;
import com.example.chat.modules.message.infrastructure.client.FriendshipClient;
import com.example.chat.modules.room.entity.PrivateRoom;
import com.example.chat.modules.room.entity.Room;
import com.example.chat.modules.room.enums.RoomType;
import com.example.chat.modules.room.repository.PrivateRoomRepository;
import com.example.chat.modules.room.repository.RoomRepository;
import com.example.chat.exception.ChatErrorCode;
import com.example.common.core.exception.BusinessException;
import com.example.common.web.response.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CheckBlockedPairStepTest {

    @Mock private RoomRepository roomRepository;
    @Mock private PrivateRoomRepository privateRoomRepository;
    @Mock private FriendshipClient friendshipClient;

    @InjectMocks
    private CheckBlockedPairStep step;

    private UUID roomId;
    private UUID senderId;
    private UUID otherId;
    private SendMessageContext context;

    @BeforeEach
    void setUp() {
        roomId = UUID.randomUUID();
        senderId = UUID.randomUUID();
        otherId = UUID.randomUUID();

        context = new SendMessageContext();
        context.setRoomId(roomId);
        context.setSenderId(senderId);
    }

    @Test
    void execute_skips_whenRoomIsNotPrivate() {
        when(roomRepository.findById(roomId)).thenReturn(Optional.of(
                Room.builder().id(roomId).type(RoomType.GROUP).build()
        ));

        assertThatCode(() -> step.execute(context)).doesNotThrowAnyException();
    }

    @Test
    void execute_skips_whenPrivateRoomMappingMissing() {
        when(roomRepository.findById(roomId)).thenReturn(Optional.of(
                Room.builder().id(roomId).type(RoomType.PRIVATE).build()
        ));
        when(privateRoomRepository.findByRoomId(roomId)).thenReturn(Optional.empty());

        assertThatCode(() -> step.execute(context)).doesNotThrowAnyException();
    }

    @Test
    void execute_throwsBlockedSend_whenStatusIsBlocked() {
        when(roomRepository.findById(roomId)).thenReturn(Optional.of(
                Room.builder().id(roomId).type(RoomType.PRIVATE).build()
        ));
        PrivateRoom privateRoom = PrivateRoom.builder()
                .id(UUID.randomUUID())
                .roomId(roomId)
                .user1Id(senderId)
                .user2Id(otherId)
                .build();
        when(privateRoomRepository.findByRoomId(roomId)).thenReturn(Optional.of(privateRoom));

        ApiResponse<Boolean> response = ApiResponse.success(true);
        when(friendshipClient.isBlockedBetween(senderId, otherId)).thenReturn(response);

        assertThatThrownBy(() -> step.execute(context))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ChatErrorCode.BLOCKED_SEND);
    }

    @Test
        void execute_allowsSend_whenStatusIsNone() {
        when(roomRepository.findById(roomId)).thenReturn(Optional.of(
                Room.builder().id(roomId).type(RoomType.PRIVATE).build()
        ));
        PrivateRoom privateRoom = PrivateRoom.builder()
                .id(UUID.randomUUID())
                .roomId(roomId)
                .user1Id(senderId)
                .user2Id(otherId)
                .build();
        when(privateRoomRepository.findByRoomId(roomId)).thenReturn(Optional.of(privateRoom));

        ApiResponse<Boolean> response = ApiResponse.success(false);
        when(friendshipClient.isBlockedBetween(senderId, otherId)).thenReturn(response);

        assertThatCode(() -> step.execute(context)).doesNotThrowAnyException();
    }

    @Test
    void execute_allowsSend_whenStatusIsFriends() {
        when(roomRepository.findById(roomId)).thenReturn(Optional.of(
                Room.builder().id(roomId).type(RoomType.PRIVATE).build()
        ));
        PrivateRoom privateRoom = PrivateRoom.builder()
                .id(UUID.randomUUID())
                .roomId(roomId)
                .user1Id(senderId)
                .user2Id(otherId)
                .build();
        when(privateRoomRepository.findByRoomId(roomId)).thenReturn(Optional.of(privateRoom));

        ApiResponse<Boolean> response = ApiResponse.success(false);
        when(friendshipClient.isBlockedBetween(senderId, otherId)).thenReturn(response);

        assertThatCode(() -> step.execute(context)).doesNotThrowAnyException();
    }

    @Test
    void execute_deniesSend_whenFriendshipServiceFails() {
        when(roomRepository.findById(roomId)).thenReturn(Optional.of(
                Room.builder().id(roomId).type(RoomType.PRIVATE).build()
        ));
        PrivateRoom privateRoom = PrivateRoom.builder()
                .id(UUID.randomUUID())
                .roomId(roomId)
                .user1Id(otherId)
                .user2Id(senderId)
                .build();
        when(privateRoomRepository.findByRoomId(roomId)).thenReturn(Optional.of(privateRoom));
        when(friendshipClient.isBlockedBetween(senderId, otherId)).thenThrow(new RuntimeException("service unavailable"));

        assertThatThrownBy(() -> step.execute(context))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ChatErrorCode.BLOCKED_SEND);
    }

    @Test
    void execute_rechecksBlockedStatus_onEverySendAttempt() {
        when(roomRepository.findById(roomId)).thenReturn(Optional.of(
                Room.builder().id(roomId).type(RoomType.PRIVATE).build()
        ));
        PrivateRoom privateRoom = PrivateRoom.builder()
                .id(UUID.randomUUID())
                .roomId(roomId)
                .user1Id(senderId)
                .user2Id(otherId)
                .build();
        when(privateRoomRepository.findByRoomId(roomId)).thenReturn(Optional.of(privateRoom));
        when(friendshipClient.isBlockedBetween(senderId, otherId))
                .thenReturn(ApiResponse.success(false))
                .thenReturn(ApiResponse.success(true));

        assertThatCode(() -> step.execute(context)).doesNotThrowAnyException();
        assertThatThrownBy(() -> step.execute(context))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ChatErrorCode.BLOCKED_SEND);

                verify(friendshipClient, times(2)).isBlockedBetween(senderId, otherId);
    }
}

