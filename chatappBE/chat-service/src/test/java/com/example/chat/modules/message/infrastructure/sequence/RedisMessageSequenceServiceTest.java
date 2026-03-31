package com.example.chat.modules.message.infrastructure.sequence;

import com.example.chat.modules.message.domain.repository.ChatMessageRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisMessageSequenceServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ChatMessageRepository messageRepository;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private RedisMessageSequenceService service;

    @Test
    void nextSeq_initializesFromDbMaxWhenKeyAbsent_thenIncrements() {
        UUID roomId = UUID.randomUUID();
        String key = "chat:room:seq:" + roomId;

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(key)).thenReturn(null);
        when(messageRepository.findMaxSeqByRoomId(roomId)).thenReturn(145L);
        when(valueOperations.setIfAbsent(key, "145")).thenReturn(true);
        when(valueOperations.increment(key)).thenReturn(146L);

        long seq = service.nextSeq(roomId);

        assertThat(seq).isEqualTo(146L);
        verify(messageRepository).findMaxSeqByRoomId(roomId);
        verify(valueOperations).setIfAbsent(key, "145");
        verify(valueOperations).increment(key);
    }

    @Test
    void nextSeq_skipsDbLookupWhenKeyAlreadyExists() {
        UUID roomId = UUID.randomUUID();
        String key = "chat:room:seq:" + roomId;

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(key)).thenReturn("210");
        when(valueOperations.increment(key)).thenReturn(211L);

        long seq = service.nextSeq(roomId);

        assertThat(seq).isEqualTo(211L);
        verify(messageRepository, never()).findMaxSeqByRoomId(roomId);
        verify(valueOperations, never()).setIfAbsent(key, "210");
        verify(valueOperations).increment(key);
    }
}
