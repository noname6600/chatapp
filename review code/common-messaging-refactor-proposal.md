# Common Messaging Refactor Proposal

**Status**: Proposal  
**Date**: 2026-04-30  
**Scope**: `common-events`, `common-kafka`, `common-redis`  
**Based on**: `common-messaging-full-review.md`

---

## Executive Summary

This proposal unifies the fragmented event abstraction across three common modules. Currently, Kafka and Redis each define their own base event model, metadata shape, and serialization strategy, while `common-events` contains transport-specific constants that should not be there.

**Key Outcome**: After refactoring, `common-event` becomes the single source of truth for `Event`, `EventEnvelope`, `EventMetadata`, and event type validation. Kafka and Redis become transport-specific routing layers only.

**Approach**: Phased migration with backward-compatible adapter layers to avoid breaking downstream services during implementation.

**Risk Level**: Low-Medium. Changes are internal to common modules with no public API changes until services opt-in to new types.

---

## 1. Phased Migration Plan

### Phase 1: Add Shared Event Abstractions (Week 1)
**Goal**: Introduce unified event model without removing existing code.  
**Breaking Risk**: None—purely additive.

1.1 Add shared event interfaces and models to `common-events`
1.2 Keep all existing event type enums, payloads, and constants untouched
1.3 Add shared `EventContractValidator`
1.4 Services continue using old APIs; new code can opt-in to shared types

**Deliverables**:
- `com.example.common.event.Event<T>` interface
- `com.example.common.event.EventEnvelope<T>` record
- `com.example.common.event.EventMetadata` record
- `com.example.common.event.EventType` enum (empty or stub)
- `com.example.common.event.registry.EventRegistry<T>` interface
- `com.example.common.event.validation.EventContractValidator`

**Duration**: 2–3 days

---

### Phase 2: Normalize Metadata Field Names (Week 1)
**Goal**: Standardize timestamp field and identity field names globally.  
**Breaking Risk**: Low—compile errors only in common modules; services unaffected initially.

2.1 Update `Kafka` event metadata field names to match shared model
   - Ensure `eventId`, `eventType`, `sourceService`, `createdAt`, `correlationId` consistency
2.2 Update `Redis` message metadata field names to match shared model
   - Ensure explicit `eventId` and `correlationId` fields exist
2.3 Keep deprecated aliases (e.g., `FIELD_OCCURRED_AT`) in place for backward compatibility
2.4 Update all internal loggers and routing contexts to use new field names

**Deliverables**:
- Field name standardization in `AbstractKafkaEvent`
- Field name standardization in `AbstractRedisMessage` / `RedisMessage<T>`
- Updated `EventMetadata` record with all 5 fields
- Backward-compatibility aliases in serializers

**Duration**: 2–3 days

---

### Phase 3: Fix Event Type Values (Week 2)
**Goal**: Rename invalid event type values to comply with shared validator.  
**Breaking Risk**: Medium—affects Kafka topic routing and Redis channel deserialization. Requires deserializer migration logic.

3.1 Rename presence event type values:
   - `presence.user.status_changed` → `presence.user.status-changed`
   - `presence.room.stop_typing` → `presence.room.stop-typing`
   - `presence.global.online_users` → `presence.global.online-users`
   - `presence.room.online_users` → `presence.room.online-users`

3.2 Add deserializer aliases to accept old underscored values during wire format reads
3.3 Emit new hyphenated values on publish
3.4 Log warnings when old underscored values are received

**Deliverables**:
- Updated `PresenceEventType` enum
- Deserializer mapping: `underscored_old_value` → `hyphenated-new-value`
- Logging framework for deprecated value usage
- Test cases for round-trip compatibility

**Duration**: 1–2 days

---

### Phase 4: Move Transport-Specific Constants from common-events (Week 2)
**Goal**: Remove Redis channel constants and WebSocket terminology from shared event module.  
**Breaking Risk**: Low—all old locations remain as deprecated bridges. Services using old imports continue to work.

4.1 Move `com.example.common.integration.realtime.RealtimeRedisChannels` → `com.example.common.redis.channel.RedisChannels`
   - Keep old class as deprecated delegating bridge
4.2 Move Redis entries in `com.example.common.integration.realtime.RealtimeContractVersions` → Redis config
   - Keep old enum entries as deprecated aliases
4.3 Clean up `com.example.common.integration.realtime.RealtimeContractConventions`
   - Remove `CHANNEL_PREFIX_WS`
   - Remove `CHANNEL_PREFIX_REALTIME` if Redis-specific
   - Keep transport-neutral parts (e.g., field names)
4.4 Delete empty `com.example.common.integration.websocket` directory

**Deliverables**:
- New `RedisChannels` class in `common-redis`
- Deprecated bridge classes in `common-events`
- Cleaned `RealtimeContractConventions` (transport-neutral only)
- Updated imports in all services (with deprecation warnings during this phase)

**Duration**: 2–3 days

---

### Phase 5: Move Kafka Topics into Kafka Package (Week 2)
**Goal**: Relocate Kafka topic constants to transport-specific module.  
**Breaking Risk**: Low—old location remains as bridge.

5.1 Move `com.example.common.integration.kafka.KafkaTopics` → `com.example.common.kafka.topic.KafkaTopics`
5.2 Keep old location as deprecated delegating class
5.3 Update all Kafka producer code to use new location
5.4 Mark old location with `@Deprecated` and migration guide comment

**Deliverables**:
- New `com.example.common.kafka.topic.KafkaTopics` class
- Deprecated bridge at old location
- Updated producer/consumer references
- Migration guide in Javadoc

**Duration**: 1 day

---

### Phase 6: Rename Kafka Publisher API to Producer API (Week 3)
**Goal**: Align Kafka terminology with domain (Producer/Consumer pattern).  
**Breaking Risk**: Low—publisher API remains as deprecated adapter.

6.1 Create new producer package structure:
   - `com.example.common.kafka.producer.KafkaEventProducer` interface
   - `com.example.common.kafka.producer.DefaultKafkaEventProducer` implementation
6.2 Update implementation to accept `EventEnvelope<?>`
6.3 Keep old publisher classes with `@Deprecated` marker
6.4 Create adapter: `IKafkaEventPublisher` → new `KafkaEventProducer`
6.5 Update internal usage; services can migrate gradually

**Deliverables**:
- New `producer/` package with new interfaces
- Updated producer implementation using shared `EventEnvelope`
- Deprecated adapters (old `IKafkaEventPublisher`, etc.)
- Migration guide in Javadoc

**Duration**: 2–3 days

---

### Phase 7: Rename Redis Message API to Event API (Week 3)
**Goal**: Align Redis terminology with shared event model.  
**Breaking Risk**: Low—message API remains as deprecated wrapper.

7.1 Create new event-focused package structure:
   - `com.example.common.redis.publisher.RedisEventPublisher` interface
   - `com.example.common.redis.publisher.DefaultRedisEventPublisher` implementation
   - `com.example.common.redis.subscriber.RedisEventSubscriber<T>` interface
   - `com.example.common.redis.dispatcher.RedisEventDispatcher` (renamed from `RedisMessageDispatcher`)
   - `com.example.common.redis.listener.RedisEventListener` (renamed from `DefaultRedisMessageListener`)
   - `com.example.common.redis.serialization.RedisEventSerializer` interface
   - `com.example.common.redis.serialization.JsonRedisEventSerializer` implementation
   - `com.example.common.redis.registry.RedisEventRegistry` interface
   - `com.example.common.redis.registry.DefaultRedisEventRegistry` implementation

7.2 Update all new classes to work with `EventEnvelope<?>`
7.3 Create deprecated adapters for old `RedisMessage` API
7.4 Ensure deserialization preserves `eventId` and `correlationId`
7.5 Keep old message classes with `@Deprecated` marker

**Deliverables**:
- New event-oriented packages and classes
- Updated serializer that preserves identity fields
- Deprecated bridges for old message API
- Migration guide in Javadoc

**Duration**: 3–4 days

---

### Phase 8: Add Kafka Consumer and Handler Contracts (Week 4)
**Goal**: Add missing inbound abstractions to complete Kafka flow parity with Redis.  
**Breaking Risk**: None—purely additive.

8.1 Create consumer package:
   - `com.example.common.kafka.consumer.KafkaEventConsumer` interface
   - `com.example.common.kafka.consumer.KafkaEventHandler<T>` interface
8.2 Create registry package:
   - `com.example.common.kafka.registry.KafkaEventRegistry<T>` interface
   - `com.example.common.kafka.registry.DefaultKafkaEventRegistry` implementation
8.3 Create serialization package:
   - `com.example.common.kafka.serialization.KafkaEventSerializer` interface
   - `com.example.common.kafka.serialization.KafkaEventDeserializer` interface
8.4 Create listener adapter in consumer package for Spring Kafka integration
8.5 Implement event routing by `eventType` using registry

**Deliverables**:
- New `consumer/`, `registry/`, `serialization/` packages
- Event handler contract and routing dispatcher
- Integration with Spring Kafka listener container
- Contract tests

**Duration**: 3–4 days

---

### Phase 9: Reorganize Package Structure (Week 4)
**Goal**: Move code into clean, intended package hierarchy.  
**Breaking Risk**: Medium—requires search-and-replace in all consumer services.

9.1 Reorganize `common-kafka`:
   ```
   from: com.example.common.kafka.*
   to:   com.example.common.kafka.{producer,consumer,topic,serialization,registry,config,logging,error}
   ```
   with deprecated bridges at old locations

9.2 Reorganize `common-redis`:
   ```
   from: com.example.common.redis.*
   to:   com.example.common.redis.pubsub.{publisher,subscriber,listener,dispatcher,channel,serialization,registry,config,logging,error}
   ```
   with deprecated bridges at old locations

9.3 Reorganize `common-events`:
   ```
   from: com.example.common.integration.*
   to:   com.example.common.event.{metadata,payload,type,registry,validation,contract}
   ```
   Keep backward-compat bridges for critical imports

9.4 Update all internal imports in common modules

**Deliverables**:
- Reorganized package trees
- Deprecated bridges at old package locations
- Updated imports in common modules
- No service-level imports changed yet (bridges still work)

**Duration**: 2–3 days

---

### Phase 10: Add Contract Tests (Week 5)
**Goal**: Verify architectural compliance and migration correctness.  
**Breaking Risk**: None—test-only additions.

10.1 Create contract test suite:
   - Shared event envelope serialization round-trip
   - Kafka producer creates envelopes with correct metadata
   - Kafka consumer deserializes by event type
   - Redis publisher serializes shared envelope
   - Redis listener preserves `eventId` and `correlationId` after deserialize
   - Event type validator accepts all `EventType` enum values
   - Old wire format compatibility (underscored presence values)
   - Channel/topic constants not duplicated across modules

10.2 Add migration tests:
   - Old `IKafkaEventPublisher` delegates to new `KafkaEventProducer`
   - Old `RedisMessage` API delegates to new `RedisEvent` API
   - Deprecated bridges do not break imports

**Deliverables**:
- Test suite in each common module
- Contract test documentation
- CI integration

**Duration**: 3–4 days

---

### Phase 11: Migrate Service Imports (Weeks 6–8)
**Goal**: Update all consumer services to use new APIs and package structures.  
**Breaking Risk**: High during this phase; must coordinate with service teams.

11.1 For each service:
   - Replace old Kafka publisher imports with new producer imports
   - Replace old Redis message imports with new event imports
   - Update handler registration patterns (if applicable)
   - Verify compile and runtime behavior
   - Run service integration tests

11.2 Deprecation timeline:
   - Phase 11 completion: All services updated
   - Phase 12: Remove deprecated bridges (1 release cycle after all services updated)

**Duration**: 2–3 weeks (depends on service count and team capacity)

---

### Phase 12: Remove Deprecated Bridges (Release + 1 cycle)
**Goal**: Clean up old code and complete migration.  
**Breaking Risk**: None if Phase 11 complete.

12.1 After all services updated:
   - Delete deprecated adapter classes
   - Delete old package bridges
   - Remove migration guides from Javadoc
   - Update documentation to reflect new architecture

12.2 Archive/delete:
   - `com.example.common.integration.kafka.*`
   - `com.example.common.integration.realtime` (old classes)
   - `com.example.common.integration.websocket`
   - Old `com.example.common.kafka` packages (replaced by new structure)
   - Old `com.example.common.redis` packages (replaced by new structure)

**Duration**: 1 day (after service migration complete)

---

## 2. Exact Rename Map

### Kafka Renames

| Current Name | New Name | Location | Type | Status |
|---|---|---|---|---|
| `IKafkaEventPublisher` | `KafkaEventProducer` | `common-kafka` | Interface | Add new; deprecate old |
| `DefaultKafkaEventPublisher` | `DefaultKafkaEventProducer` | `common-kafka.producer` | Class | Add new; deprecate old |
| `KafkaEventPublisher` | (remove deprecated alias) | `common-kafka` | Deprecated Alias | Delete in Phase 12 |
| `KafkaEvent<T>` | (remove deprecated alias) | `common-kafka` | Deprecated Alias | Delete in Phase 12 |
| `com.example.common.integration.kafka.KafkaTopics` | `com.example.common.kafka.topic.KafkaTopics` | `common-kafka.topic` | Class | Move; bridge old location |
| `IKafkaEvent<T>` | (keep as deprecated; use `EventEnvelope<?>`internally) | `common-kafka.api` | Interface | Deprecate Phase 6 |
| `AbstractKafkaEvent` | (delete after shared `EventEnvelope`; use `EventEnvelope<Payload>`) | `common-kafka` | Class | Delete Phase 12 |
| `AccountCreatedEvent` | `AccountCreatedPayload` (wrapper removed; use `EventEnvelope<AccountCreatedPayload>`) | `common-event.payload.account` | Class | Rename Phase 6 |
| `ChatMessageSentEvent` | `ChatMessagePayload` | `common-event.payload.chat` | Class | Rename Phase 6 |
| `ChatMessageEditedEvent` | `ChatMessageEditedPayload` | `common-event.payload.chat` | Class | Rename Phase 6 |
| `ChatMessageDeletedEvent` | `ChatMessageDeletedPayload` | `common-event.payload.chat` | Class | Rename Phase 6 |
| `ChatReactionUpdatedEvent` | `ChatReactionPayload` | `common-event.payload.chat` | Class | Rename Phase 6 |
| `FriendshipEvent` | `FriendshipPayload` | `common-event.payload.friendship` | Class | Rename Phase 6 |
| `FriendRequestKafkaEvent` | (delete; use `EventEnvelope<FriendRequestPayload>`) | `common-kafka` | Class | Delete Phase 12 |
| `NotificationRequestedEvent` | `NotificationPayload` | `common-event.payload.notification` | Class | Rename Phase 6 |
| `KafkaEventLogger` | Refactor to use shared `EventMetadata` | `common-kafka.logging` | Class | Update Phase 2 |
| `KafkaEventRoutingContext` | Refactor to use shared `EventMetadata` | `common-kafka.flow` | Class | Update Phase 2 |

### Redis Renames

| Current Name | New Name | Location | Type | Status |
|---|---|---|---|---|
| `IRedisMessage` | `RedisEventEnvelope<T>` (or use shared `EventEnvelope<T>`) | `common-redis.pubsub` | Interface | Replace Phase 7 |
| `RedisMessage<T>` | `RedisEvent<T>` (if transport wrapper remains) | `common-redis.pubsub` | Class | Replace Phase 7 |
| `AbstractRedisMessage` | (delete; use shared `EventEnvelope`) | `common-redis` | Class | Delete Phase 12 |
| `DefaultRedisMessageListener` | `RedisEventListener` | `common-redis.pubsub.listener` | Class | Rename Phase 7 |
| `RedisMessageDispatcher` | `RedisEventDispatcher` | `common-redis.pubsub.dispatcher` | Class | Rename Phase 7 |
| `IRedisMessageSerializer` | `RedisEventSerializer` | `common-redis.pubsub.serialization` | Interface | Rename Phase 7 |
| `JsonRedisMessageSerializer` | `JsonRedisEventSerializer` | `common-redis.pubsub.serialization` | Class | Rename Phase 7 |
| `IRedisMessageRegistry` | `RedisEventRegistry` | `common-redis.pubsub.registry` | Interface | Rename Phase 7 |
| `DefaultRedisMessageRegistry` | `DefaultRedisEventRegistry` | `common-redis.pubsub.registry` | Class | Rename Phase 7 |
| `IRedisSubscriber` | (keep; rename to `RedisEventSubscriber<T>`) | `common-redis.pubsub.subscriber` | Interface | Rename Phase 7 |
| `RedisMessageFields` | `EventEnvelopeFields` (if shared) or `RedisEventFields` | `common-redis.pubsub.serialization` | Class | Rename Phase 7 |
| `RedisMessageDispatcher` (logger method) | Use `RedisPubSubLogger` | `common-redis.pubsub.logging` | Method/Class | Rename Phase 7 |
| `RedisPubSubLogger.logForward` | Rename to `logPublish` or remove WebSocket-specific language | `common-redis.pubsub.logging` | Method | Update Phase 7 |

### Common Event Renames / Additions

| Current Name | New Name | Location | Type | Status |
|---|---|---|---|---|
| (N/A) | `Event<T>` | `com.example.common.event` | Interface | Add Phase 1 |
| (N/A) | `EventEnvelope<T>` | `com.example.common.event` | Record | Add Phase 1 |
| (N/A) | `EventMetadata` | `com.example.common.event` | Record | Add Phase 1 |
| (N/A) | `EventType` | `com.example.common.event` | Enum | Add Phase 1 |
| (N/A) | `EventRegistry<T>` | `com.example.common.event.registry` | Interface | Add Phase 1 |
| (N/A) | `EventContractValidator` | `com.example.common.event.validation` | Class | Add Phase 1 |
| `RealtimeRedisChannels` | `RedisChannels` | `common-redis.pubsub.channel` | Class | Move Phase 4 |
| `RealtimeContractVersions` | Keep in `common-events` for backward-compat; move Redis entries to `RedisChannelConfig` | `common-redis.pubsub.config` | Class | Reorganize Phase 4 |
| `RealtimeContractConventions` | Clean; remove `CHANNEL_PREFIX_WS` and Redis-specific entries | `common-events` | Class | Clean Phase 4 |
| `PresenceEventType` | Fix value names (hyphenate underscores) | `com.example.common.event.type` | Enum | Update Phase 3 |

---

## 3. Exact Package Move Map

### Phase 4: Move Transport-Specific Constants

```
MOVE FROM → TO

com/example/common/integration/realtime/RealtimeRedisChannels.java
  → com/example/common/redis/pubsub/channel/RedisChannels.java
  
com/example/common/integration/realtime/RealtimeContractConventions.java (partial)
  → Keep only transport-neutral parts in common-events
  → Redis-specific constants → com/example/common/redis/pubsub/config/RedisChannelConfig.java

DELETE: com/example/common/integration/websocket/ (empty directory)

BRIDGE: Keep old location in common-events pointing to new location with @Deprecated marker
```

### Phase 5: Move Kafka Topics

```
MOVE FROM → TO

com/example/common/integration/kafka/KafkaTopics.java
  → com/example/common/kafka/topic/KafkaTopics.java

BRIDGE: Keep old location pointing to new location with @Deprecated marker
```

### Phase 6: Reorganize Kafka Structure

```
OLD STRUCTURE → NEW STRUCTURE

com/example/common/kafka/core/
  - DefaultKafkaEventPublisher.java
  - IKafkaEventPublisher.java
  → com/example/common/kafka/producer/
    - DefaultKafkaEventProducer.java (new)
    - KafkaEventProducer.java (new)
    - [deprecated bridges for old names]

com/example/common/kafka/api/
  - IKafkaEvent.java
  - KafkaEventRoutingContext.java
  → com/example/common/kafka/api/ (keep for compatibility, deprecate references)

com/example/common/kafka/event/
  - AbstractKafkaEvent.java
  - AccountCreatedEvent.java
  - ChatMessageSentEvent.java
  - etc.
  → DELETE or DEPRECATE (use EventEnvelope instead)

com/example/common/kafka/observability/
  → com/example/common/kafka/logging/

com/example/common/kafka/exception/
  → com/example/common/kafka/error/

NEW PACKAGES:
  com/example/common/kafka/consumer/
    - KafkaEventConsumer.java
    - KafkaEventHandler.java

  com/example/common/kafka/registry/
    - KafkaEventRegistry.java
    - DefaultKafkaEventRegistry.java

  com/example/common/kafka/serialization/
    - KafkaEventSerializer.java
    - KafkaEventDeserializer.java
```

### Phase 7: Reorganize Redis Structure

```
OLD STRUCTURE → NEW STRUCTURE

com/example/common/redis/message/
  - AbstractRedisMessage.java
  - RedisMessage.java
  → DELETE (use EventEnvelope instead)

com/example/common/redis/publisher/
  - DefaultRedisPublisher.java
  - IRedisPublisher.java
  → com/example/common/redis/pubsub/publisher/
    - DefaultRedisEventPublisher.java (new)
    - RedisEventPublisher.java (new)

com/example/common/redis/listener/
  - DefaultRedisMessageListener.java
  → com/example/common/redis/pubsub/listener/
    - RedisEventListener.java (renamed)

com/example/common/redis/dispatcher/
  - RedisMessageDispatcher.java
  → com/example/common/redis/pubsub/dispatcher/
    - RedisEventDispatcher.java (renamed)

com/example/common/redis/serialization/
  - IRedisMessageSerializer.java
  - JsonRedisMessageSerializer.java
  → com/example/common/redis/pubsub/serialization/
    - RedisEventSerializer.java (renamed)
    - JsonRedisEventSerializer.java (renamed)

com/example/common/redis/registry/
  - IRedisMessageRegistry.java
  - DefaultRedisMessageRegistry.java
  → com/example/common/redis/pubsub/registry/
    - RedisEventRegistry.java (renamed)
    - DefaultRedisEventRegistry.java (renamed)

com/example/common/redis/constants/
  → MERGE INTO serialization or delete if only message fields

com/example/common/redis/observability/
  → com/example/common/redis/pubsub/logging/

com/example/common/redis/exception/
  → com/example/common/redis/pubsub/error/

NEW PACKAGES:
  com/example/common/redis/pubsub/channel/
    - RedisChannels.java (moved from common-events)

  com/example/common/redis/pubsub/subscriber/
    - RedisEventSubscriber.java (renamed from IRedisSubscriber)
```

### Phase 1: Add to Common Events

```
NEW PACKAGES:
  com/example/common/event/
    - Event.java (interface)
    - EventEnvelope.java (record)
    - EventMetadata.java (record)
    - EventType.java (enum)

  com/example/common/event/payload/
    - (move existing domain payloads here)
    - account/AccountCreatedPayload.java
    - chat/ChatMessagePayload.java
    - chat/ChatMessageEditedPayload.java
    - chat/ChatMessageDeletedPayload.java
    - chat/ChatReactionPayload.java
    - friendship/FriendshipPayload.java
    - friendship/FriendRequestPayload.java
    - notification/NotificationPayload.java
    - user/UserPayload.java

  com/example/common/event/registry/
    - EventRegistry.java (interface)

  com/example/common/event/validation/
    - EventContractValidator.java
```

---

## 4. Compatibility Strategy

### For Services Using Kafka

**During Phases 1–6**:
- Old `IKafkaEventPublisher` and `DefaultKafkaEventPublisher` remain available
- Services continue importing from old locations without modification
- Internally, old publisher creates new producer instances under the hood

**Adapter Pattern**:
```java
// In common-kafka/core/ (old location, kept for compatibility)
@Deprecated(forRemoval = true)
public class DefaultKafkaEventPublisher implements IKafkaEventPublisher {
    private final KafkaEventProducer producer;
    
    public void publish(String topic, String key, IKafkaEvent<?> event) {
        // Convert IKafkaEvent to EventEnvelope
        EventEnvelope<?> envelope = convertToEnvelope(event);
        producer.publish(topic, key, envelope);
    }
}
```

**Migration Path**:
- Phase 11: Services update imports to new producer package
- Phase 12: Old adapter classes removed

### For Services Using Redis

**During Phases 1–7**:
- Old `IRedisMessage` and `RedisMessage<T>` remain available
- Old publisher/dispatcher/listener classes remain with deprecated markers
- Internally, old message classes wrap new event classes

**Adapter Pattern**:
```java
// In common-redis/ (old location, kept for compatibility)
@Deprecated(forRemoval = true)
public interface IRedisMessage {
    default Event<?> asEvent() {
        return new EventEnvelope(
            EventMetadata.of(this),
            getPayload()
        );
    }
}
```

**Migration Path**:
- Phase 11: Services update imports to new event classes
- Phase 12: Old message classes removed

### Wire Format Compatibility

**Kafka**:
- No wire format changes initially (Kafka serialization remains as-is)
- During Phase 6: Introduce optional shared serializer alongside existing
- Both deserializers work side-by-side
- New services can opt-in to new serializer; old ones continue with existing

**Redis**:
- Old Redis payloads with `messageId` field continue to deserialize
- New deserializer maps `messageId` → `eventId` if `eventId` missing
- Presence event type values: old underscored → new hyphenated (via deserializer alias mapping)
- During Phase 7: New serializer preserves `eventId` and `correlationId` on round-trip

**Backward-Compat Aliases**:
```java
// In new deserializer
private static final Map<String, String> EVENT_TYPE_ALIASES = Map.of(
    "presence.user.status_changed", "presence.user.status-changed",
    "presence.room.stop_typing", "presence.room.stop-typing",
    "presence.global.online_users", "presence.global.online-users",
    "presence.room.online_users", "presence.room.online-users"
);

// When deserializing: if old value found, log warning and map to new value
```

### Database / Cache Considerations

- No schema changes needed (Kafka and Redis don't store structured data)
- Old event payloads in flight will continue to work
- Recommend gradual migration of downstream subscribers to accept both old and new event types during Phase 11

---

## 5. Risk List

### High Risk

| Risk | Probability | Impact | Mitigation |
|---|---|---|---|
| Services break during import refactoring if deprecated bridges not in place | High | Critical | Create bridges in Phase 1; test with @Deprecated compiler warnings; coordinate Phase 11 rollout |
| Event type value change (presence underscored → hyphenated) causes deserializer failures | Medium | High | Implement deserializer aliases in Phase 3; log warnings; test in staging; coordinate with presence-service |
| Redis `eventId`/`correlationId` deserialization loss breaks traceability | Medium | High | Implement new serializer in Phase 7 with explicit field preservation; add contract tests; monitor logs for lost IDs |
| Kafka topics duplicated across old and new locations during transition | Low | Medium | Enforce single KafkaTopics class; deprecate old location early; lint check for duplicates in CI |

### Medium Risk

| Risk | Probability | Impact | Mitigation |
|---|---|---|---|
| Service migration (Phase 11) takes longer than planned; deprecated code has to stay longer | Medium | Medium | Start Phase 11 planning early; identify dependencies; coordinate with service teams; allow 2–3 week buffer |
| Old Kafka event wrapper classes (e.g., `AccountCreatedEvent`) still imported by services | High | Low | Mark with `@Deprecated` and migration guide; search codebase for imports early; provide replacement examples in PR description |
| Redis listener configuration breaks if old class no longer available | Low | Medium | Keep listener class through Phase 12; use adapter pattern if needed; document config migration in Phase 11 |
| Contract tests insufficient to catch edge cases in live traffic | Medium | Medium | Add chaos tests; run both old and new serializers in parallel in staging; monitor for differences during Phase 11 |

### Low Risk

| Risk | Probability | Impact | Mitigation |
|---|---|---|---|
| Package reorganization increases compile time | Low | Low | Batch compile; use incremental builds; acceptable given one-time cost |
| New shared EventEnvelope adoption by other services delayed | Low | Low | Make new APIs easy to use; provide examples; update documentation; include in architecture guide |
| Logging context inconsistency between old and new during transition | Low | Low | Standardize on shared `EventMetadata` fields for logging; update loggers in Phase 2 |

---

## 6. Compile-Safety Strategy

### Compile-Time Enforcement

**Goals**:
- No "silent" broken builds during migration
- Clear, actionable compiler errors when old APIs are used
- Gradual deprecation with visibility

### Approach

**Phase 1–2** (Additive; no breaking changes):
- Add new classes without removing old ones
- Services continue to compile without change
- Compiler warnings: none yet

**Phase 3–5** (Namespace cleanup):
- Old classes/packages marked with `@Deprecated`
- Compiler warnings on old imports: `@Deprecated` annotation warning
- Services continue to compile but see warnings
- CI: Warn about deprecated API usage (non-blocking)

**Phase 6–8** (Reorganization):
- Old classes remain; new structure live in parallel
- Both old and new compile and work
- Compiler warnings on old imports
- CI: Fail-on-deprecation flag off (non-blocking)

**Phase 9–10** (Test coverage):
- Contract tests validate both old and new APIs work
- No compile-time breakage yet

**Phase 11** (Service migration):
- Each service updates imports in coordinated PRs
- CI: Now fail-on-deprecation for services (blocking until fixed)
- Services see exact compile errors and clear migration paths
- Deprecation Javadoc and replacement examples provided

**Phase 12** (Cleanup):
- After all services updated, remove deprecated classes
- Services that missed migration will now have hard compile errors
- Clear error message points to migration guide

### Implementation Details

**Gradle Configuration**:
```gradle
// In common-kafka/build.gradle
compileJava {
    options.compilerArgs += [
        '-Xlint:all',
        '-Werror'  // Fail on warnings during Phase 11 migration
    ]
}
```

**Javadoc Deprecation Template**:
```java
/**
 * @deprecated Use {@link KafkaEventProducer} instead.
 *             Migration guide: Replace IKafkaEventPublisher imports with KafkaEventProducer.
 *             Old: new DefaultKafkaEventPublisher(template).publish(topic, key, event)
 *             New: new DefaultKafkaEventProducer(template).publish(topic, key, envelope)
 */
@Deprecated(forRemoval = true, since = "2.0")
public interface IKafkaEventPublisher { ... }
```

**IDE Hints**:
- Add inspection: "Discouraged API usage" highlighting old classes
- Provide quick-fix: Suggest new import and usage pattern

### Testing for Compilation Safety

**Contract tests to run at each phase**:
1. Old APIs compile and work (Phase 1–11)
2. New APIs compile and work (Phase 1+)
3. No duplicate definitions of same class (Phase 9+)
4. All imports resolve correctly (Phase 9+)
5. Deprecated imports produce compiler warnings (Phase 3+)

---

## 7. Contract-Test Plan

### Test Scope

**Goal**: Verify architectural compliance and correctness across the refactoring.

### Test Organization

Create new test module: `common-messaging-contract-tests/`

### Test Categories

#### 1. Shared Event Model Tests
**Package**: `com.example.common.event.contract`

```java
// Test: EventEnvelope serialization round-trip
// Verify: EventEnvelope<T> with EventMetadata serializes/deserializes correctly
@Test
void testEventEnvelopeSerializationRoundTrip() {
    EventMetadata metadata = new EventMetadata("evt-123", "chat.message.sent", 
        "chat-service", Instant.now(), "corr-456");
    ChatMessagePayload payload = new ChatMessagePayload(/* ... */);
    EventEnvelope<ChatMessagePayload> envelope = new EventEnvelope<>(metadata, payload);
    
    String json = objectMapper.writeValueAsString(envelope);
    EventEnvelope<?> deserialized = objectMapper.readValue(json, EventEnvelope.class);
    
    assertEquals(metadata.eventId(), deserialized.metadata().eventId());
    assertEquals(metadata.correlationId(), deserialized.metadata().correlationId());
    assertEquals(payload, deserialized.payload());
}

// Test: EventType validator accepts all enum values
@Test
void testEventTypeValidationAcceptsAllTypes() {
    EventContractValidator validator = new EventContractValidator();
    for (EventType type : EventType.values()) {
        assertTrue(validator.isValidEventType(type.value()),
            "EventType." + type.name() + " failed validation");
    }
}

// Test: Event type naming pattern enforced
@Test
void testEventTypeNamingPatternEnforced() {
    String pattern = EventContractValidator.EVENT_NAME_PATTERN;
    assertTrue(pattern.matches("chat.message.sent"));
    assertTrue(pattern.matches("presence.user.status-changed")); // hyphenated OK
    assertFalse(pattern.matches("presence.user.status_changed")); // underscored NOT OK
}
```

#### 2. Kafka Producer Tests
**Package**: `com.example.common.kafka.contract`

```java
// Test: Producer creates EventEnvelope with correct metadata
@Test
void testProducerMetadataCorrectness() {
    KafkaEventProducer producer = new DefaultKafkaEventProducer(kafkaTemplate);
    
    EventMetadata metadata = new EventMetadata("evt-123", "account.created", 
        "auth-service", Instant.now(), "corr-456");
    AccountCreatedPayload payload = new AccountCreatedPayload(/* ... */);
    EventEnvelope<AccountCreatedPayload> envelope = new EventEnvelope<>(metadata, payload);
    
    producer.publish("account-events", "user-123", envelope);
    
    // Verify producer called kafkaTemplate with correct topic/key
    verify(kafkaTemplate).send(eq("account-events"), eq("user-123"), any());
}

// Test: Producer accepts shared EventEnvelope type
@Test
void testProducerAcceptsEventEnvelope() {
    KafkaEventProducer producer = new DefaultKafkaEventProducer(kafkaTemplate);
    
    // This should compile and not require IKafkaEvent
    EventEnvelope<?> envelope = new EventEnvelope<>(/* ... */);
    assertDoesNotThrow(() -> producer.publish("topic", "key", envelope));
}

// Test: Deprecated publisher delegates to new producer
@Test
@Deprecated
void testDeprecatedPublisherDelegatesToProducer() {
    IKafkaEventPublisher publisher = new DefaultKafkaEventPublisher(kafkaTemplate);
    IKafkaEvent<?> event = new TestKafkaEvent(/* ... */);
    
    publisher.publish("topic", "key", event);
    
    verify(kafkaTemplate).send(any(String.class), any(String.class), any());
}
```

#### 3. Kafka Consumer Tests
**Package**: `com.example.common.kafka.contract`

```java
// Test: Consumer deserializes by event type
@Test
void testConsumerDeserializesByEventType() {
    KafkaEventRegistry registry = new DefaultKafkaEventRegistry();
    registry.register("account.created", AccountCreatedPayload.class);
    
    String eventJson = objectMapper.writeValueAsString(new EventEnvelope<>(
        new EventMetadata("evt-123", "account.created", "auth-service", Instant.now(), "corr-456"),
        new AccountCreatedPayload(/* ... */)
    ));
    
    KafkaEventDeserializer deserializer = new KafkaEventDeserializer(registry, objectMapper);
    EventEnvelope<?> deserialized = deserializer.deserialize("account-events", eventJson);
    
    assertEquals("account.created", deserialized.metadata().eventType());
    assertInstanceOf(AccountCreatedPayload.class, deserialized.payload());
}

// Test: Handler receives deserialized event
@Test
void testHandlerReceivesDeserializedEvent() {
    KafkaEventHandler<AccountCreatedPayload> handler = mockHandler();
    
    EventMetadata metadata = new EventMetadata("evt-123", "account.created", 
        "auth-service", Instant.now(), "corr-456");
    AccountCreatedPayload payload = new AccountCreatedPayload(/* ... */);
    EventEnvelope<AccountCreatedPayload> envelope = new EventEnvelope<>(metadata, payload);
    
    handler.handle(envelope);
    
    verify(handler).handle(argThat(e -> e.metadata().eventId().equals("evt-123")));
}
```

#### 4. Redis Publisher Tests
**Package**: `com.example.common.redis.pubsub.contract`

```java
// Test: Publisher serializes shared EventEnvelope
@Test
void testPublisherSerializesEventEnvelope() {
    RedisEventPublisher publisher = new DefaultRedisEventPublisher(redisTemplate, serializer);
    
    EventMetadata metadata = new EventMetadata("evt-123", "presence.user.online", 
        "presence-service", Instant.now(), "corr-456");
    PresencePayload payload = new PresencePayload(/* ... */);
    EventEnvelope<PresencePayload> envelope = new EventEnvelope<>(metadata, payload);
    
    publisher.publish("presence:updates", envelope);
    
    verify(redisTemplate).convertAndSend(eq("presence:updates"), any());
}

// Test: Publisher validates event type with shared validator
@Test
void testPublisherValidatesEventType() {
    RedisEventPublisher publisher = new DefaultRedisEventPublisher(redisTemplate, serializer);
    
    // Invalid: underscore in event type
    EventMetadata badMetadata = new EventMetadata("evt-123", "presence.user.status_changed", 
        "presence-service", Instant.now(), "corr-456");
    EventEnvelope<?> badEnvelope = new EventEnvelope<>(badMetadata, new Object());
    
    assertThrows(InvalidEventTypeException.class, 
        () -> publisher.publish("presence:updates", badEnvelope));
}
```

#### 5. Redis Listener Tests
**Package**: `com.example.common.redis.pubsub.contract`

```java
// Test: Listener preserves eventId and correlationId on deserialize
@Test
void testListenerPreservesIdentityMetadata() {
    String json = objectMapper.writeValueAsString(new EventEnvelope<>(
        new EventMetadata("evt-123", "presence.user.online", "presence-service", Instant.now(), "corr-456"),
        new PresencePayload(/* ... */)
    ));
    
    RedisEventSerializer serializer = new JsonRedisEventSerializer(objectMapper);
    EventEnvelope<?> deserialized = serializer.deserialize(json);
    
    assertEquals("evt-123", deserialized.metadata().eventId());
    assertEquals("corr-456", deserialized.metadata().correlationId());
}

// Test: Listener accepts old presence event type values (backward compat)
@Test
void testListenerAcceptsOldPresenceEventTypes() {
    String oldJson = objectMapper.writeValueAsString(new EventEnvelope<>(
        new EventMetadata("evt-123", "presence.user.status_changed", // old underscored value
            "presence-service", Instant.now(), "corr-456"),
        new PresencePayload(/* ... */)
    ));
    
    RedisEventSerializer serializer = new JsonRedisEventSerializer(objectMapper);
    EventEnvelope<?> deserialized = serializer.deserialize(oldJson);
    
    // Should map to new hyphenated value
    assertEquals("presence.user.status-changed", deserialized.metadata().eventType());
}

// Test: Dispatcher routes by event type
@Test
void testDispatcherRoutesByEventType() {
    RedisEventDispatcher dispatcher = new RedisEventDispatcher();
    RedisEventSubscriber<PresencePayload> subscriber = mockSubscriber();
    
    dispatcher.subscribe("presence.user.online", subscriber);
    
    EventMetadata metadata = new EventMetadata("evt-123", "presence.user.online", 
        "presence-service", Instant.now(), "corr-456");
    EventEnvelope<PresencePayload> envelope = new EventEnvelope<>(metadata, new PresencePayload(/* ... */));
    
    dispatcher.dispatch(envelope);
    
    verify(subscriber).onEvent(envelope);
}
```

#### 6. Backward Compatibility Tests
**Package**: `com.example.common.contract.compatibility`

```java
// Test: Old Kafka publisher adapter still works
@Test
@Deprecated
void testOldKafkaPublisherAdapter() {
    IKafkaEventPublisher publisher = new DefaultKafkaEventPublisher(kafkaTemplate);
    IKafkaEvent<?> event = new TestKafkaEvent();
    
    assertDoesNotThrow(() -> publisher.publish("topic", "key", event));
}

// Test: Old Redis message type still deserializes
@Test
@Deprecated
void testOldRedisMessageDeserialization() {
    String oldJson = objectMapper.writeValueAsString(
        new RedisMessage<>(
            "msg-123",
            "evt-123",
            "corr-456",
            "presence.user.online",
            "presence-service",
            Instant.now(),
            new PresencePayload(/* ... */)
        )
    );
    
    // Deserializer should handle old messageId → eventId mapping
    EventEnvelope<?> envelope = deserializer.deserialize(oldJson);
    assertEquals("evt-123", envelope.metadata().eventId());
}

// Test: Presence event type rename maps old → new
@Test
void testPresenceEventTypeMapping() {
    Map<String, String> aliases = PresenceEventTypeAliases.getAliases();
    
    assertEquals("presence.user.status-changed", 
        aliases.get("presence.user.status_changed"));
    assertEquals("presence.room.stop-typing", 
        aliases.get("presence.room.stop_typing"));
}
```

#### 7. No-Duplication Tests
**Package**: `com.example.common.contract.structure`

```java
// Test: KafkaTopics only defined in one location
@Test
void testKafkaTopicsNotDuplicated() {
    // Verify only one class with name "KafkaTopics" exists in classpath
    // OR verify old location is deprecated bridge only
    
    Class<?> oldLocation = Class.forName("com.example.common.integration.kafka.KafkaTopics");
    Class<?> newLocation = Class.forName("com.example.common.kafka.topic.KafkaTopics");
    
    // New location is the real implementation
    assertNotEquals(oldLocation, newLocation);
    assertTrue(isDeprecatedBridge(oldLocation));
}

// Test: RedisChannels only defined in redis module
@Test
void testRedisChannelsNotInCommonEvents() {
    assertThrows(ClassNotFoundException.class, 
        () -> Class.forName("com.example.common.integration.realtime.RealtimeRedisChannels"));
    
    Class<?> redisLocation = Class.forName("com.example.common.redis.pubsub.channel.RedisChannels");
    assertNotNull(redisLocation);
}

// Test: No domain-specific event wrappers in kafka module
@Test
void testNoDomainEventWrappersInKafka() {
    // These classes should NOT exist in com.example.common.kafka
    String[] forbiddenClasses = {
        "com.example.common.kafka.event.AccountCreatedEvent",
        "com.example.common.kafka.event.ChatMessageSentEvent"
    };
    
    for (String className : forbiddenClasses) {
        assertThrows(ClassNotFoundException.class, () -> Class.forName(className),
            "Domain event wrapper " + className + " should not exist in kafka module");
    }
}
```

#### 8. Event Type Validator Tests
**Package**: `com.example.common.event.contract`

```java
// Test: All EventType enum values pass validator
@Test
void testAllEventTypesPassValidator() {
    EventContractValidator validator = new EventContractValidator();
    
    for (EventType type : EventType.values()) {
        assertTrue(validator.isValidEventType(type.value()),
            "EventType " + type.name() + " with value '" + type.value() + "' failed validation");
    }
}

// Test: Validator pattern matches expected format
@Test
void testValidatorPatternCorrect() {
    EventContractValidator validator = new EventContractValidator();
    
    // Valid: lowercase.dot.separated.hyphenated-names
    assertTrue(validator.isValidEventType("chat.message.sent"));
    assertTrue(validator.isValidEventType("presence.user.status-changed"));
    assertTrue(validator.isValidEventType("notification.email-sent"));
    
    // Invalid: underscores, uppercase, spaces
    assertFalse(validator.isValidEventType("chat.message_sent"));
    assertFalse(validator.isValidEventType("Chat.Message.Sent"));
    assertFalse(validator.isValidEventType("chat message sent"));
}
```

### Test Execution Plan

**Phase 1–2**: Run all tests to establish baseline
```bash
mvn clean test -Dtest=com.example.common.event.contract.*
mvn clean test -Dtest=com.example.common.kafka.contract.*
mvn clean test -Dtest=com.example.common.redis.pubsub.contract.*
```

**Phase 3–12**: Run all tests after each phase
```bash
mvn clean test -pl common-messaging-contract-tests
```

**CI Integration**: 
- Fail build if any contract test fails
- Fail build if deprecated API coverage < 95%
- Report on old vs new API usage trends

**Coverage Report**:
- Both old and new APIs covered
- Backward-compat code exercised
- Edge cases (null metadata, missing payload) handled

---

## 8. Recommended Implementation Order

### Priority 1: Foundation (Week 1, Days 1–3)
**Goal**: Establish shared event model without breaking anything.

1. **Phase 1: Add Shared Event Abstractions**
   - Create `common-event` base interfaces and records
   - Add `EventContractValidator`
   - Add stub `EventType` enum
   - **Duration**: 2–3 days
   - **Blockers**: None (purely additive)
   - **Review**: Core team; architecture validation

### Priority 2: Clean Up Constants (Week 1, Days 4–5)
**Goal**: Remove transport-specific constants from shared module.

2. **Phase 4: Move Transport-Specific Constants from common-events**
   - Move `RealtimeRedisChannels` to redis module
   - Clean up `RealtimeContractConventions` (remove WebSocket terminology)
   - Create deprecated bridges in common-events
   - **Duration**: 2–3 days
   - **Blockers**: None (bridges keep old imports working)
   - **Review**: Architecture team

3. **Phase 5: Move Kafka Topics**
   - Create `kafka.topic.KafkaTopics`
   - Create deprecated bridge at old location
   - Update Kafka producer to use new location
   - **Duration**: 1 day
   - **Blockers**: None (bridge keeps old imports working)
   - **Review**: Kafka service owners (optional)

### Priority 3: Normalize Metadata (Week 2, Days 1–3)
**Goal**: Standardize field names globally.

4. **Phase 2: Normalize Metadata Field Names**
   - Update Kafka event metadata fields (eventId, createdAt, etc.)
   - Update Redis message metadata fields
   - Update loggers and routing contexts
   - Add backward-compat aliases in serializers
   - **Duration**: 2–3 days
   - **Blockers**: None (services unaffected due to backward compat)
   - **Review**: Core team + service leads

### Priority 4: Fix Event Type Values (Week 2, Days 4–5)
**Goal**: Ensure all event types comply with validator.

5. **Phase 3: Fix Event Type Values**
   - Rename presence event types (underscores → hyphens)
   - Add deserializer aliases for old underscored values
   - Test presence service integration
   - **Duration**: 1–2 days
   - **Blockers**: Presence service sign-off
   - **Review**: Presence service owners + core team
   - **Testing**: Presence service staging tests

### Priority 5: API Alignment (Week 3–4)
**Goal**: Rename Kafka/Redis APIs for clarity and consistency.

6. **Phase 6: Rename Kafka Publisher API to Producer API**
   - Create `producer/` package structure
   - Implement new `KafkaEventProducer` interface
   - Create adapter for old `IKafkaEventPublisher`
   - Update Kafka producer usage internally
   - **Duration**: 2–3 days
   - **Blockers**: None (old API still available)
   - **Review**: Kafka service owners (for internal updates)

7. **Phase 7: Rename Redis Message API to Event API**
   - Create new event-focused packages
   - Implement new `RedisEventPublisher`, `RedisEventDispatcher`, etc.
   - Fix serializer to preserve `eventId` and `correlationId`
   - Create adapters for old `RedisMessage` API
   - **Duration**: 3–4 days
   - **Blockers**: Thorough testing of deserialization changes
   - **Review**: Redis/presence service owners + core team
   - **Testing**: Staging tests for backward compat

### Priority 6: Complete Kafka Flow (Week 4)
**Goal**: Add missing inbound abstractions to Kafka.

8. **Phase 8: Add Kafka Consumer and Handler Contracts**
   - Create `consumer/`, `registry/`, `serialization/` packages
   - Implement consumer adapter and handler interface
   - Implement event registry and deserializer
   - Wire into Spring Kafka listener container
   - **Duration**: 3–4 days
   - **Blockers**: None (purely new APIs)
   - **Review**: Core team + Kafka service leads

### Priority 7: Structural Reorganization (Week 4–5)
**Goal**: Move code into final package hierarchy.

9. **Phase 9: Reorganize Package Structure**
   - Move Kafka code into `producer/`, `consumer/`, `topic/`, etc.
   - Move Redis code into `pubsub/` hierarchy
   - Move common event payloads into `event/payload/` hierarchy
   - Create deprecated bridges at old package locations
   - Update all internal imports in common modules
   - **Duration**: 2–3 days
   - **Blockers**: Completion of Phases 1–8
   - **Review**: Full team; package structure approval

### Priority 8: Test Coverage (Week 5)
**Goal**: Verify architectural compliance.

10. **Phase 10: Add Contract Tests**
    - Write test suite for shared event model
    - Write Kafka producer/consumer tests
    - Write Redis publisher/listener tests
    - Write backward-compat tests
    - Write no-duplication tests
    - **Duration**: 3–4 days
    - **Blockers**: Completion of Phases 1–9
    - **Review**: QA + core team

### Priority 9: Service Migration (Weeks 6–8)
**Goal**: Update all consumer services to new APIs.

11. **Phase 11: Migrate Service Imports**
    - Identify all services importing old APIs (automated scan)
    - For each service:
      - Update imports to new packages/classes
      - Run compile and integration tests
      - Deploy to staging
      - Verify in staging
    - **Duration**: 2–3 weeks (depends on service count)
    - **Blockers**: Staging test results
    - **Review**: Each service's owning team
    - **Rollout**: Coordinated with release cycle

### Priority 10: Cleanup (Post-Release)
**Goal**: Remove deprecated code after services updated.

12. **Phase 12: Remove Deprecated Bridges**
    - Verify all services updated in Phase 11
    - Delete deprecated adapter classes
    - Delete old package bridges
    - Remove migration Javadoc
    - Archive old event type aliases
    - **Duration**: 1 day
    - **Blockers**: Confirmation that all services migrated
    - **Review**: Full team
    - **Release**: Next major version bump

---

## 9. Sequencing Constraints & Dependencies

**Hard Dependencies** (must complete in order):
- Phase 1 must complete before Phase 2 (need shared model to normalize to)
- Phase 2 must complete before Phase 3 (need consistent field names to fix event types)
- Phases 1–5 must complete before Phase 6–8 (need foundation for API changes)
- Phases 1–8 must complete before Phase 9 (need all new code in place before moving packages)
- Phases 1–9 must complete before Phase 10 (need tests passing to verify changes)
- Phases 1–10 must complete before Phase 11 (services can't migrate until all refactoring done)
- Phase 11 must complete before Phase 12 (can't remove old APIs until services updated)

**Soft Dependencies** (can overlap):
- Phase 6 and Phase 7 can run in parallel after Phase 5 (independent Kafka/Redis changes)
- Phase 8 can start as soon as Phase 7 complete (independent Kafka consumer work)
- Contract test writing (Phase 10) can start as soon as Phase 8 complete

**Critical Path** (minimum time):
- Week 1: Phases 1, 4, 5
- Week 2: Phases 2, 3
- Week 3: Phases 6, 7
- Week 4: Phases 8, 9
- Week 5: Phase 10
- Weeks 6–8: Phase 11 (depends on service count; run in parallel per service)
- Post-release: Phase 12

---

## 10. Deliverables Checklist

### By Phase

- [ ] **Phase 1**: New `com.example.common.event.*` packages and core classes added to common-events
- [ ] **Phase 2**: All metadata field names normalized across Kafka/Redis; backward-compat aliases in place
- [ ] **Phase 3**: Presence event types renamed (underscores → hyphens); deserializer aliases working
- [ ] **Phase 4**: `RealtimeRedisChannels` moved to redis module; old location is deprecated bridge; `RealtimeContractConventions` cleaned
- [ ] **Phase 5**: `KafkaTopics` moved to `kafka.topic` package; old location is deprecated bridge
- [ ] **Phase 6**: New `producer/` package created; `KafkaEventProducer` interface/implementation added; deprecated adapters for old publisher API
- [ ] **Phase 7**: New `redis.pubsub.*` packages created with event-focused classes; old message API wrapped with adapters; serializer preserves identity metadata
- [ ] **Phase 8**: `consumer/`, `registry/`, `serialization/` packages added to Kafka; consumer handler contract and routing implemented
- [ ] **Phase 9**: Package reorganization complete; all old locations have deprecated bridges; no service imports changed yet (bridges still work)
- [ ] **Phase 10**: Contract test suite written and passing; old and new APIs both tested; backward-compat verified
- [ ] **Phase 11**: All services updated to new APIs; staged and verified; all old imports removed from service code
- [ ] **Phase 12**: Deprecated bridges removed; old package locations deleted; migration guides removed from Javadoc

### Files to Create / Modify

**New Files Created**:
- `chatappBE/common/common-event/src/main/java/com/example/common/event/*.java` (Event, EventEnvelope, EventMetadata, EventType, etc.)
- `chatappBE/common/common-event/src/main/java/com/example/common/event/registry/*.java`
- `chatappBE/common/common-event/src/main/java/com/example/common/event/validation/*.java`
- `chatappBE/common/common-kafka/src/main/java/com/example/common/kafka/producer/*.java` (new producer API)
- `chatappBE/common/common-kafka/src/main/java/com/example/common/kafka/consumer/*.java` (new consumer API)
- `chatappBE/common/common-kafka/src/main/java/com/example/common/kafka/topic/*.java` (topics moved here)
- `chatappBE/common/common-kafka/src/main/java/com/example/common/kafka/registry/*.java`
- `chatappBE/common/common-kafka/src/main/java/com/example/common/kafka/serialization/*.java`
- `chatappBE/common/common-redis/src/main/java/com/example/common/redis/pubsub/publisher/*.java` (new publisher API)
- `chatappBE/common/common-redis/src/main/java/com/example/common/redis/pubsub/subscriber/*.java`
- `chatappBE/common/common-redis/src/main/java/com/example/common/redis/pubsub/listener/*.java`
- `chatappBE/common/common-redis/src/main/java/com/example/common/redis/pubsub/dispatcher/*.java` (renamed)
- `chatappBE/common/common-redis/src/main/java/com/example/common/redis/pubsub/channel/*.java` (moved from common-events)
- `chatappBE/common/common-redis/src/main/java/com/example/common/redis/pubsub/registry/*.java`
- `chatappBE/common/common-redis/src/main/java/com/example/common/redis/pubsub/serialization/*.java` (renamed)
- `chatappBE/common/common-messaging-contract-tests/...` (entire test module)

**Files Modified** (additive or non-breaking):
- `chatappBE/common/common-events/src/main/java/com/example/common/integration/realtime/RealtimeRedisChannels.java` (deprecated bridge added)
- `chatappBE/common/common-events/src/main/java/com/example/common/integration/realtime/RealtimeContractConventions.java` (WebSocket terminology removed; bridge added for removed fields)
- `chatappBE/common/common-events/src/main/java/com/example/common/integration/kafka/KafkaTopics.java` (deprecated bridge added)
- `chatappBE/common/common-kafka/src/main/java/com/example/common/kafka/api/IKafkaEventPublisher.java` (deprecated marker added)
- `chatappBE/common/common-kafka/src/main/java/com/example/common/kafka/core/DefaultKafkaEventPublisher.java` (deprecated marker; updated to use new producer internally)
- `chatappBE/common/common-redis/src/main/java/com/example/common/redis/message/*.java` (deprecated markers; adapters added)
- `chatappBE/common/common-redis/src/main/java/com/example/common/redis/serialization/JsonRedisMessageSerializer.java` (updated to preserve eventId/correlationId)

**Files Deleted** (only in Phase 12, after service migration complete):
- All deprecated bridge files
- `chatappBE/common/common-events/src/main/java/com/example/common/integration/websocket/`
- Old `com/example/common/kafka/api/` classes (after services migrated)
- Old `com/example/common/kafka/core/` classes (after services migrated)
- Old `com/example/common/kafka/event/` classes (after services migrated)
- Old `com/example/common/redis/message/` classes (after services migrated)
- Old `com/example/common/redis/publisher/` classes (after services migrated)
- Etc. (full cleanup list in Phase 12)

---

## 11. Success Criteria

### Architectural Outcomes

✅ `common-event` is the single source of truth for:
   - `Event<T>` interface
   - `EventEnvelope<T>` record
   - `EventMetadata` record
   - Shared `EventType` enum
   - Event type validation rules
   - Event registry contract

✅ `common-kafka` is transport-specific only:
   - Contains `producer/`, `consumer/`, `topic/`, `registry/`, `serialization/`, `logging/`, `error/` packages
   - Contains zero domain-specific event wrappers
   - Contains zero transport-specific constants from common-events
   - Uses shared `EventEnvelope<T>` and `EventMetadata`

✅ `common-redis` is transport-specific only:
   - Contains `pubsub/publisher/`, `pubsub/subscriber/`, `pubsub/listener/`, `pubsub/dispatcher/`, `pubsub/channel/`, `pubsub/registry/`, `pubsub/serialization/`, `pubsub/logging/`, `pubsub/error/` packages
   - Contains zero Redis-specific constants in common-events
   - Uses shared `EventEnvelope<T>` and `EventMetadata`
   - Preserves `eventId` and `correlationId` on round-trip serialization

✅ Backward Compatibility:
   - All old APIs remain available through Phase 11
   - Services can migrate gradually without forced refactoring
   - Wire format compatibility maintained (old presence event types, old messageId → eventId mappings)
   - Deprecated bridges delegate to new implementations

✅ Quality:
   - All contract tests pass
   - No compile errors in any phase
   - Backward-compat coverage > 95%
   - Old and new APIs tested in parallel

✅ Documentation:
   - Migration guide in Javadoc for each deprecated class
   - Replacement examples provided
   - Package structure documented in architecture guide
   - Contract test documentation explains test coverage

---

## 12. Risk Mitigation Timeline

| Phase | Risk | Mitigation Action | Owner |
|-------|------|-------------------|-------|
| 1 | Shared model unclear | Design review with core team before implementation | Architecture |
| 2 | Metadata normalization breaks serializers | Extensive backward-compat testing; deserializer aliases | Core team |
| 3 | Event type rename breaks presence service | Coordinate with presence-service; deserializer aliases; staging test | Presence + Core |
| 4 | Services still importing old locations | Keep deprecated bridges through Phase 11; CI warnings | Core team |
| 5–7 | API renames cause confusion | Comprehensive migration guides in Javadoc; examples | Core team |
| 8 | Kafka consumer implementation incomplete | Comprehensive contract tests before Phase 9 | Kafka team |
| 9 | Package reorganization breaks imports | Deprecated bridges at all old locations; scan codebase for stragglers | Core team |
| 10 | Contract tests insufficient | Chaos tests; staging validation; parallel old/new comparison | QA + Core |
| 11 | Service migration slower than planned | Start service planning in Phase 1; identify blockers early; buffer time | Service leads |
| 12 | Delayed service migration keeps old code alive | Set hard cutoff date; escalate blocked services; support rollback plan | Program lead |

---

## Appendix A: File Structure Reference

### Final `common-event` Structure (After All Phases)
```
chatappBE/common/common-event/
  src/main/java/com/example/common/event/
    Event.java
    EventEnvelope.java
    EventMetadata.java
    EventType.java (or EventTypeRegistry.java)
    
    payload/
      account/AccountCreatedPayload.java
      chat/ChatMessagePayload.java
      chat/ChatMessageEditedPayload.java
      chat/ChatMessageDeletedPayload.java
      chat/ChatReactionPayload.java
      friendship/FriendshipPayload.java
      friendship/FriendRequestPayload.java
      notification/NotificationPayload.java
      presence/PresencePayload.java
      user/UserPayload.java
    
    registry/
      EventRegistry.java
      EventTypeRegistry.java
    
    validation/
      EventContractValidator.java
    
    contract/
      RealtimeContractConventions.java (cleaned)
      RealtimeContractVersions.java (backward-compat)
```

### Final `common-kafka` Structure (After All Phases)
```
chatappBE/common/common-kafka/
  src/main/java/com/example/common/kafka/
    producer/
      KafkaEventProducer.java
      DefaultKafkaEventProducer.java
    
    consumer/
      KafkaEventConsumer.java
      KafkaEventHandler.java
      KafkaEventListenerAdapter.java
    
    topic/
      KafkaTopics.java
    
    registry/
      KafkaEventRegistry.java
      DefaultKafkaEventRegistry.java
    
    serialization/
      KafkaEventSerializer.java
      KafkaEventDeserializer.java
    
    config/
      KafkaAutoConfiguration.java
    
    logging/
      KafkaEventLogger.java
    
    error/
      KafkaEventException.java
      // ... other Kafka-specific exceptions
```

### Final `common-redis` Structure (After All Phases)
```
chatappBE/common/common-redis/
  src/main/java/com/example/common/redis/pubsub/
    publisher/
      RedisEventPublisher.java
      DefaultRedisEventPublisher.java
    
    subscriber/
      RedisEventSubscriber.java
    
    listener/
      RedisEventListener.java
    
    dispatcher/
      RedisEventDispatcher.java
    
    channel/
      RedisChannels.java
      RedisChannelBuilder.java
    
    registry/
      RedisEventRegistry.java
      DefaultRedisEventRegistry.java
    
    serialization/
      RedisEventSerializer.java
      JsonRedisEventSerializer.java
      RedisEventDeserializer.java
    
    config/
      RedisPublisherConfig.java
      RedisListenerContainerConfig.java
    
    logging/
      RedisPubSubLogger.java
    
    error/
      RedisEventException.java
      // ... other Redis-specific exceptions
    
    flow/
      RedisEventRoutingContext.java
```

---

**End of Proposal**
