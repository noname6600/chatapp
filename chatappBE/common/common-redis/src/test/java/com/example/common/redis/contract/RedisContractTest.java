package com.example.common.redis.contract;

import com.example.common.event.EventEnvelope;
import com.example.common.event.EventMetadata;
import com.example.common.event.SharedEventCatalog;
import com.example.common.integration.chat.ChatEventType;
import com.example.common.integration.chat.ChatMessagePayload;
import com.example.common.integration.presence.PresenceEventType;
import com.example.common.redis.channel.RedisChannels;
import com.example.common.redis.flow.RedisEventRoutingContext;
import com.example.common.redis.registry.DefaultRedisEventRegistry;
import com.example.common.redis.registry.RedisEventRegistry;
import com.example.common.redis.serialization.JsonRedisEventSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contract tests for common-redis.
 */
class RedisContractTest {

    private ObjectMapper mapper;
    private DefaultRedisEventRegistry registry;
    private JsonRedisEventSerializer serializer;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        registry = new DefaultRedisEventRegistry();
        registry.register("chat.message.sent", String.class);
        registry.register("account.created", String.class);

        serializer = new JsonRedisEventSerializer(mapper, registry);
    }

    // ── Serializer ────────────────────────────────────────────────────────────

    @Test
    void serializer_roundTripsCanonicalEnvelopeShape() {
        String eventId = UUID.randomUUID().toString();
        String correlationId = UUID.randomUUID().toString();

        EventEnvelope<String> envelope = envelope(eventId, correlationId, "hello-envelope");

        String json = serializer.serialize(envelope);
        EventEnvelope<?> result = serializer.deserialize(json);

        assertThat(result.metadata().getEventId()).isEqualTo(eventId);
        assertThat(result.metadata().getCorrelationId()).isEqualTo(correlationId);
        assertThat(result.metadata().getEventType()).isEqualTo("chat.message.sent");
        assertThat(result.payload()).isEqualTo("hello-envelope");
    }

    @Test
    void deserializer_preservesEventIdAndCorrelationId() {
        String eventId = UUID.randomUUID().toString();
        String correlationId = UUID.randomUUID().toString();

        String json = String.format(
                "{\"metadata\":{\"eventId\":\"%s\",\"correlationId\":\"%s\"," +
                "\"eventType\":\"chat.message.sent\",\"sourceService\":\"chat-service\"," +
                "\"createdAt\":\"2026-01-01T00:00:00Z\"},\"payload\":\"hello\"}",
                eventId, correlationId
        );

        EventEnvelope<?> result = serializer.deserialize(json);

        assertThat(result.metadata().getEventId()).isEqualTo(eventId);
        assertThat(result.metadata().getCorrelationId()).isEqualTo(correlationId);
    }

    @Test
    void deserializer_rejectsMissingPayloadForPayloadBearingEvent() {
        // registry with real catalog so payload-bearing event is registered
        DefaultRedisEventRegistry catalogRegistry = new DefaultRedisEventRegistry();
        SharedEventCatalog.registerAll(catalogRegistry);
        JsonRedisEventSerializer catalogSerializer = new JsonRedisEventSerializer(mapper, catalogRegistry);

        String json = String.format(
                "{\"metadata\":{\"eventId\":\"%s\",\"correlationId\":\"%s\"," +
                "\"eventType\":\"%s\",\"sourceService\":\"chat-service\"," +
                "\"createdAt\":\"2026-01-01T00:00:00Z\"}}",
                UUID.randomUUID(), UUID.randomUUID(),
                ChatEventType.MESSAGE_SENT.value()
        );

        assertThatThrownBy(() -> catalogSerializer.deserialize(json))
                .hasMessageContaining("missing payload for payload-bearing event type");
    }

    @Test
    void deserializer_acceptsPayloadLessEventWithNullPayload() {
        // Use a payload-less event type — no payload field in JSON, no registry lookup expected
        DefaultRedisEventRegistry catalogRegistry = new DefaultRedisEventRegistry();
        SharedEventCatalog.registerAll(catalogRegistry);
        JsonRedisEventSerializer catalogSerializer = new JsonRedisEventSerializer(mapper, catalogRegistry);

        String payloadLessType = SharedEventCatalog.PAYLOAD_LESS_EVENT_TYPES.iterator().next();
        String json = String.format(
                "{\"metadata\":{\"eventId\":\"%s\",\"correlationId\":\"%s\"," +
                "\"eventType\":\"%s\",\"sourceService\":\"some-service\"," +
                "\"createdAt\":\"2026-01-01T00:00:00Z\"}}",
                UUID.randomUUID(), UUID.randomUUID(), payloadLessType
        );

        EventEnvelope<?> result = catalogSerializer.deserialize(json);

        assertThat(result.metadata().getEventType()).isEqualTo(payloadLessType);
        assertThat(result.payload()).isNull();
    }

    // ── SharedEventCatalog bootstrap ──────────────────────────────────────────

    @Test
    void sharedCatalog_bootstrapsRedisRegistryWithPayloadBearingEvents() {
        DefaultRedisEventRegistry catalogRegistry = new DefaultRedisEventRegistry();
        SharedEventCatalog.registerAll(catalogRegistry);

        assertThat(catalogRegistry.contains(ChatEventType.MESSAGE_SENT.value())).isTrue();
        assertThat(catalogRegistry.resolvePayload(ChatEventType.MESSAGE_SENT.value()))
                .isEqualTo(ChatMessagePayload.class);
        assertThat(catalogRegistry.contains(PresenceEventType.USER_STATUS_CHANGED.value())).isTrue();
    }

    @Test
    void sharedCatalog_payloadLessTypesNotRegisteredInRedisRegistry() {
        DefaultRedisEventRegistry catalogRegistry = new DefaultRedisEventRegistry();
        SharedEventCatalog.registerAll(catalogRegistry);

        for (String payloadLessType : SharedEventCatalog.PAYLOAD_LESS_EVENT_TYPES) {
            assertThat(catalogRegistry.contains(payloadLessType))
                    .as("payload-less event should not be in registry: " + payloadLessType)
                    .isFalse();
        }
    }

    // ── Registry ──────────────────────────────────────────────────────────────

    @Test
    void registry_registersAndResolvesPayloadClass() {
        RedisEventRegistry reg = new DefaultRedisEventRegistry();
        reg.register("account.created", String.class);

        assertThat(reg.resolvePayload("account.created")).isEqualTo(String.class);
    }

    @Test
    void registry_throwsOnDuplicateRegistration() {
        RedisEventRegistry reg = new DefaultRedisEventRegistry();
        reg.register("account.created", String.class);

        assertThatThrownBy(() -> reg.register("account.created", Integer.class))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Conflicting");
    }

    @Test
    void registry_throwsOnUnknownEventType() {
        RedisEventRegistry reg = new DefaultRedisEventRegistry();

        assertThatThrownBy(() -> reg.resolvePayload("unknown.event"))
                .isInstanceOf(IllegalStateException.class);
    }

    // ── RedisEventRoutingContext ───────────────────────────────────────────────

    @Test
    void routingContext_mapsEnvelopeMetadataConsistently() {
        EventEnvelope<String> envelope = envelope(UUID.randomUUID().toString(), "corr-1", "hello");

        RedisEventRoutingContext ctx = RedisEventRoutingContext.of("realtime.chat.room.1", envelope);

        assertThat(ctx.channel()).isEqualTo("realtime.chat.room.1");
        assertThat(ctx.eventType()).isEqualTo("chat.message.sent");
        assertThat(ctx.correlationId()).isEqualTo("corr-1");
        assertThat(ctx.sourceService()).isEqualTo("chat-service");
    }

    @Test
    void routingContext_handlesNullEnvelopeGracefully() {
        RedisEventRoutingContext ctx = RedisEventRoutingContext.of("channel", (EventEnvelope<?>) null);
        assertThat(ctx.eventType()).isNull();
        assertThat(ctx.correlationId()).isNull();
        assertThat(ctx.sourceService()).isNull();
    }

    private static EventEnvelope<String> envelope(String eventId, String correlationId, String payload) {
        return new EventEnvelope<>(
                new EventMetadata(
                        eventId,
                        "chat.message.sent",
                        "chat-service",
                        Instant.parse("2026-01-01T00:00:00Z"),
                        correlationId
                ),
                payload
        );
    }
}
