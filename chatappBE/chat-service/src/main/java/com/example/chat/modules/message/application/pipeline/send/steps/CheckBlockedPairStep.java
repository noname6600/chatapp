package com.example.chat.modules.message.application.pipeline.send.steps;

import com.example.chat.modules.message.application.pipeline.send.SendMessageContext;
import com.example.chat.modules.message.infrastructure.client.FriendshipClient;
import com.example.chat.modules.room.entity.PrivateRoom;
import com.example.chat.modules.room.entity.Room;
import com.example.chat.modules.room.enums.RoomType;
import com.example.chat.modules.room.repository.PrivateRoomRepository;
import com.example.chat.modules.room.repository.RoomRepository;
import com.example.common.core.pipeline.PipelineStep;
import com.example.common.web.exception.BusinessException;
import com.example.common.web.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class CheckBlockedPairStep
        implements PipelineStep<SendMessageContext> {

    private final RoomRepository roomRepository;
    private final PrivateRoomRepository privateRoomRepository;
    private final FriendshipClient friendshipClient;

    @Override
    public void execute(SendMessageContext context) {
        UUID roomId = context.getRoomId();
        UUID senderId = context.getSenderId();

        Room room = roomRepository.findById(roomId).orElse(null);
        if (room == null || room.getType() != RoomType.PRIVATE) {
            return;
        }

        PrivateRoom privateRoom = privateRoomRepository.findByRoomId(roomId).orElse(null);
        if (privateRoom == null) {
            return;
        }

        UUID otherId = senderId.equals(privateRoom.getUser1Id())
                ? privateRoom.getUser2Id()
                : privateRoom.getUser1Id();

        try {
            var response = friendshipClient.isBlockedBetween(senderId, otherId);
            Boolean blocked = response != null ? response.getData() : null;

            if (Boolean.TRUE.equals(blocked)) {
                throw new BusinessException(
                        ErrorCode.BLOCKED_SEND,
                        "You cannot send messages to this user."
                );
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            // Fail closed so private-room sends cannot bypass blocked-pair protection.
                log.warn("[CheckBlockedPairStep] Could not check block status for roomId={}, otherId={}: {}",
                    roomId, otherId, e.getMessage());
            throw new BusinessException(
                    ErrorCode.BLOCKED_SEND,
                    "You cannot send messages to this user."
            );
        }
    }

    @Override
    public Class<? extends PipelineStep<?>>[] runAfter() {
        return new Class[]{ValidateRoomPermissionStep.class};
    }
}
