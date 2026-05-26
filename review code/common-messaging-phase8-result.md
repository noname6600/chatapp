# Phase 8 Result: Kafka Consumer and Handler Contracts

**Date**: 2026-04-30  
**Status**: Complete  
**Scope**: `chatappBE/common/common-kafka`  
**Build**: `BUILD SUCCESSFUL` (no errors, deprecation notes only from prior phases)

---

## Summary

Phase 8 added the missing inbound (consumer-side) abstractions to `common-kafka`, achieving flow parity with the Redis event-oriented API introduced in Phase 7.  All changes are purely additive; no existing files were removed or restructured.

---

## New Files Created

### `consumer/` package

| File | Purpose |
|------|---------|
| `KafkaEventHandler<T>` | Per-event-type handler contract. Declares `eventType()` and `handle(IKafkaEvent<T>)`. Mirrors `IRedisSubscriber<T>` from `common-redis`. |
| `KafkaEventConsumer` | Marker/grouping interface for Spring `@Component` classes that host `@KafkaListener` methods. Provides `handlers()` (default: empty list) and `onUnhandledEvent()` hook. Optional to implement; existing consumers are unaffected. |
| `KafkaEventDispatcher` | Routes an incoming `IKafkaEvent` to the registered `KafkaEventHandler` by `eventType`. Transparent presence alias resolution (underscored legacy → hyphenated canonical) via `PresenceEventType`. Mirrors `RedisMessageDispatcher` in behaviour. |

### `registry/` package

| File | Purpose |
|------|---------|
| `KafkaEventRegistry` | Interface: `register(eventType, Class<?>)`, `resolvePayload(eventType)`, `contains(eventType)`. Mirrors `RedisEventRegistry`. |
| `DefaultKafkaEventRegistry` | `ConcurrentHashMap`-backed implementation. Presence alias resolution on `resolvePayload`. Duplicate registration throws `IllegalStateException`. |

### `serialization/` package

| File | Purpose |
|------|---------|
| `KafkaEventSerializer` | Interface: `byte[] serialize(String topic, IKafkaEvent<?> event)`. Mirrors `RedisEventSerializer` direction. |
| `KafkaEventDeserializer` | Interface: `IKafkaEvent<?> deserialize(String topic, byte[] data)`. Complements serializer; returns `null` for null data (tombstone records). |

---

## Modified Files

### `config/KafkaAutoConfiguration`

Two new beans added (both `@ConditionalOnMissingBean`):

| Bean | Type | Notes |
|------|------|-------|
| `kafkaEventRegistry` | `KafkaEventRegistry` | `DefaultKafkaEventRegistry` instance |
| `kafkaEventDispatcher` | `KafkaEventDispatcher` | Wired with all `KafkaEventHandler<?>` beans in context; safe with zero handlers |

---

## Compatibility Notes

- **Zero breaking changes**: All prior producer paths (`KafkaEventProducer`, `DefaultKafkaEventProducer`, `IKafkaEventPublisher`, `DefaultKafkaEventPublisher`) are unchanged.
- **Existing `@KafkaListener` consumers are unaffected**: `KafkaEventConsumer` and `KafkaEventDispatcher` are opt-in. Services continue using direct `@KafkaListener` methods without any migration required.
- **`KafkaEventDispatcher` with no handlers**: The auto-configured dispatcher is backed by an empty map and simply logs a warning for unmatched event types. It does not throw on startup.
- **Presence alias resolution**: Both `KafkaEventDispatcher` and `DefaultKafkaEventRegistry` apply the same two-level alias resolution used in the Redis layer, ensuring consistent wire-format tolerance across transports.
- **Serialization contracts are interfaces only**: No concrete `KafkaEventSerializer`/`KafkaEventDeserializer` implementations are provided in Phase 8. Services using Spring Kafka's built-in JSON deserialization continue to work. Concrete implementations are deferred to Phase 9+.

---

## Kafka ↔ Redis Flow Parity (after Phase 8)

| Concern | Redis (Phase 7) | Kafka (Phase 8) |
|---------|----------------|----------------|
| Event handler contract | `IRedisSubscriber<T>` / `RedisEventSubscriber<T>` | `KafkaEventHandler<T>` |
| Consumer marker | — | `KafkaEventConsumer` |
| Dispatcher | `RedisMessageDispatcher` / `RedisEventDispatcher` | `KafkaEventDispatcher` |
| Registry | `RedisEventRegistry` / `DefaultRedisEventRegistry` | `KafkaEventRegistry` / `DefaultKafkaEventRegistry` |
| Serializer | `RedisEventSerializer` / `JsonRedisEventSerializer` | `KafkaEventSerializer` (interface only) |
| Deserializer | (inside `JsonRedisEventSerializer`) | `KafkaEventDeserializer` (interface only) |

---

## Deferred to Phase 9+

- **Concrete `KafkaEventSerializer`/`KafkaEventDeserializer` implementations** (JSON-backed, leveraging `ObjectMapper`).
- **Package reorganisation**: Moving `common-kafka` classes into clean sub-packages (`api/`, `core/` → `producer/`, etc.) is out-of-scope until Phase 9.
- **Service-level migration**: Services replacing `@KafkaListener` + manual parsing with `KafkaEventConsumer` + `KafkaEventDispatcher` is a Phase 11 concern.
- **Contract tests** for the consumer/registry/serialization contracts are Phase 10.
- **`AbstractKafkaEvent`** is still in `com.example.common.integration.kafka.event` (wrong package). Relocation deferred to Phase 9 reorganisation.
