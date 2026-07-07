package com.chatweb.voice.adapter.out.redis;

import com.chatweb.voice.domain.port.out.VoiceRoomStatePort;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class VoiceRoomRedisAdapter implements VoiceRoomStatePort {

    private static final String ROOM_PARTICIPANTS_KEY = "voice:room:%s:participants";
    private static final String USER_ACTIVE_ROOMS_KEY = "voice:user:%s:active_rooms";

    private final StringRedisTemplate redisTemplate;

    @Override
    public void addParticipant(UUID chatRoomId, UUID userId) {
        String key = ROOM_PARTICIPANTS_KEY.formatted(chatRoomId);
        redisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
    }

    @Override
    public void removeParticipant(UUID chatRoomId, UUID userId) {
        String key = ROOM_PARTICIPANTS_KEY.formatted(chatRoomId);
        redisTemplate.opsForZSet().remove(key, userId.toString());
    }

    @Override
    public Set<String> getParticipantIds(UUID chatRoomId) {
        String key = ROOM_PARTICIPANTS_KEY.formatted(chatRoomId);
        Set<String> members = redisTemplate.opsForZSet().range(key, 0, -1);
        return members != null ? members : Set.of();
    }

    @Override
    public long getParticipantCount(UUID chatRoomId) {
        String key = ROOM_PARTICIPANTS_KEY.formatted(chatRoomId);
        Long count = redisTemplate.opsForZSet().size(key);
        return count != null ? count : 0L;
    }

    @Override
    public void addActiveRoom(UUID userId, UUID chatRoomId) {
        String key = USER_ACTIVE_ROOMS_KEY.formatted(userId);
        redisTemplate.opsForSet().add(key, chatRoomId.toString());
    }

    @Override
    public void removeActiveRoom(UUID userId, UUID chatRoomId) {
        String key = USER_ACTIVE_ROOMS_KEY.formatted(userId);
        redisTemplate.opsForSet().remove(key, chatRoomId.toString());
    }

    @Override
    public Set<String> getActiveRooms(UUID userId) {
        String key = USER_ACTIVE_ROOMS_KEY.formatted(userId);
        Set<String> rooms = redisTemplate.opsForSet().members(key);
        return rooms != null ? rooms : Set.of();
    }
}
