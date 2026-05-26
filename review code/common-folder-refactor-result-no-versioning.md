# Common Folder Cleanup — No Versioning Result

**Date:** 2026-05-06  
**Scope:** chatappBE/common (common-events, common-kafka, common-redis only)  
**Goal:** Remove ALL legacy/compatibility/versioning code; freeze-ready canonical APIs only  
**Status:** ✅ COMPLETE

## 1. Summary of Changes

### Objectives Achieved

✅ **Removed all versioning code:**
- Removed `eventVersion` field from `EventMetadata` (5-arg constructor now, was 6-arg)
- Removed `DEFAULT_EVENT_VERSION` and `TRACE_POLICY` constants from `EventMetadata`
- Removed `getSupportedEventVersion()` and `validateEventVersionOrThrow()` from `SharedEventCatalog`
- Removed `EVENT_VERSION_MAP` static map from `SharedEventCatalog`
- Removed `eventVersion` header from Kafka producer
- Removed `eventVersion` deserialization logic from Kafka and Redis serializers
- Removed all version-related tests

✅ **Removed all compatibility/deprecated classes:**
- **common-events:** FriendRequestEvent, RealtimeContractValidator, RealtimeContractVersions, contract package, realtime package
- **common-kafka:** Entire `api` package (IKafkaEvent, KafkaEvent, IKafkaEventPublisher, KafkaEventPublisher), KafkaEventPublisher producer, DefaultKafkaEventPublisher, entire `integration/kafka/event` package (9 wrapper DTOs)
- **common-redis:** `api` package (IRedisMessage, IRedisPublisher), RedisMessage, RedisEventHandler

✅ **Enforced canonical APIs:**
- Kafka: `KafkaEventProducer.send(...)` only (no Publisher)
- Redis: `RedisEventPublisher.publish(EventEnvelope)` only (no RedisMessage overload)
- Redis: `RedisEventSubscriber<T>.onEnvelope(EventEnvelope<T>)` only (no payload-first onMessage)
- EventMetadata: 5-arg constructor, no versioning
- SharedEventCatalog: pure payload mapping and contract enforcement, no versioning

✅ **Removed semantic event aliases from transport APIs:**
- KafkaTopics now contains only true Kafka routes (TOPIC_FRIENDSHIP_EVENTS, TOPIC_SYSTEM_DEAD_LETTER, etc.)
- Removed deprecated semantic aliases: ACCOUNT_CREATED, CHAT_MESSAGE_SENT, NOTIFICATION_REQUESTED, etc.

## 2. Deleted Files

### common-events
- `src/main/java/com/example/common/integration/friendship/FriendRequestEvent.java` (deprecated compat)
- `src/main/java/com/example/common/integration/contract/RealtimeContractValidator.java` (deprecated compat)
- `src/main/java/com/example/common/integration/realtime/RealtimeContractVersions.java` (deprecated compat)
- Entire `src/main/java/com/example/common/integration/contract/` directory
- Entire `src/main/java/com/example/common/integration/realtime/` directory

### common-kafka
- **API package:** Entire `src/main/java/com/example/common/kafka/api/` directory:
  - IKafkaEvent.java
  - KafkaEvent.java
  - IKafkaEventPublisher.java
  - KafkaEventPublisher.java
- **Integration wrappers:** Entire `src/main/java/com/example/common/integration/kafka/event/` directory:
  - BaseKafkaCompatEvent.java
  - AccountCreatedEvent.java
  - ChatMessageDeletedEvent.java
  - ChatMessageEditedEvent.java
  - ChatMessageSentEvent.java
  - ChatReactionUpdatedEvent.java
  - FriendRequestKafkaEvent.java
  - FriendshipEvent.java
  - NotificationRequestedEvent.java
- **Producers:**
  - `src/main/java/com/example/common/kafka/producer/KafkaEventPublisher.java` (deprecated)
  - `src/main/java/com/example/common/kafka/producer/DefaultKafkaEventPublisher.java` (deprecated)

### common-redis
- **API package:** Entire `src/main/java/com/example/common/redis/api/` directory:
  - IRedisMessage.java
  - IRedisPublisher.java
- **Message:** `src/main/java/com/example/common/redis/message/RedisMessage.java` (legacy envelope)
- **Subscriber:** `src/main/java/com/example/common/redis/subscriber/RedisEventHandler.java` (deprecated compat)

## 3. Modified Files

### common-events

#### EventMetadata.java
- **Removed:** `eventVersion` field, `DEFAULT_EVENT_VERSION`, `TRACE_POLICY` constants, 6-arg constructor with eventVersion param
- **Kept:** 5-arg constructor (eventId, eventType, sourceService, createdAt, correlationId)
- **Kept:** Static `of(...)` factory method
- **Result:** Canonical lightweight metadata, no versioning

#### SharedEventCatalog.java
- **Removed:** `EVENT_VERSION_MAP` static map
- **Removed:** `getSupportedEventVersion(String eventType)` method
- **Removed:** `validateEventVersionOrThrow(String eventType, int eventVersion)` method
- **Kept:** `PAYLOAD_BEARING_EVENT_TYPES` and `PAYLOAD_LESS_EVENT_TYPES` sets
- **Kept:** `getCanonicalPayloadClass(String eventType)` method
- **Kept:** `enforcePublishPayloadContract(String eventType, Object payload)` method
- **Kept:** `registerAll(EventPayloadRegistry registry)` method
- **Result:** Pure semantic-to-payload mapping, no versioning logic

#### SharedEventModelContractTest.java
- **Removed:** `FriendRequestEvent` import
- **Removed:** Test `eventMetadata_defaultsEventVersionToOneWhenMissing()`
- **Removed:** Test `staleFriendRequestEvent_isDeprecatedCompatibilityOnly()`
- **Removed:** Test `eventVersionPolicy_defaultsToOneAndRejectsUnsupportedVersion()`
- **Modified:** `eventEnvelope_serializesAndDeserializesRoundTrip()` — removed assertion on `getEventVersion()`
- **Result:** Only canonical contract tests remain, no versioning tests

### common-kafka

#### KafkaAutoConfiguration.java
- **Removed:** Imports for DefaultKafkaEventPublisher, KafkaEventPublisher
- **Removed:** Bean method `kafkaEventPublisher()` that returned DefaultKafkaEventPublisher
- **Kept:** Bean method `kafkaEventProducer()` returning DefaultKafkaEventProducer
- **Kept:** Auto-config for KafkaEventDispatcher, consumer/producer properties, error handler
- **Result:** Only KafkaEventProducer bean auto-wired, no Publisher

#### DefaultKafkaEventProducer.java
- **Removed:** `HEADER_EVENT_VERSION` constant
- **Removed:** Version validation: `SharedEventCatalog.validateEventVersionOrThrow(eventType, metadata.getEventVersion())`
- **Removed:** Version header line: `addHeader(record, HEADER_EVENT_VERSION, ...)`
- **Kept:** Standard headers: eventId, eventType, correlationId, sourceService, createdAt
- **Kept:** Payload contract enforcement via `SharedEventCatalog.enforcePublishPayloadContract(...)`
- **Result:** No versioning in producer; canonical EventEnvelope<T> pipeline

#### KafkaTopics.java
- **Removed:** All deprecated semantic aliases (ACCOUNT_CREATED, CHAT_MESSAGE_SENT, CHAT_MESSAGE_EDITED, CHAT_MESSAGE_DELETED, CHAT_REACTION_UPDATED, NOTIFICATION_REQUESTED, FRIENDSHIP_EVENTS, FRIENDSHIP_REQUEST_EVENTS)
- **Kept:** Only true Kafka routes:
  - `TOPIC_FRIENDSHIP_EVENTS` = "friendship.events" (aggregate topic)
  - `TOPIC_FRIENDSHIP_REQUEST_EVENTS` = "friendship.request.events" (aggregate topic)
  - `TOPIC_SYSTEM_DEAD_LETTER` = "system.dead-letter" (infrastructure)
  - `TOPIC_SYSTEM_RETRY` = "system.retry" (infrastructure)
- **Result:** KafkaTopics = transport routes only, not semantic event names

#### KafkaEventRoutingContext.java
- **Removed:** Import of `IKafkaEvent` (legacy api package)
- **Removed:** Overload `of(String topic, String key, IKafkaEvent<?> event)`
- **Kept:** Overload `of(String topic, String key, EventEnvelope<?> event)`
- **Result:** Purely EventEnvelope-based routing

#### EventEnvelopeKafkaDeserializer.java
- **Removed:** Method `intOrNull(...)` for version parsing
- **Removed:** Version deserialization logic in deserialize method
- **Removed:** 6-arg EventMetadata constructor call with eventVersion parameter
- **Changed to:** 5-arg EventMetadata constructor call (no eventVersion)
- **Result:** Canonical deserialization, no version validation

#### KafkaContractTest.java
- **Removed:** Test assertion `assertThat(readHeader(sentRecord.get(), "eventVersion")).isEqualTo("1")`
- **Removed:** 6-arg EventMetadata constructor calls in helper methods
- **Changed to:** 5-arg EventMetadata constructor calls
- **Removed:** Version assertion from `kafkaSerde_roundTrip_usesSharedCatalogAndRejectsUnknownType()` test
- **Result:** Tests validate canonical EventEnvelope pipeline, no versioning

### common-redis

#### RedisEventSubscriber.java
- **Complete rewrite:** Removed payload-first design
- **Before:** `onMessage(T message)` was abstract, `onEnvelope(EventEnvelope<?> envelope)` was default that extracted payload
- **After:** `onEnvelope(EventEnvelope<T> envelope)` is the canonical abstract method
- **Removed:** `onMessage(T message)` method entirely
- **Result:** True EventEnvelope-first contract

#### RedisEventPublisher.java
- **Removed:** RedisMessage compatibility overload `publish(String channel, RedisMessage<?> message)`
- **Removed:** Default method that converted RedisMessage to EventEnvelope
- **Kept:** Single canonical method: `publish(String channel, EventEnvelope<?> eventEnvelope)`
- **Result:** Pure EventEnvelope-based publisher API

#### JsonRedisEventSerializer.java
- **Removed:** Method `intOrNull(...)`
- **Removed:** Version deserialization in `deserialize()` method
- **Removed:** Version validation logic: `SharedEventCatalog.validateEventVersionOrThrow(eventType, resolvedVersion)`
- **Removed:** 6-arg EventMetadata constructor call
- **Changed to:** 5-arg EventMetadata constructor call
- **Result:** Pure EventEnvelope serde, no versioning

#### RedisEventRoutingContext.java
- **Removed:** Import of `IRedisMessage` (legacy api package)
- **Removed:** Overload `of(String channel, IRedisMessage message)`
- **Kept:** Overload `of(String channel, EventEnvelope<?> envelope)`
- **Result:** Purely EventEnvelope-based routing

#### RedisContractTest.java
- **Changed:** Subscriber implementations in duplicate test — replaced `onMessage(String message)` with `onEnvelope(EventEnvelope<String> envelope)`
- **Result:** Tests validate envelope-first contract

## 4. Deprecated APIs Removed

### Entirely Deleted

**Kafka:**
- `com.example.common.kafka.api.IKafkaEvent` interface
- `com.example.common.kafka.api.KafkaEvent` interface
- `com.example.common.kafka.api.IKafkaEventPublisher` interface
- `com.example.common.kafka.api.KafkaEventPublisher` interface
- `com.example.common.kafka.producer.KafkaEventPublisher` class
- `com.example.common.kafka.producer.DefaultKafkaEventPublisher` class
- `com.example.common.integration.kafka.event.BaseKafkaCompatEvent` abstract class
- `com.example.common.integration.kafka.event.AccountCreatedEvent` class
- `com.example.common.integration.kafka.event.ChatMessageDeletedEvent` class
- `com.example.common.integration.kafka.event.ChatMessageEditedEvent` class
- `com.example.common.integration.kafka.event.ChatMessageSentEvent` class
- `com.example.common.integration.kafka.event.ChatReactionUpdatedEvent` class
- `com.example.common.integration.kafka.event.FriendRequestKafkaEvent` class
- `com.example.common.integration.kafka.event.FriendshipEvent` class
- `com.example.common.integration.kafka.event.NotificationRequestedEvent` class

**Redis:**
- `com.example.common.redis.api.IRedisMessage` interface
- `com.example.common.redis.api.IRedisPublisher` interface
- `com.example.common.redis.message.RedisMessage` class
- `com.example.common.redis.subscriber.RedisEventHandler` class (deprecated compat)

**Events:**
- `com.example.common.integration.friendship.FriendRequestEvent` class (deprecated compat)
- `com.example.common.integration.contract.RealtimeContractValidator` class (deprecated compat)
- `com.example.common.integration.realtime.RealtimeContractVersions` class (deprecated compat)

### Removed from Public APIs (But Keep Constants/Values)

**KafkaTopics semantic aliases** (all marked `@Deprecated`):
- `ACCOUNT_CREATED`
- `CHAT_MESSAGE_SENT`
- `CHAT_MESSAGE_EDITED`
- `CHAT_MESSAGE_DELETED`
- `CHAT_REACTION_UPDATED`
- `NOTIFICATION_REQUESTED`
- `FRIENDSHIP_EVENTS`
- `FRIENDSHIP_REQUEST_EVENTS`

## 5. Versioning Code Removed

### From EventMetadata

- Field `private final int eventVersion`
- Constant `public static final int DEFAULT_EVENT_VERSION = 1`
- Constant `public static final String TRACE_POLICY = "correlationId-only"`
- Constructor `EventMetadata(..., @JsonProperty("eventVersion") Integer eventVersion)`
- All constructor logic that resolved/validated eventVersion

### From SharedEventCatalog

- Static map `private static final Map<String, Integer> EVENT_VERSION_MAP`
- Initialization code for EVENT_VERSION_MAP in static block
- Method `public static Integer getSupportedEventVersion(String eventType)`
- Method `public static void validateEventVersionOrThrow(String eventType, int eventVersion)`

### From Kafka Producer

- Header constant `HEADER_EVENT_VERSION`
- Version header line in `DefaultKafkaEventProducer.send()`
- Version validation call to `SharedEventCatalog.validateEventVersionOrThrow(...)`

### From Kafka Deserializer

- Method `intOrNull(JsonNode, String fieldName)`
- Version parameter passed to EventMetadata constructor

### From Redis Serializer

- Version deserialization logic in `deserialize()` method
- Version validation call to `SharedEventCatalog.validateEventVersionOrThrow(...)`
- Version parameter passed to EventMetadata constructor
- Method `intOrNull(JsonNode, String fieldName)`

### From Tests

- All version-related assertions
- Test methods for version defaults and validation
- 6-arg EventMetadata constructor calls (changed to 5-arg)
- Helper imports for deprecated version classes

## 6. Final Canonical APIs

### common-events (Public Surface)

```java
// EventMetadata — immutable, no versioning
public record EventMetadata {
    public EventMetadata(
        String eventId,
        String eventType,
        String sourceService,
        Instant createdAt,
        String correlationId
    );
    public static EventMetadata of(...);
}

// EventEnvelope — standard transport format
public record EventEnvelope<T> {
    public EventEnvelope(EventMetadata metadata, T payload);
}

// SharedEventCatalog — pure payload mapping
public static class SharedEventCatalog {
    public static final Set<String> PAYLOAD_BEARING_EVENT_TYPES;
    public static final Set<String> PAYLOAD_LESS_EVENT_TYPES;
    public static Class<?> getCanonicalPayloadClass(String eventType);
    public static void registerAll(EventPayloadRegistry registry);
    public static void enforcePublishPayloadContract(String eventType, Object payload);
}

// EventContractValidator — event type syntax
public class EventContractValidator {
    public boolean isValidEventType(String eventType);
    public boolean isRegisteredEventType(String eventType);
    public void validateEventNameOrThrow(String eventType);
}
```

### common-kafka (Public Surface)

```java
// KafkaEventProducer — Producer terminology only
public interface KafkaEventProducer {
    void send(String topic, String key, EventEnvelope<?> envelope);
}

// KafkaEventHandler — Consumer side
public interface KafkaEventHandler<T> {
    String eventType();
    void handle(EventEnvelope<T> event);
}

// KafkaEventDispatcher — Unknown event policy
public class KafkaEventDispatcher {
    public KafkaEventDispatcher(List<? extends KafkaEventHandler<?>> handlers);
    public void dispatch(EventEnvelope<?> envelope);
}

// KafkaTopics — Transport routes only
public static final class KafkaTopics {
    public static final String TOPIC_FRIENDSHIP_EVENTS;
    public static final String TOPIC_FRIENDSHIP_REQUEST_EVENTS;
    public static final String TOPIC_SYSTEM_DEAD_LETTER;
    public static final String TOPIC_SYSTEM_RETRY;
}
```

### common-redis (Public Surface)

```java
// RedisEventPublisher — EventEnvelope only
public interface RedisEventPublisher {
    void publish(String channel, EventEnvelope<?> eventEnvelope);
}

// RedisEventSubscriber — Envelope-first
public interface RedisEventSubscriber<T> {
    String eventType();
    void onEnvelope(EventEnvelope<T> envelope);
}

// RedisEventDispatcher — Unknown event DROP/FAIL
public class RedisEventDispatcher {
    public RedisEventDispatcher(List<? extends RedisEventSubscriber<?>> subscribers);
    public void dispatch(String channel, EventEnvelope<?> envelope);
}

// RedisChannels — Transport channels
public static final class RedisChannels {
    public static String chatRoom(UUID roomId);
    public static String notificationUser(UUID userId);
    public static String presenceRoom(UUID roomId);
}
```

## 7. Test Results

### Compilation Results

```
:common:common-events:compileJava   — BUILD SUCCESSFUL
:common:common-kafka:compileJava    — BUILD SUCCESSFUL
:common:common-redis:compileJava    — BUILD SUCCESSFUL
```

### Test Results

```
:common:common-events:test   — BUILD SUCCESSFUL
:common:common-kafka:test    — BUILD SUCCESSFUL
:common:common-redis:test    — BUILD SUCCESSFUL

Total tests run: All modules pass
All contract tests validate canonical EventEnvelope pipeline
All versioning-related tests removed
All compatibility/legacy API tests removed
```

## 8. Breaking Changes Summary

### For Services Using These Modules

**DO NOT DEPLOY SERVICES WITHOUT UPDATING TO NEW APIS:**

1. **Kafka Producer Change:**
   - Old: Any use of `KafkaEventPublisher` bean or interface — REMOVED
   - New: Use `KafkaEventProducer.send(topic, key, EventEnvelope<?>)` only
   - No `publish(...)` method

2. **Kafka Wrapper DTOs Removed:**
   - Old: Classes like `AccountCreatedEvent`, `ChatMessageSentEvent`, `FriendRequestKafkaEvent` — DELETED
   - New: Use `EventEnvelope<Payload>` where Payload is from `common-events` domain
   - Event type strings must come from `common-events` event type enums

3. **Kafka Semantic Aliases Removed:**
   - Old: KafkaTopics.ACCOUNT_CREATED, KafkaTopics.CHAT_MESSAGE_SENT — DELETED
   - New: Use event type enum values directly: `AccountEventType.ACCOUNT_CREATED.value()`
   - KafkaTopics contains only true Kafka aggregate topics and infrastructure topics

4. **Redis Message Removed:**
   - Old: `RedisMessage<T>`, `RedisEventHandler<T>` — DELETED
   - New: Implement `RedisEventSubscriber<T>` with `onEnvelope(EventEnvelope<T> envelope)` abstract method
   - Subscribe to `EventEnvelope` directly, not RedisMessage

5. **EventMetadata Constructor Changed:**
   - Old: `new EventMetadata(id, type, service, createdAt, correlationId, eventVersion)` — 6 args
   - New: `new EventMetadata(id, type, service, createdAt, correlationId)` — 5 args
   - No eventVersion parameter

6. **Payload Extraction:**
   - Old (Redis): `RedisEventHandler.onMessage(T message)` — REMOVED
   - New (Redis): `RedisEventSubscriber.onEnvelope(EventEnvelope<T> envelope)` — extract `envelope.payload()` yourself

### Compilation Impact

Services will NOT COMPILE until they update:
- All `KafkaEventPublisher` references
- All Kafka wrapper DTO constructors
- All `KafkaTopics.SEMANTIC_ALIAS` references
- All `RedisEventHandler` implementations
- All `RedisMessage` usage
- All 6-arg `EventMetadata` constructor calls
- All `onMessage()` method signatures to `onEnvelope()`

## 9. Remaining Common-Only TODOs

**None identified.** The three modules are now:
- ✅ Transport-neutral (common-events only)
- ✅ Free of versioning
- ✅ Free of legacy/deprecated compatibility APIs
- ✅ Using only canonical EventEnvelope<T> format across Kafka and Redis
- ✅ All tests passing
- ✅ Freeze-ready

## 10. Freeze Verdict

### common-events
**Status: ✅ READY TO FREEZE**

- Pure semantic event contracts
- No transport dependencies
- No versioning
- No compatibility layers
- Canonical EventMetadata, EventEnvelope, SharedEventCatalog APIs
- All contract tests passing
- Single source of truth for event semantics

### common-kafka
**Status: ✅ READY TO FREEZE**

- Producer/Consumer terminology only (no Publisher)
- EventEnvelope<T> canonical format
- KafkaTopics contains routes only, not semantic aliases
- No Kafka wrapper DTOs (use EventEnvelope + common-events payloads)
- No versioning
- All contract tests passing
- Clean dependency: depends only on common-events

### common-redis
**Status: ✅ READY TO FREEZE**

- Publisher/Subscriber terminology correct
- EventEnvelope<T> primary interface throughout
- No RedisMessage compatibility
- RedisEventSubscriber envelope-first (no payload-only path)
- RedisEventPublisher EventEnvelope-only
- No versioning
- All contract tests passing
- Clean dependency: depends only on common-events

### Overall Common Folder
**Status: ✅ READY TO FREEZE**

- All three modules are canonical, lean, versioning-free
- Dependency direction: common-events ← common-kafka, common-redis (no cross-deps)
- Transport boundaries clear and enforced
- EventEnvelope<T> is the universal event shape
- SharedEventCatalog is the single semantic owner
- No service compatibility layer needed in common (services update, not common)
- All module tests green
- Recommended: Tag as 3.0.0 (major breaking release, versioning removed)

## 11. Next Steps

**Immediate:**
- ✅ This refactor is complete for the common modules
- Services must update their Kafka/Redis code to use new APIs (separate task)
- Services will NOT COMPILE against these new common APIs until updated

**Future:**
- Monitor service compilation errors for any missed API updates
- No further changes expected to these three modules
- Consider version tagging and release notes documenting breaking changes

---

**End of Report**
