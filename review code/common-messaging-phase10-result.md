# Phase 10 Result: Contract Tests for Common Messaging Modules

## Summary

Phase 10 added pure JUnit 5 + AssertJ contract tests to `common-events`, `common-kafka`, and `common-redis`. All **44 tests pass** with zero failures.

| Module | Test Class | Tests |
|---|---|---|
| `common-events` | `SharedEventModelContractTest` | 11 |
| `common-kafka` | `KafkaContractTest` | 18 |
| `common-redis` | `RedisContractTest` | 15 |
| **Total** | | **44** |

---

## Build Changes

### `common-events/build.gradle`
Added `jackson-datatype-jsr310` (both `implementation` and `testImplementation`) to enable `Instant` serialization in tests; previously missing.

```groovy
implementation 'com.fasterxml.jackson.datatype:jackson-datatype-jsr310'
testImplementation 'org.junit.jupiter:junit-jupiter'
testImplementation 'org.assertj:assertj-core'
testImplementation 'com.fasterxml.jackson.datatype:jackson-datatype-jsr310'
testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
```

### `common-kafka/build.gradle`
Added `jackson-datatype-jsr310` test dependency (already had `jackson-databind`):

```groovy
testImplementation 'com.fasterxml.jackson.datatype:jackson-datatype-jsr310'
```

### `common-redis/build.gradle`
Already had both `jackson-databind` and `jackson-datatype-jsr310` as test deps — no changes needed.

---

## Test Coverage

### Phase 10.1 — Shared Event Model (`SharedEventModelContractTest`)

| Test | What It Verifies |
|---|---|
| `eventEnvelope_serializesAndDeserializesRoundTrip` | `EventEnvelope<T>` full Jackson round-trip preserving all 5 `EventMetadata` fields |
| `eventEnvelope_preservesEventIdAndCorrelationIdOnRoundTrip` | Specific identity fields survive serialization |
| `eventEnvelope_implementsEventInterface` | `EventEnvelope` is an `Event<T>` at runtime |
| `allEventTypeEnumValues_passValidation` | Every `EventType` value passes `isValidEventType()` |
| `allEventTypeEnumValues_followPattern` | Every `EventType` value matches `EVENT_NAME_PATTERN` regex |
| `presenceEventTypes_useHyphenNotUnderscore` | Phase 3 fix: presence types use hyphens (not underscores) |
| `validator_acceptsValidEventTypePatterns` | Validator accepts conforming event type strings |
| `validator_rejectsInvalidEventTypePatterns` | Validator rejects underscores, uppercase, empty strings |
| `validator_validateMetadata_rejectsBlankEventId` | `validateMetadata` throws `IAE` on blank eventId |
| `validator_validateMetadata_rejectsNonConformingEventType` | `validateMetadata` throws `IAE` on uppercase event type |
| `validator_validateMetadata_passesForValidMetadata` | `validateMetadata` succeeds for fully valid metadata |

### Phase 10.1 — Kafka Contracts (`KafkaContractTest`)

| Test | What It Verifies |
|---|---|
| `routingContext_mapsEventMetadataConsistently` | `KafkaEventRoutingContext.of()` maps all fields correctly |
| `routingContext_handlesNullEventGracefully` | `KafkaEventRoutingContext.of(topic, key, null)` does not throw |
| `kafkaEventProducer_hasDistinctIKafkaEventAndEnvelopeOverloads` | Two `publish` overloads exist on `KafkaEventProducer` |
| `dispatcher_routesEventToMatchingHandler` | `KafkaEventDispatcher` routes to exact handler by `eventType` |
| `dispatcher_logsWarningAndSkipsForUnknownEventType` | Dispatcher does not throw for unmapped event types |
| `dispatcher_throwsOnDuplicateHandlerForSameEventType` | Dispatcher throws `ISE` on duplicate handler registration |
| `dispatcher_resolvesDeprecatedPresenceAlias` | Underscored presence type dispatches to hyphenated handler |
| `registry_registersAndResolvesPayloadClass` | `DefaultKafkaEventRegistry` register/resolvePayload/contains |
| `registry_throwsOnDuplicateRegistration` | Registry throws `ISE` on duplicate `eventType` |
| `registry_throwsOnUnknownEventType` | `resolvePayload` throws `ISE` for unknown type |
| `registry_resolvesDeprecatedPresenceAlias` | Registry resolves underscored presence alias |

### Phase 10.2 — Kafka Compatibility Bridges (`KafkaContractTest`, continued)

| Test | What It Verifies |
|---|---|
| `legacyIKafkaEventPublisher_isAssignableFromKafkaEventProducer` | `IKafkaEventPublisher` delegates correctly to producer |
| `deprecatedKafkaEventPublisherInterface_isSubtypeOfIKafkaEventPublisher` | `KafkaEventPublisher` → `IKafkaEventPublisher` hierarchy |
| `deprecatedKafkaEventInterface_isSubtypeOfIKafkaEvent` | `KafkaEvent` → `IKafkaEvent` hierarchy + `getEventIdAsString()` |
| `kafkaEventConsumer_defaultHandlersIsEmpty` | `KafkaEventConsumer` default `handlers()` returns empty list |
| `kafkaEventConsumer_defaultOnUnhandledEventIsNoOp` | Default `onUnhandledEvent` does not throw |
| `legacyKafkaTopicsBridge_delegatesToCanonicalValues` | `integration.kafka.KafkaTopics` values match `kafka.topic.KafkaTopics` |
| `kafkaTopicsBridge_doesNotDuplicateValues` | Bridge constants are the **same string references** (not copies) |

### Phase 10.1 — Redis Contracts (`RedisContractTest`)

| Test | What It Verifies |
|---|---|
| `deserializer_preservesEventIdAndCorrelationId` | Phase 2 fix: `JsonRedisMessageSerializer` preserves `eventId` + `correlationId` |
| `deserializer_derivesEventIdFromMessageIdWhenEventIdAbsent` | `eventId` falls back to `messageId` |
| `deserializer_acceptsLegacyTypeFieldAlias` | Legacy `type` field maps to `eventType` |
| `deserializer_acceptsLegacyDataFieldAlias` | Legacy `data` field maps to `payload` |
| `deserializer_normalisesDeprecatedPresenceEventTypeAlias` | Phase 3 fix: underscored presence alias resolved at deserialization |
| `registry_registersAndResolvesPayloadClass` | `DefaultRedisMessageRegistry` register/resolvePayload |
| `registry_throwsOnDuplicateRegistration` | Duplicate registration throws `ISE` |
| `registry_throwsOnUnknownEventType` | Unknown type throws `ISE` |
| `registry_resolvesDeprecatedPresenceAlias` | Registry resolves underscored presence alias |
| `defaultRedisEventRegistry_isSubtypeOfDefaultRedisMessageRegistry` | `DefaultRedisEventRegistry extends DefaultRedisMessageRegistry` |
| `routingContext_mapsMessageMetadataConsistently` | `RedisEventRoutingContext.of()` maps all fields correctly |
| `routingContext_handlesNullMessageGracefully` | `RedisEventRoutingContext.of(channel, null)` does not throw |

### Phase 10.2 — Redis Compatibility Bridges (`RedisContractTest`, continued)

| Test | What It Verifies |
|---|---|
| `legacyIRedisPublisher_isAssignableFromRedisEventPublisher` | `IRedisPublisher extends RedisEventPublisher` hierarchy |
| `realtimeRedisChannels_bridgeDelegatesToRedisChannels` | `RealtimeRedisChannels` constants match `RedisChannels` values |
| `realtimeRedisChannels_helperMethodsReturnSameValueAsCanonical` | Bridge helper methods (`notificationUser`, `presenceRoom`) match canonical |

---

## Gaps Not Yet Covered

1. **`DefaultKafkaEventProducer` integration** — The canonical producer's `publish(topic, key, EventEnvelope)` method is tested via reflection for existence, but its actual envelope-to-`IKafkaEvent` adapter logic is not tested. This requires a mock `KafkaTemplate` or an embedded Kafka broker.
2. **`DefaultRedisEventPublisher` integration** — Similar: publishing via `RedisTemplate` is not covered; requires a Redis test container or embedded Redis.
3. **`KafkaEventLogger` / `IKafkaEventLogger`** — Logging behaviour under error conditions not covered.
4. **`JsonRedisEventSerializer` (canonical)** — Only the deprecated `JsonRedisMessageSerializer` is directly tested; `JsonRedisEventSerializer` inherits all behaviour but has no dedicated test.
5. **`KafkaAutoConfiguration` / `RedisAutoConfiguration`** — Spring bean wiring is not covered; requires a Spring `ApplicationContext`.
6. **Presence alias round-trip completeness** — Only `status_changed` alias tested; other deprecated aliases (`room_typing`, `stop_typing`, etc.) not individually exercised.
7. **`RedisContractVersions` / `RealtimeContractVersions`** — Version constant bridging not tested.

---

## Key Decisions

- **No Spring context**: All tests are pure unit tests; no `@SpringBootTest` or embedded infrastructure.
- **Anonymous implementations** for multi-method interfaces (`KafkaEventProducer`, `IKafkaEventPublisher`, `KafkaEventPublisher`, `IRedisPublisher`) since they are not functional interfaces (two `publish` overloads each).
- **`jsr310` added to `common-events`** as both production and test dependency — `EventMetadata` uses `Instant` and Jackson serialization tests require `JavaTimeModule`.
- **Deprecated API usage** in tests is intentional (testing backward compat bridges) — suppressed at compiler level by Gradle note only, not at source level.

---

## Files Added / Changed

### New test files:
- `common-events/src/test/java/com/example/common/event/contract/SharedEventModelContractTest.java`
- `common-kafka/src/test/java/com/example/common/kafka/contract/KafkaContractTest.java`
- `common-redis/src/test/java/com/example/common/redis/contract/RedisContractTest.java`

### Modified build files:
- `common-events/build.gradle` — added `jackson-datatype-jsr310` (implementation + testImplementation)
- `common-kafka/build.gradle` — added `jackson-datatype-jsr310` (testImplementation)
