package com.chatweb.chat.modules.message.infrastructure.sequence;

import com.chatweb.chat.modules.message.domain.service.IMessageSequenceService;
import com.chatweb.chat.modules.room.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
class RedisRoomSequenceService implements IMessageSequenceService {

    private final RoomRepository roomRepository;
    private final StringRedisTemplate redisTemplate;

    @Override
    public long nextSeq(UUID roomId) {
        String redisKey = "room:seq:" + roomId;
        Long nextSeq = redisTemplate.opsForValue().increment(redisKey);

        if (nextSeq == null) {
            // Redis INCR should never return null for a healthy connection; treat as seed
            nextSeq = seedFromDb(roomId, redisKey);
        } else if (nextSeq == 1L) {
            // Key was just created by this INCR. Check if DB has a higher sequence.
            Long dbSeq = roomRepository.findById(roomId)
                    .map(room -> room.getLastSeq())
                    .orElse(0L);
            if (dbSeq != null && dbSeq > 0) {
                // Seed the key to DB value and increment once more atomically.
                // Race condition: another INCR may have run between our INCR and SET.
                // We use SET only when still at 1 to avoid rolling back concurrent increments.
                redisTemplate.opsForValue().setIfPresent(redisKey, String.valueOf(dbSeq));
                Long reincrement = redisTemplate.opsForValue().increment(redisKey);
                nextSeq = reincrement != null ? reincrement : dbSeq + 1;
            }
        }

        return nextSeq;
    }

    private long seedFromDb(UUID roomId, String redisKey) {
        Long dbSeq = roomRepository.findById(roomId)
                .map(room -> room.getLastSeq())
                .orElse(0L);
        long seed = dbSeq != null ? dbSeq + 1L : 1L;
        redisTemplate.opsForValue().set(redisKey, String.valueOf(seed));
        return seed;
    }
}
