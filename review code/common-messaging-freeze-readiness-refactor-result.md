# Common Messaging Freeze-Readiness Refactor Result

## 1. Scope

Files changed:

**common-events (main source)**
- `chatappBE/common/common-events/src/main/java/com/example/common/event/SharedEventCatalog.java`
- `chatappBE/common/common-events/src/main/java/com/example/common/event/validation/EventContractValidator.java`
- `chatappBE/common/common-events/src/main/java/com/example/common/integration/presence/PresenceEventType.java`
- `chatappBE/common/common-events/src/main/java/com/example/common/integration/presence/PresenceHeartbeatPayload.java`

**common-events (tests)**
- `chatappBE/common/common-events/src/test/java/com/example/common/event/contract/SharedEventModelContractTest.java`

**common-redis (main source)**
- `chatappBE/common/common-redis/src/main/java/com/example/common/redis/publisher/DefaultRedisEventPublisher.java`
- `chatappBE/common/common-redis/src/main/java/com/example/common/redis/serialization/JsonRedisEventSerializer.java`

**common-redis (tests)**
- `chatappBE/common/common-redis/src/test/java/com/example/common/redis/contract/RedisContractTest.java`

**common-kafka (main source)**
- `chatappBE/common/common-kafka/src/main/java/com/example/common/kafka/producer/DefaultKafkaEventPublisher.java`

**common-kafka (tests)**
- `chatappBE/common/common-kafka/src/test/java/com/example/common/kafka/contract/KafkaContractTest.java`

No files outside the three modules were touched. No files were deleted.

---

## 2. Freeze Blockers Targeted

From the latest current-source review:

- **HIGH** — Payload contract not enforced at publish boundary in either transport. Publishers validated event-name syntax only. Payload-bearing events could be published with `null` payload; Kafka never consulted `SharedEventCatalog` at publish time.
- **HIGH** — `JsonRedisEventSerializer` silently normalized away off-contract input: payload-less events with an unexpected `payload` field returned `payload = null` instead of failing fast.
- **MEDIUM** — `EventContractValidator.validateIdentityOrThrow(...)` duplicated constructor-level identity checks already enforced by `EventMetadata`.
- **MEDIUM** — `EventContractValidator.isRegisteredEventType(...)` was a second independent enum-scanning authority instead of delegating to `SharedEventCatalog`.
- **MEDIUM** — No in-module completeness test to prove enum-to-catalog coverage.
- **LOW** — Misnamed test `validator_validateMetadata_rejectsBlankEventId` asserted success, not rejection.
- **LOW** — `PresenceHeartbeatPayload` Javadoc contained a historical "previously misplaced" note.
- **LOW** — `PresenceEventType.fromValue(...)` lowercased inbound values before matching (lenient compatibility behavior in the canonical enum layer).

---

## 3. Changes Made In Common-Events

### `SharedEventCatalog.java`
Restructured around a private internal `PAYLOAD_MAP`:
- Added `private static final Map<String, Class<?>> PAYLOAD_MAP` as a static initializer. This is the single definition point for all payload-bearing events; both `registerAll` and `PAYLOAD_BEARING_EVENT_TYPES` now derive from it.
- Added `public static final Set<String> PAYLOAD_BEARING_EVENT_TYPES` — derived from `PAYLOAD_MAP.keySet()`. This was the missing half of the canonical contract surface.
- `registerAll(EventPayloadRegistry)` is now a one-liner: `PAYLOAD_MAP.forEach(registry::register)`. The catalog no longer has two parallel definitions of the same data.
- Added `enforcePublishPayloadContract(String eventType, Object payload)` static method. Rules:
  - Payload-less event type + non-null payload → `IllegalArgumentException("Unexpected payload for payload-less event type: ...")`
  - Payload-bearing event type + null payload → `IllegalArgumentException("Missing required payload for payload-bearing event type: ...")`
  - Event type not in either catalog set → no enforcement (service-local event, not in scope)
- Updated class Javadoc to document all three contract surfaces and the enforcement intent.

### `EventContractValidator.java`
- **Removed** `validateIdentityOrThrow(String, String, String, Instant)` static method. It duplicated checks already enforced by the `EventMetadata` constructor and had no callsites inside the three modules.
- **Replaced** `isRegisteredEventType(String)` enum-scanning logic with a catalog-backed check:  
  `SharedEventCatalog.PAYLOAD_BEARING_EVENT_TYPES.contains(eventType) || SharedEventCatalog.PAYLOAD_LESS_EVENT_TYPES.contains(eventType)`  
  This eliminates the second independent knowledge source that could drift from the catalog.
- Updated class Javadoc to reflect the removed method, the catalog delegation, and removed the stale example list.
- Removed the now-unused `Arrays` and `*EventType` imports.

### `PresenceEventType.java`
- Removed `value.toLowerCase()` in `fromValue(String)`. The canonical enum now matches values with exact case only, consistent with the event naming convention (all values are already `lowercase.dot.separated`). Lenient compatibility behavior is removed.

### `PresenceHeartbeatPayload.java`
- Removed the historical note "This was previously misplaced in the user package as `UserPresencePayload`" from the class Javadoc. The payload is a canonical shared contract type; historical migration notes do not belong in the final source.

---

## 4. Changes Made In Common-Redis

### `DefaultRedisEventPublisher.java`
- Added `SharedEventCatalog.enforcePublishPayloadContract(eventType, payload)` inside the VALIDATE stage, after `validateEventNameOrThrow`. This call runs before any serialization or I/O.
- The validation logic extracts `eventType` and `payload` from the envelope before the try block to make the separation explicit.
- Any `IllegalArgumentException` from the new contract check is caught by the existing VALIDATE catch block, wrapped in `RedisPubSubException`, and attributed to the VALIDATE lifecycle stage — not the PUBLISH stage.
- Added `SharedEventCatalog` import.

### `JsonRedisEventSerializer.java`
- In Phase 3 (payload resolution), restructured the conditional from `if (!isPayloadLess)` to explicit `if (isPayloadLess) { ... } else { ... }`:
  - **Payload-less branch**: if `payloadNode != null && !payloadNode.isNull()` → throw `RedisPubSubException` with `"unexpected payload for payload-less event type: ..."`. Previously this case silently set `payloadObject = null`.
  - **Payload-bearing branch**: unchanged — throws for missing payload, resolves registry, deserializes.
- Updated Javadoc to list all nine distinct failure categories, including the new "unexpected payload for payload-less event type" category (was missing previously).

---

## 5. Changes Made In Common-Kafka

### `DefaultKafkaEventPublisher.java`
- Added `SharedEventCatalog.enforcePublishPayloadContract(eventType, payload)` inside the VALIDATE stage, after `validateEventNameOrThrow`. Same pattern as the Redis publisher.
- Any `IllegalArgumentException` from the contract check is caught and wrapped in `KafkaMessagingException` attributed to the VALIDATE stage.
- Added `SharedEventCatalog` import.
- Updated class Javadoc to reflect that the publisher now "enforces the canonical shared payload contract before sending".

---

## 6. Runtime Contract Enforcement Added

### Publish-time payload checks
Both `DefaultRedisEventPublisher` and `DefaultKafkaEventPublisher` now enforce the canonical shared payload contract in their VALIDATE stage before any I/O. The check uses `SharedEventCatalog.enforcePublishPayloadContract(eventType, payload)`:
- Payload-bearing shared event + null payload → `IllegalArgumentException` → caught → VALIDATE-stage transport exception.
- Payload-less shared event + non-null payload → `IllegalArgumentException` → caught → VALIDATE-stage transport exception.
- Service-local event type (not in catalog) → no enforcement, proceeds normally.

### Payload-less unexpected payload checks (deserialize)
`JsonRedisEventSerializer` now rejects payload-less events that arrive with a non-null `payload` JSON field. This was previously silently normalized to `null`. The check is distinct from the other failure categories and produces a dedicated exception message.

### Validation-stage error handling
- VALIDATE exceptions (both pattern violations and contract violations) are caught before the PUBLISH block.
- Both publishers maintain distinct VALIDATE and PUBLISH stages; enforcement failures are never misclassified as PUBLISH failures.
- Exception messages include the lifecycle stage name (`"VALIDATE"`) and the transport-specific exception type (`RedisPubSubException` / `KafkaMessagingException`).

---

## 7. Tests Added / Updated

### Catalog completeness tests (`SharedEventModelContractTest`)
- `catalog_payloadBearingAndPayloadLessSetsAreDisjoint` — verifies `PAYLOAD_BEARING_EVENT_TYPES ∩ PAYLOAD_LESS_EVENT_TYPES = ∅`.
- `catalog_everyEnumValueIsInExactlyOneSet` — iterates all six shared event enums and verifies every value appears in exactly one catalog set. Fails if a new enum value is added without registering it. Also fails if the catalog contains values with no corresponding enum entry.
- `catalog_registerAllPopulatesExactlyPayloadBearingEvents` — verifies `registerAll` populates a registry with every payload-bearing type and no payload-less type.
- `catalog_registerAllIsIdempotentForSameClass` — verifies repeated `registerAll` calls do not conflict.

### Payload-bearing vs payload-less enforcement tests (`SharedEventModelContractTest`)
- `catalog_enforceContract_rejectsNullPayloadForPayloadBearingEvent`
- `catalog_enforceContract_rejectsNonNullPayloadForPayloadLessEvent`
- `catalog_enforceContract_passesForPayloadBearingEventWithPayload`
- `catalog_enforceContract_passesForPayloadLessEventWithNullPayload`
- `catalog_enforceContract_passesForUnknownEventTypeRegardlessOfPayload`

### Catalog delegation test (`SharedEventModelContractTest`)
- `validator_isRegisteredEventType_delegatesToCatalog` — verifies the catalog-backed implementation returns correct results for a known payload-bearing type, a known payload-less type, an unknown type, null, and empty string.

### Misnamed test fixed (`SharedEventModelContractTest`)
- Removed `validator_validateMetadata_rejectsBlankEventId` — the test body created valid metadata and asserted no exception (misleading name). The success case is fully covered by the existing `validator_validateMetadata_passesForValidMetadata`.

### Publish-time enforcement tests (`KafkaContractTest`)
- `publisher_rejectsMissingPayloadForPayloadBearingEvent` — verifies `KafkaMessagingException` with VALIDATE message and "Missing required payload" cause.
- `publisher_rejectsUnexpectedPayloadForPayloadLessEvent` — verifies `KafkaMessagingException` with VALIDATE message and "Unexpected payload" cause.

### Publish-time enforcement tests (`RedisContractTest`)
- `publisher_rejectsMissingPayloadForPayloadBearingEvent` — verifies `RedisPubSubException` with VALIDATE message and "Missing required payload" cause.
- `publisher_rejectsUnexpectedPayloadForPayloadLessEvent` — verifies `RedisPubSubException` with VALIDATE message and "Unexpected payload" cause.

### Redis deserialize enforcement test (`RedisContractTest`)
- `deserializer_rejectsUnexpectedPayloadForPayloadLessEvent` — verifies that an incoming JSON with a non-null `payload` field for a payload-less event type throws `RedisPubSubException` with `"unexpected payload for payload-less event type"`.

### Build result
`BUILD SUCCESSFUL` — all 12 tasks executed across the three modules:
```
:common:common-events:test   ✓
:common:common-redis:test    ✓
:common:common-kafka:test    ✓
```

---

## 8. Deleted / Reduced Current Softness

### Redundant validation logic removed
- `EventContractValidator.validateIdentityOrThrow(...)` removed. This static method duplicated identity-field validation that the `EventMetadata` constructor already enforces at object construction time. No callsites existed inside the three modules.

### Catalog knowledge consolidation
- `EventContractValidator.isRegisteredEventType(...)` no longer maintains its own enum-scanning loop. It delegates to `SharedEventCatalog`, eliminating the risk of drift between the validator's knowledge and the catalog's knowledge.
- `SharedEventCatalog.registerAll(...)` no longer duplicates the payload map inline. It reads from `PAYLOAD_MAP`, making the payload-bearing event list single-source.

### Lenient behavior removed
- `PresenceEventType.fromValue(...)` no longer lowercases inbound values. The canonical enum now applies exact-match semantics only.

### Historical notes cleaned
- `PresenceHeartbeatPayload` Javadoc no longer contains the "previously misplaced" migration note.

### Misleading test removed
- `validator_validateMetadata_rejectsBlankEventId` (misnamed, asserted success, redundant) was removed from `SharedEventModelContractTest`.

### Compatibility surface status
- `RedisEventRegistry` and `DefaultRedisEventRegistry` remain. They are still the auto-configured bean type injected by downstream services and remain necessary for in-scope wiring. They were not changed.
- `KafkaEventHandler` and `KafkaEventDispatcher` remain. They are coherent module code and are covered by current in-module tests. They were not changed.

---

## 9. Remaining External Migration Risks

These require edits outside the three allowed modules and are not addressed in this pass:

- **`chat-service`**: `KafkaChatMessageEventPublisher`, `KafkaReactionEventPublisher` — still use service-local Kafka publishers not wired through `DefaultKafkaEventPublisher`. `ChatRedisEventConfig` — still contains service-local Redis re-registration that may conflict with the shared catalog. Redis subscriber classes in `realtime/subscriber/*` — still depend on `RedisEventRegistry` adapter injection pattern.
- **`presence-service`**: `PresenceRedisRegistryConfig` — still contains service-local Redis re-registration. Redis classes in `presence/redis/*` — depend on `RedisEventRegistry` adapter.
- **`notification-service`**: `NotificationEventProducer` — still uses removed Kafka alias constants or a service-local producer not aligned with `DefaultKafkaEventPublisher`.
- **`auth-service`**: `AccountCreatedEventProducer` — still uses a service-local Kafka producer.

Until these services migrate to `DefaultKafkaEventPublisher` and `DefaultRedisEventPublisher`, the new publish-time contract enforcement will not apply to their events in production.

---

## 10. Final Summary

The three modules are now closer to a frozen standard:

**Runtime contract enforcement** is active at the publish boundary for both transports. Any attempt to publish a payload-bearing shared event without a payload, or a payload-less event with a payload, is rejected in the VALIDATE stage with a clear error before any I/O occurs. This closes the highest-priority freeze blocker.

**Deserialize enforcement** is complete in `JsonRedisEventSerializer`. All nine failure categories (including unexpected payload for payload-less events) are now distinct and produce operationally useful error messages.

**`SharedEventCatalog` is now the single source of truth** for the payload-bearing event set. `PAYLOAD_BEARING_EVENT_TYPES` is derived from the same internal map as `registerAll`, eliminating drift between the two. `EventContractValidator.isRegisteredEventType` delegates to the catalog instead of maintaining its own enum-scanning copy.

**Catalog completeness is now self-enforcing** via a test. Adding a new enum value without updating `SharedEventCatalog` will fail `catalog_everyEnumValueIsInExactlyOneSet`.

**Remaining in-scope softness** (`RedisEventRegistry`/`DefaultRedisEventRegistry` adapter surface, `KafkaEventHandler`/`KafkaEventDispatcher` optional consumer helpers) is preserved as-is. Both remain coherent, tested, and necessary for current wiring. Removing them requires downstream migration outside scope.

**The only remaining freeze risk** is the external migration path: downstream services must adopt `DefaultKafkaEventPublisher` and `DefaultRedisEventPublisher` to benefit from the new runtime enforcement. That work is entirely outside the three allowed modules.
