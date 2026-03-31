package com.example.common.redis.serialization;

import com.example.common.redis.constants.RedisMessageFields;
import com.example.common.redis.exception.RedisPubSubException;
import com.example.common.redis.api.IRedisMessage;
import com.example.common.redis.message.RedisMessage;
import com.example.common.redis.registry.IRedisMessageRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class JsonRedisMessageSerializer
        implements IRedisMessageSerializer {

    private final ObjectMapper mapper;
    private final IRedisMessageRegistry registry;

    public JsonRedisMessageSerializer(
            ObjectMapper mapper,
            IRedisMessageRegistry registry
    ) {
        this.mapper = mapper;
        this.registry = registry;
    }

    @Override
    public String serialize(IRedisMessage message) {

        try {
            return mapper.writeValueAsString(message);

        } catch (Exception e) {
            throw new RedisPubSubException(
                    "unknown",
                    "Redis serialize failed for class=" +
                            message.getClass().getName(),
                    e
            );
        }
    }

    @Override
    public IRedisMessage deserialize(String payload) {

        try {

            JsonNode node = mapper.readTree(payload);

            String eventType =
                    node.get(RedisMessageFields.EVENT_TYPE).asText();

            Class<?> payloadClass =
                    registry.resolvePayload(eventType);

            JsonNode payloadNode = node.get(RedisMessageFields.PAYLOAD);

            Object payloadObject =
                    mapper.treeToValue(payloadNode, payloadClass);

            RedisMessage<Object> base =
                    mapper.treeToValue(node, RedisMessage.class);

            return RedisMessage.builder()
                    .messageId(base.getMessageId())
                    .eventType(base.getEventType())
                    .sourceService(base.getSourceService())
                    .createdAt(base.getCreatedAt())
                    .payload(payloadObject)
                    .build();

        } catch (Exception e) {

            throw new RedisPubSubException(
                    "unknown",
                    "Redis deserialize failed payload=" + payload,
                    e
            );
        }
    }
}
