package com.chatweb.chat.modules.message.infrastructure.sequence;

import com.chatweb.chat.modules.room.entity.Room;
import com.chatweb.chat.modules.room.repository.RoomRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisRoomSequenceServiceTest {

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private StringRedisTemplate redisTemplate;

    @InjectMocks
    private RedisRoomSequenceService service;

    @Test
    void nextSeq_reseedsWhenRedisStartsAtOneButDatabaseHasHigherValue() {
        UUID roomId = UUID.randomUUID();
        Room room = Room.builder()
                .id(roomId)
                .lastSeq(145L)
                .build();

        when(redisTemplate.opsForValue().increment("room:seq:" + roomId)).thenReturn(1L, 146L);
        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));

        long seq = service.nextSeq(roomId);

        assertThat(seq).isEqualTo(146L);
        verify(roomRepository).findById(roomId);
    }

    @Test
    void nextSeq_usesRedisIncrementWhenAvailable() {
        UUID roomId = UUID.randomUUID();
        String redisKey = "room:seq:" + roomId;

        when(redisTemplate.opsForValue().increment(redisKey)).thenReturn(146L);

        long seq = service.nextSeq(roomId);

        assertThat(seq).isEqualTo(146L);
        verify(redisTemplate).opsForValue().increment(redisKey);
    }

    @Test
    void nextSeq_fallsBackToDatabaseSnapshotWhenRedisIncrementUnavailable() {
        UUID roomId = UUID.randomUUID();
        Room room = Room.builder()
                .id(roomId)
                .lastSeq(145L)
                .build();

        when(redisTemplate.opsForValue().increment("room:seq:" + roomId)).thenReturn(null);
        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));

        long seq = service.nextSeq(roomId);

        assertThat(seq).isEqualTo(146L);
        verify(roomRepository).findById(roomId);
    }
}
