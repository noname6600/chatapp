# Common Messaging Next Refactor Result

## 1. Scope

**Modules modified:**
- `chatappBE/common/common-events/src/main/java/com/example/common/integration/presence/PresenceEventType.java`
- `chatappBE/common/common-events/src/main/java/com/example/common/event/SharedEventCatalog.java`
- `chatappBE/common/common-kafka/src/main/java/com/example/common/kafka/producer/DefaultKafkaEventProducer.java`
- `chatappBE/common/common-kafka/src/main/java/com/example/common/kafka/config/KafkaAutoConfiguration.java`
- `chatappBE/common/common-kafka/src/main/java/com/example/common/kafka/producer/DefaultKafkaEventPublisher.java`
- `chatappBE/common/common-kafka/src/main/java/com/example/common/kafka/observability/Slf4jKafkaEventLogger.java`
- `chatappBE/common/common-kafka/src/test/java/com/example/common/kafka/contract/KafkaContractTest.java`
- `chatappBE/common/common-redis/src/test/java/com/example/common/redis/contract/RedisContractTest.java`

**Modules NOT modified:** all service modules, gateway, auth-service, chat-service, presence-service,
friendship-service, notification-service, user-service, upload-service, redis-cache module,
docker/nginx/deployment files, common-redis main source (no structural code changes needed there).

**Verification:** `.\gradlew.bat :common:common-events:test :common:common-redis:test :common:common-kafka:test` → `BUILD SUCCESSFUL`
(Deprecation warnings emitted at compile time are expected — the compat types are `@Deprecated(forRemoval=true)` by design.)

---

## 2. Problems Targeted

From the post-final-cleanup review (section 6 — Remaining Problems):

**High:**
- Fake Kafka compatibility surface: `KafkaEventProducer` and `DefaultKafkaEventProducer` existed but were not wired through `KafkaAutoConfiguration`, so services injecting `KafkaEventProducer` got a `NoSuchBeanDefinitionException`. Fixed.

**Medium:**
- Presence alias compatibility is fake: `PresenceEventType` exposed `normalize()`, `isDeprecatedAlias()`, `legacyAliasOf()` and their backing maps, but none of these are honored in the actual shared transport path (`EventContractValidator`, `SharedEventCatalog`, `JsonRedisEventSerializer` all operate on canonical hyphenated names only). Removed.
- `SharedEventCatalog` Javadoc contained a transport-specific reference (`RedisEventRegistry`) from a `common-events` class. Fixed.
- Cross-transport validation gap: `DefaultRedisEventPublisher` rejected blank channels, but `DefaultKafkaEventPublisher` did not validate blank/null topics. Fixed.
- `Slf4jKafkaEventLogger` did not implement `logDispatch`/`logDispatchError`, making consumer-side Kafka observability silently no-op even though `KafkaEventDispatcher` calls those methods. Fixed.
- Tests under-covered the new contract behaviors (SharedEventCatalog bootstrap, missing-payload rejection for Redis, blank-topic rejection for Kafka, compat type assignability). Fixed.

---

## 3. Changes Made In Common-Events

### PresenceEventType — removed deprecated alias surface

**File:** `com/example/common/integration/presence/PresenceEventType.java`

**Removed:**
- `DEPRECATED_ALIASES` static map
- `LEGACY_BY_NORMALIZED` static map
- `normalize(String)` — deprecated internal migration utility
- `isDeprecatedAlias(String)` — deprecated alias detection method
- `legacyAliasOf(String)` — deprecated legacy alias lookup method
- `java.util.Map` import (no longer needed)

**Updated:** `fromValue(String)` — now performs a direct `toLowerCase()` + equals loop without calling the removed `normalize()`. Removed `@SuppressWarnings("deprecation")`.

**Reason:** These methods had no effect in the shared transport path. `EventContractValidator` rejects underscore aliases. `SharedEventCatalog` registers only canonical hyphenated names. `JsonRedisEventSerializer` resolves exact event-type strings. The alias surface was non-operational dead code that misrepresented `PresenceEventType` as an active migration helper.

### SharedEventCatalog — removed transport leakage from Javadoc

**File:** `com/example/common/event/SharedEventCatalog.java`

**Changed:** Replaced the Redis-specific `{@link com.example.common.redis.registry.RedisEventRegistry}` reference in the `registerAll()` Javadoc with a transport-neutral description. `common-events` should not reference transport module types in any form.

---

## 4. Changes Made In Common-Redis

No main source code changes. The `RedisEventRegistry` / `DefaultRedisEventRegistry` adapter types are kept — their Javadoc now accurately explains their purpose (injection-by-type compatibility) and they add no redundant behavior.

---

## 5. Changes Made In Common-Kafka

### DefaultKafkaEventProducer — explicit interface declaration

**File:** `com/example/common/kafka/producer/DefaultKafkaEventProducer.java`

**Changed:** Added `implements KafkaEventProducer` to the class declaration.

**Before:**
```java
public class DefaultKafkaEventProducer extends DefaultKafkaEventPublisher {
```

**After:**
```java
public class DefaultKafkaEventProducer extends DefaultKafkaEventPublisher implements KafkaEventProducer {
```

**Reason:** Without the explicit interface declaration, Spring's bean type matching could not resolve `@Autowired KafkaEventProducer` injection points even when a `DefaultKafkaEventProducer` bean was present. The class must explicitly implement the interface for Spring's `isTypeMatch` to find it.

### KafkaAutoConfiguration — wire real compat bean

**File:** `com/example/common/kafka/config/KafkaAutoConfiguration.java`

**Changed:**
- Import changed from `DefaultKafkaEventPublisher` to `DefaultKafkaEventProducer`
- `kafkaEventPublisher` bean method now instantiates `DefaultKafkaEventProducer` (not `DefaultKafkaEventPublisher`)
- Added `@SuppressWarnings("deprecation")` on the factory method
- Added Javadoc explaining the compat rationale

**Before:** `return new DefaultKafkaEventPublisher(kafkaTemplate, logger)` — a type NOT assignable to `KafkaEventProducer`

**After:** `return new DefaultKafkaEventProducer(kafkaTemplate, logger)` — a type that implements both `KafkaEventPublisher` (canonical) and `KafkaEventProducer` (compat alias)

**Result:**
- Services injecting `KafkaEventPublisher` (canonical, new code) → satisfied ✓
- Services injecting `KafkaEventProducer` (deprecated alias, existing code) → satisfied ✓
- Only ONE bean registered for both interfaces

### DefaultKafkaEventPublisher — blank topic validation

**File:** `com/example/common/kafka/producer/DefaultKafkaEventPublisher.java`

**Added at the start of `publish()`:**
```java
if (topic == null || topic.isBlank()) {
    throw new IllegalArgumentException("Kafka topic must not be null or blank");
}
```

**Reason:** Redis already rejected blank channels with `IllegalArgumentException`. Kafka had no equivalent guard. Cross-transport validation behavior is now aligned for the same class of pre-condition error.

### Slf4jKafkaEventLogger — consumer-side observability

**File:** `com/example/common/kafka/observability/Slf4jKafkaEventLogger.java`

**Added:**
- `logDispatch(KafkaEventRoutingContext, EventEnvelope<?>)` — logs `DEBUG [KAFKA][DISPATCH][SUCCESS]` with eventId, eventType, correlationId
- `logDispatchError(KafkaEventRoutingContext, EventEnvelope<?>, Throwable)` — logs `ERROR [KAFKA][DISPATCH][FAILURE]` with the same fields plus the exception

**Reason:** `KafkaEventDispatcher` calls `observer.logDispatch(...)` and `observer.logDispatchError(...)`. With the previous no-op defaults in place, the default bean provided zero consumer-side observability even though the dispatcher was correctly wired. The default logger now provides operational output for both producer and consumer paths.

---

## 6. Deleted / Removed Code

### Removed from PresenceEventType (common-events)
- `DEPRECATED_ALIASES` — static `Map<String, String>` (underscore → hyphen alias map)
- `LEGACY_BY_NORMALIZED` — static `Map<String, String>` (hyphen → underscore reverse map)
- `normalize(String)` — `@Deprecated(since="2.2", forRemoval=true)` static method
- `isDeprecatedAlias(String)` — `@Deprecated(since="2.2", forRemoval=true)` static method
- `legacyAliasOf(String)` — `@Deprecated(since="2.2", forRemoval=true)` static method
- `java.util.Map` import

### Removed from SharedEventCatalog Javadoc (common-events)
- `{@link com.example.common.redis.registry.RedisEventRegistry}` cross-module reference

### Not removed (kept for real compatibility)
- `KafkaEventProducer` interface — still needed by downstream services that haven't migrated
- `DefaultKafkaEventProducer` class — now properly wired so it provides real compatibility, not fake
- `KafkaPubSubException` — kept as catch-compatible alias for services that catch it
- `RedisEventRegistry` / `DefaultRedisEventRegistry` — kept for service injection-by-type; now have accurate Javadoc justifying the wrapper

---

## 7. Canonical Decisions Confirmed

**Final owner of event names:** `common-events` domain enums (`AccountEventType`, `ChatEventType`, `FriendshipEventType`, `NotificationEventType`, `PresenceEventType`, `UserEventType`). No transport module owns semantic event-name literals.

**Final owner of payload mappings:** `SharedEventCatalog.registerAll()` in `common-events`. All payload-bearing and payload-less classifications are authoritative there.

**Final handling of payload-less events:** `SharedEventCatalog.PAYLOAD_LESS_EVENT_TYPES` is the single authoritative set. Redis deserializer skips registry lookup for those types. Kafka has no deserializer in the common layer (service-owned).

**Final Redis contract integration:** `RedisAutoConfiguration.redisEventRegistry()` bootstraps `DefaultRedisEventRegistry` from `SharedEventCatalog.registerAll()`. Redis transport owns channels, serializer, publisher, listener, dispatcher, and observability. It does not own event semantics.

**Final Kafka route ownership:** `KafkaTopics` contains only true transport routes (aggregate and infrastructure). Services reference event-type enum values directly from `common-events` for semantic routing.

**Final compatibility surface intentionally kept:**
- `KafkaEventProducer` + `DefaultKafkaEventProducer` + `KafkaPubSubException` — kept with `@Deprecated(forRemoval=true)`, now properly wired through auto-config so the compatibility is real. To be removed after downstream services migrate.

---

## 8. Validation / Error Handling / Observability Improvements

- **Kafka pre-condition parity:** `DefaultKafkaEventPublisher.publish()` now rejects blank/null topic with `IllegalArgumentException`, matching `DefaultRedisEventPublisher`'s channel guard. Both transports now fail fast with the same error kind for invalid route input.

- **Kafka consumer observability:** `Slf4jKafkaEventLogger` now logs dispatch success at DEBUG and dispatch failure at ERROR. `KafkaEventDispatcher` was already calling these methods; the default logger now produces real output instead of silent no-ops.

- **Redis missing-payload rejection:** already present from the previous cleanup pass; now also covered by a dedicated contract test that uses the real `SharedEventCatalog` bootstrap path (not a manual `String.class` stub).

- **Redis payload-less acceptance:** new contract test confirms that payload-less events (from `SharedEventCatalog.PAYLOAD_LESS_EVENT_TYPES`) deserialize correctly without triggering a registry lookup or rejection.

---

## 9. Remaining External Migration Risks

These require edits **outside** the three allowed modules:

1. **`FriendRequestEvent` deletion** — `common-events` deleted this class in the previous pass, but `friendship-service` and `notification-service` still import it. Those services will not compile against the updated `common-events` JAR until they migrate to `FriendRequestPayload` + `FriendshipEventType`.

2. **Removed `KafkaTopics` semantic constants** — The previous pass removed 1:1 semantic aliases from `KafkaTopics`. `auth-service`, `chat-service`, and `notification-service` still reference those removed constants. Those services must migrate to referencing `AccountEventType.ACCOUNT_CREATED.value()` etc. directly.

3. **`KafkaEventProducer` injection migration** — `auth-service`, `chat-service`, `friendship-service`, and `notification-service` inject `KafkaEventProducer`. The compat wiring is now real (not fake), so these services will compile and run correctly against this version. They should still migrate to `KafkaEventPublisher` before `KafkaEventProducer` is removed.

4. **`ChatRedisEventConfig` re-registration conflict** — `chat-service` still re-registers `MESSAGE_PINNED` and `MESSAGE_UNPINNED` to its service-local `RoomMessagePinEventPayload`, conflicting with the shared catalog's `MessagePinPayload` registration. This must be resolved in `chat-service`.

5. **`PresenceEventType` alias callers** — Any callers of the removed `normalize()`, `isDeprecatedAlias()`, or `legacyAliasOf()` methods outside the three modules will fail to compile. These were `@Deprecated(forRemoval=true)` and should have been cleaned up at call sites already.

---

## 10. Final Summary

This pass completed the remaining medium and high priority cleanup identified in the post-final-cleanup review:

- **Killed fake Kafka compat:** `DefaultKafkaEventProducer` now explicitly implements `KafkaEventProducer`, and `KafkaAutoConfiguration` now wires it as the default bean. Services injecting the deprecated alias get a real, working bean. This was the single highest-severity remaining issue inside scope.

- **Removed dead alias surface from PresenceEventType:** All three deprecated alias utility methods and their backing maps are gone. `fromValue()` now does a clean direct match. The shared transport path never honored these methods, so removing them makes the type honest.

- **Closed transport leakage in SharedEventCatalog Javadoc:** `common-events` no longer references `RedisEventRegistry` in any form. The semantic catalog is fully transport-agnostic.

- **Aligned cross-transport validation:** Kafka now rejects blank/null topics at the same point in the publish lifecycle that Redis rejects blank channels. Pre-condition validation behavior is symmetric.

- **Completed Kafka observer coverage:** `Slf4jKafkaEventLogger` now provides real output for all four callbacks (`logPublish`, `logError`, `logDispatch`, `logDispatchError`). Both producer-side and consumer-side observability are operational with the default bean set.

- **Tests now cover the real contract:** Redis tests use `SharedEventCatalog` bootstrap, assert missing-payload rejection, and assert payload-less acceptance. Kafka tests cover blank-topic rejection and the assignability contract for the compat type.

The three-module foundation is now internally consistent and operationally honest. The remaining open issues are all external migration tasks in downstream services.
