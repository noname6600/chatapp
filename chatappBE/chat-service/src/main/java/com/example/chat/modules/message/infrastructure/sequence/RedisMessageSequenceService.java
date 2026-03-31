package com.example.chat.modules.message.infrastructure.sequence;

import com.example.chat.modules.message.domain.repository.ChatMessageRepository;
import com.example.chat.modules.message.domain.service.IMessageSequenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RedisMessageSequenceService implements IMessageSequenceService {

    private final StringRedisTemplate redisTemplate;
    private final ChatMessageRepository messageRepository;

    private static final String KEY_PREFIX = "chat:room:seq:";

    @Override
    public long nextSeq(UUID roomId) {

        String key = KEY_PREFIX + roomId;

        // Initialize from DB max once when key is absent. setIfAbsent keeps this safe under concurrency.
        String current = redisTemplate.opsForValue().get(key);
        if (current == null) {
            Long maxSeq = messageRepository.findMaxSeqByRoomId(roomId);
            long initial = (maxSeq != null && maxSeq > 0) ? maxSeq : 0L;
            redisTemplate.opsForValue().setIfAbsent(key, String.valueOf(initial));
        }

        Long seq = redisTemplate.opsForValue().increment(key);

        if (seq == null) {
            throw new IllegalStateException("Cannot generate message sequence");
        }

        return seq;
    }
}
