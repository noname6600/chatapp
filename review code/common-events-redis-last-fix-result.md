# common-events + common-redis Last-Fix Refactor — Result

## Summary

Pass 2 final-blocker fixes targeting common-events and common-redis.
No behavior changes. All tests green. Both modules are now freeze-ready.

---

## Changed Files

### common-redis

| File | Change |
|------|--------|
| `config/RedisAutoConfiguration.java` | Added `redisPubSubSubscriberAdapter` bean; renamed `redisEventRegistry` → `eventPayloadRegistry`; renamed `logger` → `observer` in publisher bean param |
| `publisher/DefaultRedisEventPublisher.java` | Renamed field `logger` → `observer`; null-safe subscriber count (`Long` + coalesce to `0L`); renamed local `payload` → `serialized` to avoid shadowing |
| `listener/RedisPubSubSubscriberAdapter.java` | Renamed field `logger` → `observer`; updated all `logger.xxx(...)` to `observer.xxx(...)`; improved class Javadoc |
| `contract/RedisContractTest.java` | Added `RedisPubSubSubscriberAdapter` import; added `autoConfig_redisPubSubSubscriberAdapterCanBeInstantiated`; added `publisher_handlesNullSubscriberCountWithoutThrowing` |

### common-events

| File | Change |
|------|--------|
| `validation/EventContractValidator.java` | Converted to `public final class` with `private EventContractValidator() {}`; made `isValidEventType`, `validateMetadata`, `isRegisteredEventType` all `public static`; updated Javadoc |
| `contract/SharedEventModelContractTest.java` | Removed `private EventContractValidator validator` field; removed `validator = new EventContractValidator()` from `@BeforeEach`; all `validator.xxx(...)` → `EventContractValidator.xxx(...)` static calls |
| `EventEnvelope.java` | Rewrote Javadoc to semantic contract language; removed "transport-specific event types" operational wording |
| `SharedEventCatalog.java` | Replaced "Publishers and serializers should call... before transport I/O" with "Call ... before accepting an envelope" |
| `integration/chat/ChatMessagePayload.java` | Fixed import order (`java.time.Instant` before `java.util.List`); replaced `isDirect` stale comment with "Defaults to false when absent." |

---

## Redis Inbound Adapter Auto-Config Wiring

`RedisPubSubSubscriberAdapter` previously existed but was not wired by `RedisAutoConfiguration`.

Added:
```java
@Bean
@ConditionalOnMissingBean
public RedisPubSubSubscriberAdapter redisPubSubSubscriberAdapter(
        RedisEventSerializer serializer,
        RedisEventDispatcher dispatcher,
        RedisPubSubObserver observer) {
    return new RedisPubSubSubscriberAdapter(serializer, dispatcher, observer);
}
```

Channel subscription and `RedisMessageListenerContainer` management remain service-owned by design.

---

## Redis Null-Safe Subscriber Count

`StringRedisTemplate.convertAndSend(channel, payload)` returns nullable `Long`.

Before:
```java
long subscriberCount = redisTemplate.convertAndSend(channel, payload);
// NPE risk on unbox when Redis returns null
```

After:
```java
Long subscriberCount = redisTemplate.convertAndSend(channel, serialized);
observer.logPublishSubscriberCount(context, eventEnvelope, subscriberCount != null ? subscriberCount : 0L);
```

---

## EventContractValidator Final Shape

| Method | Before | After |
|--------|--------|-------|
| `validateEventNameOrThrow` | `public static` | `public static` (no change) |
| `isValidEventType` | `public` (instance) | `public static` |
| `validateMetadata` | `public` (instance) | `public static` |
| `isRegisteredEventType` | `public` (instance) | `public static` |
| Class declaration | `public class` | `public final class` |
| Constructor | (implicit public) | `private EventContractValidator() {}` |

---

## Comment / Javadoc Cleanup

- **EventEnvelope**: Replaced generic wrapper description with canonical semantic contract language; removed "transport-specific event types" phrasing.
- **SharedEventCatalog**: Replaced "Publishers and serializers should call... before transport I/O" with plain pre-condition statement.
- **ChatMessagePayload**: Removed "older producers that do not include this field" compatibility wording from `isDirect`; fixed `java.util.List` import order.
- **EventContractValidator**: New Javadoc says "Static utility for event name syntax and metadata structure validation."
- **RedisPubSubSubscriberAdapter**: Expanded Javadoc to explain channel subscription is service-owned.

---

## Tests Added / Updated

| File | Test | Type |
|------|------|------|
| `RedisContractTest.java` | `autoConfig_redisPubSubSubscriberAdapterCanBeInstantiated` | New — confirms adapter class instantiates without NPE (auto-config bean contract) |
| `RedisContractTest.java` | `publisher_handlesNullSubscriberCountWithoutThrowing` | New — verifies null return from `convertAndSend` does not throw |
| `SharedEventModelContractTest.java` | All `validator.*` tests | Updated — converted from instance method calls to static method calls |

---

## Validation

```
.\gradlew.bat :common:common-events:test :common:common-redis:test --rerun-tasks
BUILD SUCCESSFUL in 20s
8 actionable tasks: 8 executed
```

---

## Freeze Verdict

| Module | Status |
|--------|--------|
| `common-events` | **FREEZE-READY** |
| `common-kafka` | **FREEZE-READY** (already frozen from Pass 1) |
| `common-redis` | **FREEZE-READY** |
| Whole common scope | **FREEZE-READY** |
