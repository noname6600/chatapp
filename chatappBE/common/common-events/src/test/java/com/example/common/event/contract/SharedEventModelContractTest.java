package com.example.common.event.contract;

import com.example.common.event.DefaultEventPayloadRegistry;
import com.example.common.event.Event;
import com.example.common.event.EventEnvelope;
import com.example.common.event.EventMetadata;
import com.example.common.event.EventPayloadRegistry;
import com.example.common.event.SharedEventCatalog;
import com.example.common.event.validation.EventContractValidator;
import com.example.common.integration.account.AccountEventType;
import com.example.common.integration.chat.ChatEventType;
import com.example.common.integration.friendship.FriendshipEventType;
import com.example.common.integration.notification.NotificationEventType;
import com.example.common.integration.presence.PresenceEventType;
import com.example.common.integration.user.UserEventType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * Contract tests for the shared event model in common-events.
 *
 * <p>Covers:
 * <ul>
 *   <li>10.1 - Shared event envelope serialization round-trip</li>
 *   <li>10.1 - Event type validator accepts valid patterns</li>
 *   <li>10.1 - Event type validator rejects non-conforming values</li>
 *   <li>10.1 - EventMetadata validates required fields</li>
 * </ul>
 */
class SharedEventModelContractTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    // -- 10.1: Envelope serialization round-trip --

    @Test
    void eventEnvelope_serializesAndDeserializesRoundTrip() throws Exception {
        EventMetadata metadata = new EventMetadata(
                UUID.randomUUID().toString(),
                "chat.message.sent",
                "chat-service",
                Instant.parse("2026-01-01T00:00:00Z"),
                UUID.randomUUID().toString()
        );
        String payload = "hello";

        EventEnvelope<String> envelope = new EventEnvelope<>(metadata, payload);
        String json = mapper.writeValueAsString(envelope);

        @SuppressWarnings("unchecked")
        EventEnvelope<String> restored = mapper.readValue(
                json,
                mapper.getTypeFactory().constructParametricType(EventEnvelope.class, String.class)
        );

        assertThat(restored.metadata().getEventId()).isEqualTo(metadata.getEventId());
        assertThat(restored.metadata().getEventType()).isEqualTo("chat.message.sent");
        assertThat(restored.metadata().getSourceService()).isEqualTo("chat-service");
        assertThat(restored.metadata().getCreatedAt()).isEqualTo(metadata.getCreatedAt());
        assertThat(restored.metadata().getCorrelationId()).isEqualTo(metadata.getCorrelationId());
        assertThat(restored.payload()).isEqualTo("hello");
    }

    @Test
    void eventEnvelope_preservesEventIdAndCorrelationIdOnRoundTrip() throws Exception {
        String eventId = "evt-" + UUID.randomUUID();
        String correlationId = "corr-" + UUID.randomUUID();

        EventMetadata metadata = new EventMetadata(
                eventId, "account.created", "auth-service", Instant.now(), correlationId
        );
        EventEnvelope<Void> envelope = new EventEnvelope<>(metadata, null);
        String json = mapper.writeValueAsString(envelope);
        EventEnvelope<?> restored = mapper.readValue(json, EventEnvelope.class);

        assertThat(restored.metadata().getEventId()).isEqualTo(eventId);
        assertThat(restored.metadata().getCorrelationId()).isEqualTo(correlationId);
    }

    @Test
    void eventEnvelope_implementsEventInterface() {
        EventMetadata metadata = new EventMetadata(
                "evt-1", "chat.message.sent", "chat-service", Instant.now(), "corr-1"
        );
        EventEnvelope<String> envelope = new EventEnvelope<>(metadata, "payload");

        assertThat(envelope).isInstanceOf(Event.class);
        assertThat(envelope.metadata()).isSameAs(metadata);
        assertThat(envelope.payload()).isEqualTo("payload");
    }

    // -- 10.1: EventContractValidator --

    @Test
    void validator_acceptsValidEventTypePatterns() {
        assertThat(EventContractValidator.isValidEventType("chat.message.sent")).isTrue();
        assertThat(EventContractValidator.isValidEventType("presence.user.status-changed")).isTrue();
        assertThat(EventContractValidator.isValidEventType("account.created")).isTrue();
        assertThat(EventContractValidator.isValidEventType("notification.email-sent")).isTrue();
        assertThat(EventContractValidator.isValidEventType("system.dead-letter")).isTrue();
    }

    @Test
    void validator_rejectsInvalidEventTypePatterns() {
        assertThat(EventContractValidator.isValidEventType("FRIEND_STATUS_CHANGED")).isFalse();
        assertThat(EventContractValidator.isValidEventType("chat.message_sent")).isFalse();
        assertThat(EventContractValidator.isValidEventType("Chat.message.sent")).isFalse();
        assertThat(EventContractValidator.isValidEventType("presence.user.status_changed")).isFalse();
        assertThat(EventContractValidator.isValidEventType("")).isFalse();
    }

    @Test
    void eventMetadata_rejectsBlankEventId() {
        assertThatThrownBy(() -> new EventMetadata(
                "", "chat.message.sent", "chat-service", Instant.now(), "corr-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eventId");
    }

    @Test
    void eventMetadata_rejectsNullEventType() {
        assertThatThrownBy(() -> new EventMetadata(
                "evt-1", null, "chat-service", Instant.now(), "corr-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eventType");
    }

    @Test
    void eventMetadata_rejectsNullCreatedAt() {
        assertThatThrownBy(() -> new EventMetadata(
                "evt-1", "chat.message.sent", "chat-service", null, "corr-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("createdAt");
    }

    @Test
    void eventEnvelope_rejectsNullMetadata() {
        assertThatThrownBy(() -> new EventEnvelope<>(null, "payload"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("metadata");
    }

    @Test
    void validator_validateEventName_acceptsConformingValue() {
        assertThatNoException().isThrownBy(
                () -> EventContractValidator.validateEventNameOrThrow("chat.message.sent")
        );
    }

    @Test
    void validator_validateEventName_rejectsNonConformingEventType() {
        assertThatThrownBy(() -> EventContractValidator.validateEventNameOrThrow("CHAT_MESSAGE_SENT"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validator_validateIdentity_rejectsBlankCorrelationId() {
        assertThatThrownBy(() -> EventContractValidator.validateIdentityOrThrow(
                "evt-1",
                "",
                "chat-service",
                Instant.now()
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("correlationId");
    }

        @Test
        void sharedEventCatalog_coversAllCurrentSharedEventTypes() {
        EventPayloadRegistry registry = new DefaultEventPayloadRegistry();
        SharedEventCatalog.registerAll(registry);

        Set<String> allKnownEventTypes = Stream.of(
                Arrays.stream(AccountEventType.values()).map(AccountEventType::value),
                Arrays.stream(ChatEventType.values()).map(ChatEventType::value),
                Arrays.stream(FriendshipEventType.values()).map(FriendshipEventType::value),
                Arrays.stream(NotificationEventType.values()).map(NotificationEventType::value),
                Arrays.stream(PresenceEventType.values()).map(PresenceEventType::value),
                Arrays.stream(UserEventType.values()).map(UserEventType::value)
            )
            .flatMap(s -> s)
            .collect(Collectors.toSet());

        for (String eventType : allKnownEventTypes) {
            boolean payloadRegistered = registry.contains(eventType);
            boolean payloadLess = SharedEventCatalog.PAYLOAD_LESS_EVENT_TYPES.contains(eventType);

            assertThat(payloadRegistered || payloadLess)
                .as("event type must be represented by shared catalog: " + eventType)
                .isTrue();

            assertThat(payloadRegistered && payloadLess)
                .as("event type cannot be both payload-bearing and payload-less: " + eventType)
                .isFalse();
        }
        }
}
