package com.example.common.kafka.contract;

import com.example.common.event.DefaultEventPayloadRegistry;
import com.example.common.event.EventEnvelope;
import com.example.common.event.EventMetadata;
import com.example.common.event.EventPayloadRegistry;
import com.example.common.event.SharedEventCatalog;
import com.example.common.integration.account.AccountEventType;
import com.example.common.integration.chat.ChatEventType;
import com.example.common.integration.chat.ChatMessagePayload;
import com.example.common.kafka.consumer.KafkaEventDispatcher;
import com.example.common.kafka.consumer.KafkaEventHandler;
import com.example.common.kafka.flow.KafkaEventRoutingContext;
import com.example.common.kafka.observability.KafkaEventObserver;
import com.example.common.kafka.producer.DefaultKafkaEventPublisher;
import com.example.common.kafka.producer.KafkaEventPublisher;
import org.apache.kafka.clients.producer.Producer;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.SendResult;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contract tests for common-kafka.
 */
class KafkaContractTest {

    private static EventEnvelope<String> envelope(String eventType, String correlationId) {
        EventMetadata meta = new EventMetadata(
                UUID.randomUUID().toString(),
                eventType,
                "test-service",
                Instant.now(),
                correlationId
        );
        return new EventEnvelope<>(meta, "payload");
    }

    @Test
    void routingContext_mapsEventMetadataConsistently() {
        EventEnvelope<String> ev = envelope("chat.message.sent", "corr-1");
        KafkaEventRoutingContext ctx = KafkaEventRoutingContext.of("chat-topic", "room-1", ev);

        assertThat(ctx.topic()).isEqualTo("chat-topic");
        assertThat(ctx.key()).isEqualTo("room-1");
        assertThat(ctx.eventType()).isEqualTo("chat.message.sent");
        assertThat(ctx.correlationId()).isEqualTo("corr-1");
        assertThat(ctx.sourceService()).isEqualTo("test-service");
    }

    @Test
    void routingContext_handlesNullEventGracefully() {
        KafkaEventRoutingContext ctx = KafkaEventRoutingContext.of("t", "k", null);
        assertThat(ctx.eventType()).isNull();
        assertThat(ctx.correlationId()).isNull();
        assertThat(ctx.sourceService()).isNull();
    }

    @Test
    void kafkaEventPublisher_hasEnvelopePublishMethod() throws Exception {
        var envelopeMethod = KafkaEventPublisher.class.getMethod(
                "publish", String.class, String.class, EventEnvelope.class);
        assertThat(envelopeMethod).isNotNull();
    }

    @Test
    void kafkaEventPublisher_allowsTransportTopicNamesOutsideEventNamePattern() {
        AtomicReference<String> sentTopic = new AtomicReference<>();
        AtomicReference<String> sentKey = new AtomicReference<>();
        AtomicReference<Object> sentPayload = new AtomicReference<>();

        KafkaTemplate<String, Object> kafkaTemplate = new KafkaTemplate<>(new StubProducerFactory()) {
            @Override
            public CompletableFuture<SendResult<String, Object>> send(String topic, String key, Object data) {
                sentTopic.set(topic);
                sentKey.set(key);
                sentPayload.set(data);
                return CompletableFuture.completedFuture(null);
            }
        };

        KafkaEventObserver logger = new KafkaEventObserver() {
            @Override
            public void logPublish(KafkaEventRoutingContext context, EventEnvelope<?> event) {
            }

            @Override
            public void logError(KafkaEventRoutingContext context, EventEnvelope<?> event, Throwable ex) {
            }
        };

        DefaultKafkaEventPublisher publisher = new DefaultKafkaEventPublisher(kafkaTemplate, logger);
        EventEnvelope<String> event = envelope("chat.message.sent", "corr-1");

        assertThatNoException().isThrownBy(() -> publisher.publish("chat_topic_v1", "room-1", event));
        assertThat(sentTopic.get()).isEqualTo("chat_topic_v1");
        assertThat(sentKey.get()).isEqualTo("room-1");
        assertThat(sentPayload.get()).isSameAs(event);
    }

    @Test
    void dispatcher_routesEventToMatchingHandler() {
        AtomicReference<EventEnvelope<?>> received = new AtomicReference<>();

        KafkaEventHandler<String> handler = new KafkaEventHandler<>() {
            @Override public String eventType() { return "chat.message.sent"; }
            @Override public void handle(EventEnvelope<String> event) { received.set(event); }
        };

        KafkaEventDispatcher dispatcher = new KafkaEventDispatcher(List.of(handler));
        EventEnvelope<String> ev = envelope("chat.message.sent", "corr-1");
        dispatcher.dispatch(ev);

        assertThat(received.get()).isSameAs(ev);
    }

    @Test
    void dispatcher_logsWarningAndSkipsForUnknownEventType() {
        KafkaEventDispatcher dispatcher = new KafkaEventDispatcher(List.of());
        assertThatNoException().isThrownBy(
                () -> dispatcher.dispatch(envelope("unknown.event.type", "corr-1"))
        );
    }

    @Test
    void dispatcher_throwsOnDuplicateHandlerForSameEventType() {
        KafkaEventHandler<String> h1 = new KafkaEventHandler<>() {
            @Override public String eventType() { return "account.created"; }
            @Override public void handle(EventEnvelope<String> event) {}
        };
        KafkaEventHandler<String> h2 = new KafkaEventHandler<>() {
            @Override public String eventType() { return "account.created"; }
            @Override public void handle(EventEnvelope<String> event) {}
        };

        assertThatThrownBy(() -> new KafkaEventDispatcher(List.of(h1, h2)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate");
    }

    @Test
    void dispatcher_callsObserverLogDispatchOnSuccess() {
        AtomicBoolean logDispatchCalled = new AtomicBoolean(false);
        AtomicReference<KafkaEventRoutingContext> capturedContext = new AtomicReference<>();

        KafkaEventObserver observer = new KafkaEventObserver() {
            @Override public void logPublish(KafkaEventRoutingContext ctx, EventEnvelope<?> e) {}
            @Override public void logError(KafkaEventRoutingContext ctx, EventEnvelope<?> e, Throwable ex) {}
            @Override public void logDispatch(KafkaEventRoutingContext ctx, EventEnvelope<?> e) {
                logDispatchCalled.set(true);
                capturedContext.set(ctx);
            }
        };

        KafkaEventHandler<String> handler = new KafkaEventHandler<>() {
            @Override public String eventType() { return "chat.message.sent"; }
            @Override public void handle(EventEnvelope<String> event) {}
        };

        KafkaEventDispatcher dispatcher = new KafkaEventDispatcher(List.of(handler), observer);
        dispatcher.dispatch(envelope("chat.message.sent", "corr-1"));

        assertThat(logDispatchCalled.get()).isTrue();
        assertThat(capturedContext.get().eventType()).isEqualTo("chat.message.sent");
        assertThat(capturedContext.get().topic()).isNull();
    }

    @Test
    void dispatcher_callsObserverLogDispatchErrorOnHandlerFailure() {
        AtomicBoolean logDispatchErrorCalled = new AtomicBoolean(false);

        KafkaEventObserver observer = new KafkaEventObserver() {
            @Override public void logPublish(KafkaEventRoutingContext ctx, EventEnvelope<?> e) {}
            @Override public void logError(KafkaEventRoutingContext ctx, EventEnvelope<?> e, Throwable ex) {}
            @Override public void logDispatchError(KafkaEventRoutingContext ctx, EventEnvelope<?> e, Throwable ex) {
                logDispatchErrorCalled.set(true);
            }
        };

        KafkaEventHandler<String> handler = new KafkaEventHandler<>() {
            @Override public String eventType() { return "chat.message.sent"; }
            @Override public void handle(EventEnvelope<String> event) { throw new RuntimeException("handler failure"); }
        };

        KafkaEventDispatcher dispatcher = new KafkaEventDispatcher(List.of(handler), observer);

        assertThatThrownBy(() -> dispatcher.dispatch(envelope("chat.message.sent", "corr-1")))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("handler failure");
        assertThat(logDispatchErrorCalled.get()).isTrue();
    }

    @Test
    void sharedCatalog_registerAllPopulatesPayloadBearingEvents() {
        EventPayloadRegistry registry = new DefaultEventPayloadRegistry();
        SharedEventCatalog.registerAll(registry);

        assertThat(registry.contains(AccountEventType.ACCOUNT_CREATED.value())).isTrue();
        assertThat(registry.resolvePayload(ChatEventType.MESSAGE_SENT.value()))
                .isEqualTo(ChatMessagePayload.class);
    }

    @Test
    void sharedCatalog_payloadLessTypesNotRegistered() {
        EventPayloadRegistry registry = new DefaultEventPayloadRegistry();
        SharedEventCatalog.registerAll(registry);

        for (String payloadLessType : SharedEventCatalog.PAYLOAD_LESS_EVENT_TYPES) {
            assertThat(registry.contains(payloadLessType))
                    .as("payload-less event type should not be registered: " + payloadLessType)
                    .isFalse();
        }
    }

    @Test
    void sharedCatalog_registerAllIsIdempotentForSameClass() {
        EventPayloadRegistry registry = new DefaultEventPayloadRegistry();
        SharedEventCatalog.registerAll(registry);
        assertThatNoException().isThrownBy(() -> SharedEventCatalog.registerAll(registry));
    }

    @Test
    void sharedRegistry_registersAndResolvesPayloadClass() {
        EventPayloadRegistry registry = new DefaultEventPayloadRegistry();
        registry.register("chat.message.sent", String.class);

        assertThat(registry.resolvePayload("chat.message.sent")).isEqualTo(String.class);
        assertThat(registry.contains("chat.message.sent")).isTrue();
        assertThat(registry.contains("unknown")).isFalse();
    }

    @Test
    void sharedRegistry_idempotentRegistrationWithSameClass() {
        EventPayloadRegistry registry = new DefaultEventPayloadRegistry();
        registry.register("account.created", String.class);
        assertThatNoException().isThrownBy(() -> registry.register("account.created", String.class));
    }

    @Test
    void sharedRegistry_throwsOnConflictingRegistration() {
        EventPayloadRegistry registry = new DefaultEventPayloadRegistry();
        registry.register("account.created", String.class);

        assertThatThrownBy(() -> registry.register("account.created", Integer.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Conflicting");
    }

    @Test
    void sharedRegistry_throwsOnUnknownEventType() {
        EventPayloadRegistry registry = new DefaultEventPayloadRegistry();

        assertThatThrownBy(() -> registry.resolvePayload("unknown.event"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void publisher_rejectsBlankTopic() {
        KafkaTemplate<String, Object> kafkaTemplate = new KafkaTemplate<>(new StubProducerFactory());
        KafkaEventObserver logger = new KafkaEventObserver() {
            @Override public void logPublish(KafkaEventRoutingContext ctx, EventEnvelope<?> e) {}
            @Override public void logError(KafkaEventRoutingContext ctx, EventEnvelope<?> e, Throwable ex) {}
        };
        DefaultKafkaEventPublisher publisher = new DefaultKafkaEventPublisher(kafkaTemplate, logger);
        EventEnvelope<String> event = envelope("chat.message.sent", "corr-1");

        assertThatThrownBy(() -> publisher.publish("", "room-1", event))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("topic");

        assertThatThrownBy(() -> publisher.publish(null, "room-1", event))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("topic");
    }

    @Test
    void defaultKafkaEventPublisher_isAssignableToKafkaEventPublisher() {
        assertThat(KafkaEventPublisher.class).isAssignableFrom(DefaultKafkaEventPublisher.class);
    }

    private static final class StubProducerFactory implements ProducerFactory<String, Object> {
        @Override
        public Producer<String, Object> createProducer() {
            throw new UnsupportedOperationException("Producer should not be created in contract tests");
        }
    }
}
