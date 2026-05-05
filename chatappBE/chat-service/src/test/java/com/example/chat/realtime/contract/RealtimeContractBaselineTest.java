package com.example.chat.realtime.contract;

import com.example.chat.constants.ChatRedisChannels;
import com.example.common.integration.chat.ChatEventType;
import com.example.common.kafka.topic.KafkaTopics;
import com.example.common.integration.realtime.RealtimeContractVersions;
import com.example.common.redis.channel.RedisChannels;
import com.example.common.websocket.protocol.RealtimeWsEvent;
import com.example.common.websocket.dto.WsOutgoingMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RealtimeContractBaselineTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void kafkaTopicAndEventNames_matchCurrentContract() {
        assertThat(KafkaTopics.CHAT_MESSAGE_SENT).isEqualTo("chat.message.sent");
        assertThat(ChatEventType.MESSAGE_SENT.value()).isEqualTo("chat.message.sent");
        assertThat(ChatRedisChannels.CHAT_ROOM).isEqualTo(RedisChannels.CHAT_ROOM_PREFIX);
        assertThat(ChatRedisChannels.CHAT_ROOM_PATTERN).isEqualTo(RedisChannels.CHAT_ROOM_PATTERN);
        assertThat(RealtimeContractVersions.CHAT_MESSAGE_EVENTS).isEqualTo("v1");
        assertThat(RealtimeContractVersions.CHAT_REDIS_FANOUT).isEqualTo("v1");
    }

    @Test
    void wsEvent_deserializesPayloadFromDataAlias() throws Exception {
        RealtimeWsEvent event = objectMapper.readValue(
            "{\"type\":\"chat.message.sent\",\"data\":{\"k\":\"v\"}}",
            RealtimeWsEvent.class
        );

        assertThat(event.getType()).isEqualTo("chat.message.sent");
        assertThat(event.getPayload()).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) event.getPayload()).get("k")).isEqualTo("v");
    }

    @Test
    void wsOutgoingMessage_deserializesPayloadFromDataAlias() throws Exception {
        WsOutgoingMessage event = objectMapper.readValue("{\"type\":\"chat.message.sent\",\"data\":{\"n\":1}}", WsOutgoingMessage.class);

        assertThat(event.getType()).isEqualTo("chat.message.sent");
        assertThat(event.getPayload()).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) event.getPayload()).get("n")).isEqualTo(1);
    }
}
