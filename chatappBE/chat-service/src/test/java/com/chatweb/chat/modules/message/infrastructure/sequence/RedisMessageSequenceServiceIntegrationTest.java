package com.chatweb.chat.modules.message.infrastructure.sequence;

import com.chatweb.chat.modules.message.domain.service.IMessageSequenceService;
import com.chatweb.chat.modules.room.entity.Room;
import com.chatweb.chat.modules.room.repository.RoomRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RedisMessageSequenceServiceIntegrationTest {

    @Autowired
    private IMessageSequenceService service;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RoomRepository roomRepository;

    @Test
    void redisPersistence_ensuresSequenceContinuity() {
        UUID roomId = UUID.randomUUID();
        String redisKey = "room:seq:" + roomId;

        redisTemplate.opsForValue().set(redisKey, "200");

        long seq = service.nextSeq(roomId);

        assertThat(seq).isEqualTo(201L);
    }

    @Test
    void nextSeq_seedsFromDatabaseWhenRedisKeyMissing() {
        Room room = Room.builder()
                .id(UUID.randomUUID())
                .lastSeq(50L)
                .build();
        roomRepository.save(room);

        long seq = service.nextSeq(room.getId());

        assertThat(seq).isEqualTo(51L);
        assertThat(redisTemplate.opsForValue().get("room:seq:" + room.getId())).isEqualTo("51");
    }
}