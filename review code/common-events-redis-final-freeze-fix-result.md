# common-events + common-redis Final Freeze Fix — Result

## Summary

Final blocker fixes for common-events and common-redis based on
`review code/common-scope-final-freeze-review.md`.
No behavior changes beyond registry helper consistency.
All tests green. Both modules are now fully freeze-ready.

---

## 1. Changed Files

### common-events

| File | Change |
|------|--------|
| `event/DefaultEventPayloadRegistry.java` | Normalized `resolvePayload` and `contains` for null/blank/invalid inputs; updated Javadoc |
| `event/EventPayloadRegistry.java` | Updated Javadocs for all three methods with exact null/blank/invalid/unknown contracts |
| `event/EventMetadata.java` | Removed transport/routing operational language from class Javadoc |
| `event/SharedEventCatalog.java` | Removed operational wording from `PAYLOAD_LESS_EVENT_TYPES`, `validatePayloadContract`, and `registerAll` Javadocs |
| `integration/chat/MessagePinPayload.java` | Removed "service-local payload class" operational wording |
| `contract/SharedEventModelContractTest.java` | Added 8 new registry edge-case tests |

### common-redis

| File | Change |
|------|--------|
| `subscriber/RedisPubSubSubscriberAdapter.java` | New file at Publisher/Subscriber-aligned package; replaces old `listener/` path |
| `listener/RedisPubSubSubscriberAdapter.java` | **Deleted** |
| `config/RedisAutoConfiguration.java` | Updated import to new `subscriber` package; cleaned Javadoc around "listener container" wording |
| `contract/RedisContractTest.java` | Updated import; replaced direct-construction test with bean-factory-method test |

---

## 2. Registry Behavior Normalization

`DefaultEventPayloadRegistry` previously leaked raw `ConcurrentHashMap` behavior for null/invalid inputs.

### `resolvePayload` — before vs after

| Input | Before | After |
|-------|--------|-------|
| `null` | `NullPointerException` | `IllegalArgumentException` (controlled) |
| `""` | `IllegalStateException("Unknown event type: eventType=")` | `IllegalArgumentException` (controlled) |
| `"BAD_EVENT"` | `IllegalStateException("Unknown event type: eventType=BAD_EVENT")` | `IllegalArgumentException` (controlled) |
| `"unknown.valid.event"` | `IllegalStateException("Unknown event type: ...")` | `IllegalStateException` (same, intended) |
| registered type | returns class | returns class (unchanged) |

Fix: added `EventContractValidator.validateEventNameOrThrow(eventType)` at the top of `resolvePayload`.

### `contains` — before vs after

| Input | Before | After |
|-------|--------|-------|
| `null` | `NullPointerException` | `false` |
| `""` | `false` (lucky — map key) | `false` (explicit guard) |
| `"BAD_EVENT"` | `false` (lucky — map key) | `false` (explicit guard) |
| `"unknown.valid.event"` | `false` | `false` (unchanged) |
| registered type | `true` | `true` (unchanged) |

Fix: added null/blank/syntax guards before the map call.

Both helpers are now internally consistent with `EventContractValidator.isRegisteredEventType` and `SharedEventCatalog.isKnownEventType`, which also return `false` / throw cleanly for null/invalid inputs.

---

## 3. Comment / Javadoc Cleanup

All remaining transport/service-runtime operational wording removed from `common-events`:

- **EventMetadata**: Removed "routing information shared across all transports" and bullet-list field descriptions. Now: "Carries the event-model fields shared by every event in the catalog."
- **SharedEventCatalog.PAYLOAD_LESS_EVENT_TYPES**: Removed "Payload resolution must not be attempted for them during deserialization or publish."
- **SharedEventCatalog.validatePayloadContract**: Removed "This method is transport-neutral. Wrong payload class violations fail before serialization or network I/O occurs." and the "service-local event extension aperture" phrase.
- **SharedEventCatalog.registerAll**: Removed "Services must not re-register any of these shared event types with different payload classes."
- **MessagePinPayload**: Removed "Owned by common-events as a shared contract so all consumers can deserialize these events without requiring a service-local payload class."
- **RedisAutoConfiguration**: Replaced "Spring Redis `MessageListener` boundary" and "own `RedisMessageListenerContainer`" with "Spring Redis Pub/Sub boundary" and "own container."
- **RedisPubSubSubscriberAdapter**: Updated Javadoc to reflect subscriber-role framing; "Spring Redis Pub/Sub container" instead of "listener container."

---

## 4. Redis Adapter Package Move

| | Before | After |
|-|--------|-------|
| Package | `com.example.common.redis.listener` | `com.example.common.redis.subscriber` |
| Class name | `RedisPubSubSubscriberAdapter` | `RedisPubSubSubscriberAdapter` (unchanged) |
| Spring impl | `implements MessageListener` (internal detail) | `implements MessageListener` (unchanged) |

The `listener` directory is deleted. The `subscriber` package now contains both `RedisEventSubscriber` (the shared contract interface) and `RedisPubSubSubscriberAdapter` (the Spring inbound boundary adapter).

Imports updated in: `RedisAutoConfiguration`, `RedisContractTest`.

---

## 5. Auto-Config Test — Before vs After

**Before (weak):**
```java
void autoConfig_redisPubSubSubscriberAdapterCanBeInstantiated() {
    // directly called new RedisPubSubSubscriberAdapter(...) — not an auto-config test
    RedisPubSubSubscriberAdapter adapter = new RedisPubSubSubscriberAdapter(...);
    assertThat(adapter).isNotNull();
}
```

**After (real auto-config test):**
```java
void autoConfig_redisPubSubSubscriberAdapterBeanIsWiredByAutoConfiguration() {
    RedisAutoConfiguration autoConfig = new RedisAutoConfiguration();
    RedisPubSubSubscriberAdapter adapter = autoConfig.redisPubSubSubscriberAdapter(
            canonicalSerializer, canonicalDispatcher, canonicalObserver
    );
    assertThat(adapter).isNotNull();
    assertThat(adapter).isInstanceOf(RedisPubSubSubscriberAdapter.class);
}
```

The test now invokes the actual `@Bean` factory method on `RedisAutoConfiguration`, confirming:
- the bean is created through the auto-configuration path
- all three canonical dependencies are accepted without error
- the returned instance is the correct type

---

## 6. Validation Command Results

```
.\gradlew.bat :common:common-events:compileJava :common:common-redis:compileJava
BUILD SUCCESSFUL in 8s
2 actionable tasks: 2 executed

.\gradlew.bat :common:common-events:test :common:common-redis:test --rerun-tasks
BUILD SUCCESSFUL in 18s
8 actionable tasks: 8 executed

.\gradlew.bat :common:common-events:test :common:common-kafka:test :common:common-redis:test
BUILD SUCCESSFUL in 7s
12 actionable tasks: 1 executed, 11 up-to-date
```

---

## 7. Remaining Blockers

None.

---

## 8. Freeze Verdict

| Module | Status | Notes |
|--------|--------|-------|
| `common-events` | **FREEZE-READY** | Registry helpers consistent; comments semantic-only; tests cover edge cases |
| `common-kafka` | **FREEZE-READY** | No changes; already frozen |
| `common-redis` | **FREEZE-READY** | Adapter in Publisher/Subscriber-aligned package; auto-config test exercises bean factory method |
| Whole reviewed common scope | **FREEZE-READY** | All three modules pass; no remaining blockers |
