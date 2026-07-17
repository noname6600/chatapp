package com.chatweb.chat.modules.message.application.pipeline.send.steps;

import com.chatweb.chat.modules.message.application.pipeline.send.SendMessageContext;
import com.chatweb.chat.modules.message.infrastructure.client.FriendshipClient;
import com.chatweb.chat.modules.room.entity.PrivateRoom;
import com.chatweb.chat.modules.room.entity.Room;
import com.chatweb.chat.modules.room.enums.RoomType;
import com.chatweb.chat.modules.room.repository.PrivateRoomRepository;
import com.chatweb.chat.modules.room.repository.RoomRepository;
import com.chatweb.chat.exception.ChatErrorCode;
import com.chatweb.common.core.pipeline.PipelineStep;
import com.chatweb.common.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class CheckBlockedPairStep
        implements PipelineStep<SendMessageContext> {

    private static final String CACHE_PREFIX = "blocked_pair:";
    private static final Duration CACHE_TTL = Duration.ofSeconds(60);

    private final RoomRepository roomRepository;
    private final PrivateRoomRepository privateRoomRepository;
    private final FriendshipClient friendshipClient;
    private final StringRedisTemplate redisTemplate;

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

        String cacheKey = blockedPairCacheKey(senderId, otherId);

        // Cache hit: avoid calling friendship-service on every send
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                if (Boolean.parseBoolean(cached)) {
                    throw new BusinessException(ChatErrorCode.BLOCKED_SEND, "You cannot send messages to this user.");
                }
                return;
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[CheckBlockedPairStep] Cache read failed, falling through to service call: {}", e.getMessage());
        }

        // Cache miss: call friendship-service, cache result, fail-open on service unavailability
        try {
            var response = friendshipClient.isBlockedBetween(senderId, otherId);
            boolean blocked = response != null && Boolean.TRUE.equals(response.getData());

            redisTemplate.opsForValue().set(cacheKey, String.valueOf(blocked), CACHE_TTL);

            if (blocked) {
                throw new BusinessException(ChatErrorCode.BLOCKED_SEND, "You cannot send messages to this user.");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            // Fail-open: friendship-service unavailability must not silently block all messages.
            // A monitor/alert should fire on elevated rates of this warning.
            log.warn("[CheckBlockedPairStep] Block check failed (fail-open, friendship-service may be down): roomId={} otherId={} error={}",
                    roomId, otherId, e.getMessage());
        }
    }

    public void invalidateCache(UUID userId1, UUID userId2) {
        redisTemplate.delete(blockedPairCacheKey(userId1, userId2));
    }

    private String blockedPairCacheKey(UUID userId1, UUID userId2) {
        String id1 = userId1.toString();
        String id2 = userId2.toString();
        return CACHE_PREFIX + (id1.compareTo(id2) < 0 ? id1 + ":" + id2 : id2 + ":" + id1);
    }

    @Override
    public Class<? extends PipelineStep<?>>[] runAfter() {
        return new Class[]{ValidateRoomPermissionStep.class};
    }
}

