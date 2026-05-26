# common-messaging Current-Source Refactor Pass — Result

**OpenSpec Change:** `common-messaging-current-source-refactor-pass`  
**Scope:** `common-events`, `common-redis`, `common-kafka` — in-scope source only, no downstream service changes  
**Build Result:** `BUILD SUCCESSFUL in 54s — 12 actionable tasks: 12 executed`

---

## Current-Source Inventory (Pre-Refactor)

### common-events

| File | Notes |
|------|-------|
| `EventPayloadRegistry.java` | Interface — had transport-ownership Javadoc leakage |
| `DefaultEventPayloadRegistry.java` | Implementation — clean |
| `Event.java` | Interface — had transport name list in comment |
| `EventEnvelope.java` | Record — construction-level null-metadata guard in place |
| `EventMetadata.java` | Class — construction-time validation in place; had transport list in Javadoc |
| `SharedEventCatalog.java` | Canonical catalog — mappings validated as correct |
| `EventContractValidator.java` | Validator — clean |
| Integration types (~29 files) | Account, chat, friendship, notification, presence, user — clean |

### common-redis

| File | Notes |
|------|-------|
| `RedisChannels.java` | Constants — clean |
| `RedisAutoConfiguration.java` | Config — clean |
| `DefaultRedisEventPublisher.java` | Had redundant `validateIdentityOrThrow` in VALIDATE stage |
| `RedisEventPublisher.java` | Interface — clean |
| `RedisEventRegistry.java` | Injection-by-type adapter — kept (downstream services inject by this type) |
| `DefaultRedisEventRegistry.java` | Adapter impl — kept |
| `JsonRedisEventSerializer.java` | Serializer — clean (Phase 5 redundancy already removed prior) |
| `RedisEventSerializer.java` | Interface — clean |
| `RedisEventDispatcher.java` | Dispatcher — clean |
| `RedisEventListener.java` | Listener — clean |
| `RedisEventHandler.java` | Interface — had stale "Migration note" Javadoc paragraph |
| `RedisEventRoutingContext.java` | Record — clean |
| `RedisPubSubObserver.java` | Interface — clean |
| `Slf4jRedisPubSubLogger.java` | Implementation — clean |
| `RedisPubSubException.java` | Exception — clean |

### common-kafka

| File | Notes |
|------|-------|
| `KafkaAutoConfiguration.java` | Config — had stale comment referencing non-existent `DefaultKafkaEventProducer` |
| `DefaultKafkaEventPublisher.java` | Had redundant `validateIdentityOrThrow` in VALIDATE stage |
| `KafkaEventPublisher.java` | Interface — clean |
| `KafkaEventDispatcher.java` | Dispatcher — clean (observer wiring verified) |
| `KafkaEventHandler.java` | Interface — clean |
| `KafkaEventObserver.java` | Interface — clean (logDispatch/logDispatchError no-op defaults) |
| `Slf4jKafkaEventLogger.java` | Implementation — clean |
| `KafkaEventRoutingContext.java` | Record — clean |
| `KafkaTopics.java` | Constants — clean (aggregate + infra routes only, no semantic aliases) |
| `KafkaMessagingException.java` | Exception — clean |
| `KafkaContractTest.java` (test) | Had `defaultKafkaEventProducer_isAssignableToKafkaEventProducer` referencing non-existent `KafkaEventProducer`/`DefaultKafkaEventProducer` — compile failure |

---

## Changes Made

### common-events

**`EventPayloadRegistry.java`** — Removed transport-ownership Javadoc paragraph:
- Removed: `"Transport modules that own JSON deserialization (such as common-redis) may extend this interface with transport-scoped aliases for injection-by-type compatibility."`
- Rationale: common-events must not describe or direct transport module behavior.

**`Event.java`** — Removed transport name list from class Javadoc:
- Changed: `"regardless of transport (Kafka, Redis, etc)"` → `"regardless of transport"`
- Rationale: transport names are cross-module ownership pollution in the contract layer.

**`EventMetadata.java`** — Removed transport name list from class Javadoc:
- Changed: `"shared across all transport mechanisms (Kafka, Redis, etc.)"` → `"shared across all transports"`

### common-redis

**`DefaultRedisEventPublisher.java`** — Removed redundant `validateIdentityOrThrow` from VALIDATE stage:
- Removed: `EventContractValidator.validateIdentityOrThrow(eventId, correlationId, sourceService, createdAt)`
- Kept: `EventContractValidator.validateEventNameOrThrow(eventType)` — still non-redundant (validates dot-lowercase format, not just null/blank)
- Rationale: `EventMetadata` constructor now validates all 5 identity fields at construction time. Any `EventMetadata` that reached the publisher is already valid. `validateIdentityOrThrow` was unreachable in the failure path and added no protection.

**`RedisEventHandler.java`** — Removed stale migration note from Javadoc:
- Removed: `"Migration note: the previous onEvent method has been removed. Service implementations that previously overrode onEvent must override handle instead."`
- Rationale: Dead documentation with no active meaning; the `onEvent` method no longer exists anywhere in the codebase.

### common-kafka

**`DefaultKafkaEventPublisher.java`** — Removed redundant `validateIdentityOrThrow` from VALIDATE stage:
- Same reasoning as Redis: `EventMetadata` construction-time validation makes the identity check unreachable in the failure path.

**`KafkaAutoConfiguration.java`** — Removed stale bean comment:
- Removed: `"// Instantiates DefaultKafkaEventProducer (not DefaultKafkaEventPublisher) so that downstream services still injecting the deprecated KafkaEventProducer interface are satisfied by the same bean. New code should inject KafkaEventPublisher."`
- Rationale: `DefaultKafkaEventProducer` and `KafkaEventProducer` do not exist in the current source tree. The comment was factually wrong and described a compat layer that was never committed or has been deleted.

**`KafkaContractTest.java`** — Removed `defaultKafkaEventProducer_isAssignableToKafkaEventProducer` test:
- Removed the entire test body which referenced `KafkaEventProducer.class` and `DefaultKafkaEventProducer.class`.
- These types do not exist in current source and were not imported — the test was a compile failure.
- `StubProducerFactory` inner class preserved (still used by `publisher_rejectsBlankTopic` and `kafkaEventPublisher_allowsTransportTopicNamesOutsideEventNamePattern`).

---

## What Was NOT Changed (Confirmed Clean)

| Item | Decision |
|------|----------|
| `RedisEventRegistry` / `DefaultRedisEventRegistry` | Kept — injection-by-type adapter with honest Javadoc; downstream services inject `RedisEventRegistry` by type; removing requires downstream changes outside scope |
| `KafkaTopics` | Kept as-is — only aggregate routes + infra routes; no semantic aliases present |
| `SharedEventCatalog` mappings | Validated as correct against all current event-type enums |
| `EventContractValidator.validateEventNameOrThrow` in both publishers | Kept — validates dot-lowercase format not checked by `EventMetadata` constructor |
| All observer/dispatcher abstractions | Kept — `KafkaEventObserver` and `RedisPubSubObserver` are meaningfully used (publisher + dispatcher) |
| `EventContractValidator` class itself | No changes — used by both publishers for format validation |

---

## Validation Result

```
> Task :common:common-events:test
> Task :common:common-redis:test
> Task :common:common-kafka:test

BUILD SUCCESSFUL in 54s
12 actionable tasks: 12 executed
```

All 12 tasks executed (no UP-TO-DATE skips due to `--rerun-tasks`). All tests pass.
