# Common Messaging Full Current Review

## 1. Scope Reviewed

Reviewed current source only under:

- `chatappBE/common/common-events`
- `chatappBE/common/common-redis`
- `chatappBE/common/common-kafka`

The historical review/result markdown files listed in the request were used only as context. Every current-state claim below was checked against the current source tree.

Limited out-of-scope checks were used only to confirm dependency direction and downstream migration risk. I did not treat downstream code as part of the in-scope architecture verdict except where it affects freeze/adoption risk.

Verification run for the three scoped modules:

- `.\gradlew.bat :common:common-events:test :common:common-redis:test :common:common-kafka:test --rerun-tasks`
- Result: `BUILD SUCCESSFUL in 20s`; 12 tasks executed.

External compile risk check:

- `.\gradlew.bat :auth-service:compileJava :chat-service:compileJava :notification-service:compileJava :presence-service:compileJava :friendship-service:compileJava :user-service:compileJava --continue -x test`
- Result: failed in all six service compile tasks. Details are summarized in section 15.

## 2. Executive Summary

The three scoped modules are structurally much cleaner now. `common-events` is the in-scope owner of shared event enums and shared payload DTO mappings. `common-redis` is now focused on Redis pub/sub concerns. `common-kafka` no longer carries semantic topic aliases; `KafkaTopics` contains only aggregate/infrastructure routes.

The foundation is not strict enough to freeze as a final standard yet. The main remaining in-scope blocker is that publish-time enforcement is still only a nullness check. `SharedEventCatalog` owns event-to-payload mappings, but `SharedEventCatalog.enforcePublishPayloadContract(...)` does not verify that a payload-bearing event is published with the canonical payload class. A `chat.message.sent` envelope with any non-null object can pass the common publishers.

Redis deserialization is stricter now: payload-bearing events must have payload, payload-less events must not have payload, and unknown event types are rejected unless a registry explicitly knows them. The test suite now proves enum-to-catalog completeness and the null/presence enforcement paths. It does not prove publish-time payload class correctness.

Downstream services are still far behind the current common standard. Several services currently fail compilation because they reference removed wrapper events, removed Redis message APIs, removed Kafka topic aliases, or service-local event classes that no longer exist in the scoped modules.

## 3. Current Architecture Summary

`common-events` currently owns:

- `Event`, `EventEnvelope`, and `EventMetadata`
- shared semantic event enums under `com.example.common.integration.*`
- shared payload DTOs under the same integration packages
- `SharedEventCatalog`, which declares payload-bearing and payload-less shared events
- `EventPayloadRegistry` / `DefaultEventPayloadRegistry`
- `EventContractValidator`

`common-redis` currently owns:

- Redis channel constants in `RedisChannels`
- Redis publish contract and implementation
- JSON serializer/deserializer for `EventEnvelope`
- Redis listener, dispatcher, handler contract, observability, routing context, and exception
- Redis auto-configuration
- Redis-specific registry adapter types for injection compatibility

`common-kafka` currently owns:

- Kafka route constants in `KafkaTopics`
- Kafka publish contract and implementation
- Kafka optional dispatcher/handler helpers
- Kafka observability, routing context, exception, and auto-configuration

The build graph direction is clean:

- `common-events` does not depend on Redis or Kafka.
- `common-redis` depends on `common-events`.
- `common-kafka` depends on `common-events`.
- Source scans found no Redis/Kafka imports inside `common-events`, no Kafka imports inside `common-redis`, and no Redis imports inside `common-kafka`.

## 4. What Is Fully Fixed

- `common-kafka` no longer duplicates shared semantic event names in `KafkaTopics`. Current constants are only:
  - `TOPIC_FRIENDSHIP_EVENTS`
  - `TOPIC_FRIENDSHIP_REQUEST_EVENTS`
  - `TOPIC_SYSTEM_DEAD_LETTER`
  - `TOPIC_SYSTEM_RETRY`

- `common-events` now has an explicit catalog split:
  - `PAYLOAD_BEARING_EVENT_TYPES`, derived from `PAYLOAD_MAP`
  - `PAYLOAD_LESS_EVENT_TYPES`, explicitly declared
  - `registerAll(...)`, derived from the same `PAYLOAD_MAP`

- `common-events` now self-tests enum-to-catalog completeness. `SharedEventModelContractTest.catalog_everyEnumValueIsInExactlyOneSet` checks every value from `AccountEventType`, `ChatEventType`, `FriendshipEventType`, `NotificationEventType`, `PresenceEventType`, and `UserEventType`.

- Payload-bearing vs payload-less disjointness is tested by `catalog_payloadBearingAndPayloadLessSetsAreDisjoint`.

- Redis deserialize enforcement is fixed for both important nullness directions:
  - payload-bearing event without payload is rejected
  - payload-less event with non-null payload is rejected

- Redis and Kafka publishers now call `SharedEventCatalog.enforcePublishPayloadContract(...)` in the `VALIDATE` stage before transport I/O.

- `EventContractValidator.isRegisteredEventType(...)` now delegates to `SharedEventCatalog` instead of scanning enums independently.

- `EventMetadata` and `EventEnvelope` constructors now enforce structural invariants:
  - metadata identity fields cannot be null/blank
  - `createdAt` cannot be null
  - envelope metadata cannot be null

- The scoped module tests pass when rerun from source.

## 5. What Is Partially Fixed

- Publish-time payload enforcement is only partially fixed. Publishers reject missing payloads for payload-bearing events and unexpected payloads for payload-less events, but they do not reject the wrong payload class for a payload-bearing shared event.

- `SharedEventCatalog` is the declaration source for mappings, but not yet the full runtime source. Its internal `PAYLOAD_MAP` is private and is not used by publish-time enforcement to verify payload class.

- Off-catalog event handling is intentionally open-world. `SharedEventCatalog.enforcePublishPayloadContract(...)` passes unknown syntactically valid event types through. That is acceptable only if the standard explicitly allows service-local events outside `common-events`.

- Redis registry adapters are cleaned up but still present. `RedisEventRegistry` and `DefaultRedisEventRegistry` add no Redis-specific behavior beyond `EventPayloadRegistry`; they remain compatibility surface.

- Kafka dispatcher/handler helpers are internally coherent and tested, but repository scans found no downstream production implementations. They are honest optional helpers, not yet proven as the canonical consumer standard.

- Test completeness is strong for enum coverage and nullness enforcement, but incomplete for payload class enforcement and malformed-but-deserializable payload DTOs.

## 6. Remaining Problems
### High

- Publish-time enforcement does not enforce canonical payload class mappings. `SharedEventCatalog.PAYLOAD_MAP` maps event type to payload class, but `enforcePublishPayloadContract(...)` only checks `payload == null` or `payload != null`. It never checks `expectedPayloadClass.isInstance(payload)`. The current test `catalog_enforceContract_passesForPayloadBearingEventWithPayload` passes `new Object()` for an arbitrary payload-bearing event, which codifies the gap. This is a true freeze blocker if "shared payload contract" means event-to-payload-class correctness, not only payload presence.

- Current downstream service source is not repository-wide freeze-ready. The external compile check failed for `auth-service`, `chat-service`, `notification-service`, `presence-service`, `friendship-service`, and `user-service`. This does not prove the three common modules are dirty, but it blocks declaring the current messaging standard adopted and stable across the backend.

### Medium

- Unknown syntactically valid event types are allowed through publish-time validation. This is not a bug if service-local events are explicitly allowed, but it means off-contract rejection is not closed-world strict. The standard needs a clear policy: either reject every event not in `SharedEventCatalog`, or document and test the service-local aperture.

- `EventContractValidator.validateMetadata(...)` still overlaps with constructor validation in `EventMetadata`. Its non-redundant value is event-name pattern validation; most identity-field checks are duplicate.

- `EventMetadata` does not enforce event-name pattern construction-time. That can be a reasonable layering choice, but it means invalid event metadata objects can exist until a publisher or validator is invoked.

- Redis deserialize exceptions are clear in message text but not machine-categorized. `RedisPubSubException` stores only `channel`, and serializer/deserializer failures use channel `"unknown"`.

- Redis handler dispatch logs and skips missing handlers. That is operationally gentle, but not strict. It is acceptable for pub/sub fanout only if missing handlers are not contract violations.

- Kafka has no common inbound deserialization/contract enforcement equivalent to Redis. This may be transport-specific because Spring Kafka listeners are service-owned, but it leaves publish-time checks as Kafka's only common enforcement point.

- Kafka dispatcher abstractions are optional and not adopted by current downstream source. They are not fake, but they are not yet the proven consumer standard.

### Low

- `RedisEventRegistry` and `DefaultRedisEventRegistry` remain as transport-specific duplicate type surface over `EventPayloadRegistry`.

- `SharedEventCatalog.enforcePublishPayloadContract(...)` is named broadly but currently enforces only payload presence/absence. The name and Javadoc overstate strictness if the payload class mapping is considered part of the contract.

- Some tests use manually registered `String.class` mappings for shared event names. That is fine for serializer round-trip mechanics, but it should not be mistaken for canonical mapping coverage.

- The scoped Gradle rerun succeeded, but Gradle emitted deprecation notes from scoped tests. That is not an architecture blocker.

## 7. Contract Ownership Review

`common-events` is now the single in-scope source of truth for shared semantic event names. The current shared event names are declared by the six event enums under `com.example.common.integration.*`. No duplicate semantic aliases remain in current `common-redis` or `common-kafka` main source.

`common-events` is also the single in-scope declaration source for shared payload mappings. `SharedEventCatalog.PAYLOAD_MAP` maps payload-bearing event types to payload classes, `PAYLOAD_BEARING_EVENT_TYPES` derives from that map, and `registerAll(...)` derives from the same map.

Payload-bearing vs payload-less modeling is explicit and currently complete. The current tests will fail if:

- a new enum value is added without catalog coverage
- an event is placed in both payload-bearing and payload-less sets
- an event is placed in neither set
- the catalog contains a value not defined by a current enum

There is no remaining second semantic owner inside the three modules. The remaining drift aperture is runtime and extension-related:

- `EventPayloadRegistry` can register custom event types.
- `RedisEventRegistry` exposes a Redis-specific adapter type for that registry.
- `SharedEventCatalog.enforcePublishPayloadContract(...)` does not use the payload-class mapping at publish time.

`SharedEventCatalog` is the canonical runtime contract source for Redis deserialization and publish nullness enforcement. It is not yet the full runtime source for canonical payload class enforcement.

## 8. Runtime Enforcement Review

Current Redis publisher enforcement:

- rejects blank/null Redis channel
- rejects null/blank or non-lower-dot event type
- rejects null payload for catalog payload-bearing events
- rejects non-null payload for catalog payload-less events
- allows unknown syntactically valid event types
- does not verify payload object class against `SharedEventCatalog`

Current Kafka publisher enforcement:

- rejects blank/null Kafka topic
- rejects null/blank or non-lower-dot event type
- rejects null payload for catalog payload-bearing events
- rejects non-null payload for catalog payload-less events
- allows unknown syntactically valid event types
- does not verify payload object class against `SharedEventCatalog`

Current Redis deserialize enforcement:

- rejects invalid JSON
- rejects missing metadata
- rejects missing eventType
- rejects unknown event type if it is not payload-less and not in the registry
- rejects missing payload for registry-backed payload-bearing events
- rejects non-null payload for payload-less events
- wraps invalid payload conversion
- wraps invalid `createdAt`
- wraps invalid metadata identity fields

Remaining soft behavior:

- Wrong payload class at publish time is not rejected.
- Unknown event names are allowed at publish time by design.
- Payload DTO constructors generally do not enforce field-level requiredness, so structurally wrong object payloads may still deserialize into DTOs with null fields depending on JSON shape.

## 9. Validation Design Review

Validation is mostly placed at the correct layers:

- `EventMetadata` enforces identity presence.
- `EventEnvelope` enforces metadata presence.
- `EventContractValidator` enforces event-name pattern and catalog membership checks.
- `SharedEventCatalog` enforces catalog payload presence/absence.
- Redis serializer enforces inbound envelope shape and payload resolution.
- Redis/Kafka publishers wrap validation failures in transport-specific exceptions with a `VALIDATE` stage.

`EventMetadata` and `EventEnvelope` constructors enforce the right structural invariants, but not all semantic invariants. They do not enforce event-name pattern or shared catalog membership. That is acceptable only if all publish/deserialize paths consistently call the validator/catalog.

`EventContractValidator` is still necessary for event-name pattern validation. In its current form, it is larger than necessary because `validateMetadata(...)` repeats identity checks already guaranteed by `EventMetadata`.

Validation authority is no longer duplicated for catalog membership: `isRegisteredEventType(...)` delegates to `SharedEventCatalog`. Validation authority is still overlapping for metadata identity checks.

Validation errors are clear enough for humans:

- publish failures are wrapped as `VALIDATE` or `PUBLISH`
- Redis deserialize messages name the failure category

They are not fully structured for machines because stages/categories are strings inside messages rather than enums or fields.

## 10. Test Completeness Review

`common-events` now self-enforces enum-to-catalog completeness. `catalog_everyEnumValueIsInExactlyOneSet` will fail if a new shared event enum value is added without being placed in exactly one catalog set.

Tests will fail if an event is both payload-bearing and payload-less because `catalog_payloadBearingAndPayloadLessSetsAreDisjoint` checks the set intersection.

Tests will fail if an event is neither payload-bearing nor payload-less because `catalog_everyEnumValueIsInExactlyOneSet` compares all enum values against the union of the two catalog sets.

Publish-time enforcement is covered for:

- missing payload on payload-bearing events in Redis
- unexpected payload on payload-less events in Redis
- missing payload on payload-bearing events in Kafka
- unexpected payload on payload-less events in Kafka

Redis deserialize enforcement is covered for:

- missing payload on payload-bearing event
- unexpected payload on payload-less event
- payload-less event with absent payload
- registry bootstrap and payload-less exclusion

Important missing test coverage:

- no test rejects wrong payload class at publish time
- no test proves every payload-bearing event rejects a non-canonical payload class
- no test proves Redis deserialization rejects wrong structured payloads that can deserialize with null DTO fields
- no test asserts the chosen policy for unknown/off-catalog event types at the publisher boundary except the current permissive catalog test

The scoped tests are aligned with the current architecture for completeness and nullness. They are not aligned with a strict payload-class contract.

## 11. Redis Review

`common-redis` is now strictly limited to Redis pub/sub concerns. It owns channels, publish, serialize/deserialize, listen, dispatch, observe, route context, exception, and auto-configuration.

The publish/listen/serialize/deserialize/dispatch flow is coherent:

- `DefaultRedisEventPublisher` validates, serializes, sends, and logs.
- `JsonRedisEventSerializer` serializes/deserializes canonical `EventEnvelope` JSON.
- `RedisEventListener` converts raw Redis messages, deserializes, logs receive, and dispatches.
- `RedisEventDispatcher` routes by `EventMetadata.eventType`.
- `RedisPubSubObserver` / `Slf4jRedisPubSubLogger` provide transport observability.

Current Redis adapters/wrappers:

- `RedisEventRegistry`
- `DefaultRedisEventRegistry`

They are not architecturally necessary inside a clean final model because `EventPayloadRegistry` already expresses the behavior. They are currently justified only as compatibility/injection surface.

`RedisChannels` contains true Redis channel routes and patterns. It does not duplicate semantic event names.

Deserialize error classes/messages are operationally useful enough for current debugging, but not fully structured. The exception object cannot carry the actual channel during pure serializer/deserializer failures, so listener logs are more useful than `RedisPubSubException.getChannel()` for inbound failures.

No removed old Redis message/subscriber API exists inside current `common-redis` source. Those old APIs remain only in downstream code.

## 12. Kafka Review

`common-kafka` is now strictly limited to Kafka transport concerns.

`KafkaTopics` now contains only true transport routes:

- aggregate friendship topics
- system dead-letter/retry topics

Semantic alias clutter is gone from current `common-kafka`. There are no current `KafkaTopics.ACCOUNT_CREATED`, `KafkaTopics.CHAT_MESSAGE_SENT`, or `KafkaTopics.NOTIFICATION_REQUESTED` constants.

Kafka notification naming is coherent with `common-events` because Kafka no longer owns semantic notification event names.

`DefaultKafkaEventPublisher` is coherent as a producer-side transport wrapper:

- validates route input
- validates event-name syntax
- enforces payload nullness via `SharedEventCatalog`
- sends the `EventEnvelope` through `KafkaTemplate`
- wraps validation vs publish failures separately

The main Kafka gap is strictness, not ownership. Kafka publish does not validate canonical payload class. Kafka also has no common inbound deserialization/validation path; services own `@KafkaListener` methods.

`KafkaEventHandler` and `KafkaEventDispatcher` are honest optional abstractions. They are internally tested and not misleading in current Javadocs, but repository scans found no downstream production use. They should not be considered proven as the stable consumer standard yet.

No current `KafkaEventProducer` compatibility interface/class exists inside `common-kafka` source. Downstream references to that style are external migration debt, not current in-module code.

## 13. Cross-Module Consistency Review

Redis and Kafka are aligned where responsibility is equivalent:

- both depend on `common-events`
- both publish `EventEnvelope<?>`
- both validate route names for their transport destination
- both validate event-name syntax
- both invoke `SharedEventCatalog.enforcePublishPayloadContract(...)`
- both wrap failures in transport-specific exceptions
- both expose routing contexts and observer contracts

Remaining differences are partly transport-specific:

- Redis owns a common deserialize/listen/dispatch path because Redis pub/sub messages arrive as raw strings.
- Kafka leaves listener/deserialization infrastructure to services.

Remaining differences that are architectural drift or strictness debt:

- Redis inbound enforcement is stronger than Kafka because Kafka has no common inbound path.
- Neither publisher enforces payload class mapping.
- Redis has a registry adapter compatibility surface; Kafka does not currently have an equivalent in-module registry surface.
- Kafka dispatcher helpers are not adopted downstream; Redis dispatcher is at least wired by downstream configs, though those configs currently reference old logger types.

Exception and observability semantics are coherent enough for human operations, but not fully standardized as machine-readable stages/categories.

## 14. Dependency Direction Review

Compile-time dependency direction is clean:

- `chatappBE/common/common-events/build.gradle` has no Redis/Kafka project dependency.
- `chatappBE/common/common-redis/build.gradle` has `api project(':common:common-events')`.
- `chatappBE/common/common-kafka/build.gradle` has `api project(':common:common-events')`.

Source dependency direction is clean:

- No Redis/Kafka transport imports were found in `common-events` current main source.
- No Kafka imports were found in `common-redis` current main source.
- No Redis imports were found in `common-kafka` current main source.

Ownership direction is mostly clean in practice. The remaining practical ownership leak is not in the three common modules; it is downstream code still using old wrapper events, old Redis message APIs, old Kafka topic aliases, and service-local shared-event registrations.

## 15. External Migration Risk Review

The external migration risk is high and confirmed by compile failures. This is outside the hard review scope, but it matters for freeze readiness as a repository standard.

Confirmed current downstream blockers include:

- `chatappBE/auth-service/src/main/java/com/example/auth/kafka/AccountCreatedEventProducer.java`
  - imports missing `com.example.common.integration.kafka.event.AccountCreatedEvent`
  - injects missing `KafkaEventProducer`
  - references missing `KafkaTopics.ACCOUNT_CREATED`

- `chatappBE/chat-service/src/main/java/com/example/chat/modules/message/infrastructure/kafka/KafkaChatMessageEventPublisher.java`
  - imports missing `ChatMessageSentEvent`, `ChatMessageEditedEvent`, `ChatMessageDeletedEvent`
  - injects missing `KafkaEventProducer`
  - references missing `KafkaTopics.CHAT_MESSAGE_SENT`, `CHAT_MESSAGE_EDITED`, `CHAT_MESSAGE_DELETED`

- `chatappBE/chat-service/src/main/java/com/example/chat/modules/message/infrastructure/kafka/KafkaReactionEventPublisher.java`
  - imports missing `ChatReactionUpdatedEvent`
  - injects missing `KafkaEventProducer`
  - references missing `KafkaTopics.CHAT_REACTION_UPDATED`

- `chatappBE/notification-service/src/main/java/com/example/notification/kafka/*`
  - multiple consumers/producers still import missing `com.example.common.integration.kafka.event.*`
  - multiple listeners reference missing semantic `KafkaTopics.*` aliases
  - `NotificationEventProducer` injects missing `KafkaEventProducer`

- `chatappBE/friendship-service/src/main/java/com/example/friendship/kafka/*`
  - imports missing `FriendRequestEvent`, `FriendRequestKafkaEvent`, and `FriendshipEvent`
  - references missing `KafkaTopics.FRIENDSHIP_EVENTS` and `KafkaTopics.FRIENDSHIP_REQUEST_EVENTS`
  - injects missing `KafkaEventProducer`

- `chatappBE/user-service/src/main/java/com/example/user/kafka/*`
  - imports missing `AccountCreatedEvent`
  - references missing `KafkaTopics.ACCOUNT_CREATED`

- `chatappBE/chat-service/src/main/java/com/example/chat/modules/message/infrastructure/redis/RedisMessageFactory.java`
  - imports missing `com.example.common.redis.message.RedisMessage`

- `chatappBE/chat-service/src/main/java/com/example/chat/realtime/subscriber/*`
  - imports missing `RedisMessage`
  - implements missing `RedisEventSubscriber`

- `chatappBE/presence-service/src/main/java/com/example/presence/redis/*`
  - imports missing `RedisMessage` and old Redis API types
  - implements missing `RedisEventSubscriber`

- `chatappBE/chat-service/src/main/java/com/example/chat/config/ChatRedisListenerConfig.java`
  - wires current `RedisEventListener` with missing old `IRedisPubSubLogger`

- `chatappBE/presence-service/src/main/java/com/example/presence/configuration/PresenceRedisListenerConfig.java`
  - wires current `RedisEventListener` with missing old `RedisPubSubLogger`

- `chatappBE/chat-service/src/main/java/com/example/chat/config/ChatRedisEventConfig.java`
  - re-registers shared Redis event types
  - specifically maps `MESSAGE_PINNED` and `MESSAGE_UNPINNED` to service-local `RoomMessagePinEventPayload`, conflicting with canonical `MessagePinPayload`

- `chatappBE/presence-service/src/main/java/com/example/presence/configuration/PresenceRedisRegistryConfig.java`
  - re-registers shared presence event types that are already owned by `SharedEventCatalog`

These risks block repository-wide freeze/adoption. They do not invalidate the current source shape inside the three scoped modules, but they mean the stable standard is not yet actually consumed by the backend.

## 16. Leftover Deprecated / Duplicate / Misplaced / Non-Canonical Code Still Present Now

Inside the three scoped modules, current leftover code/surfaces are:

- `chatappBE/common/common-events/src/main/java/com/example/common/event/SharedEventCatalog.java`
  - `enforcePublishPayloadContract(...)` is incomplete for a strict payload mapping contract because it does not validate the payload class from `PAYLOAD_MAP`.

- `chatappBE/common/common-events/src/main/java/com/example/common/event/validation/EventContractValidator.java`
  - `validateMetadata(...)` overlaps with `EventMetadata` constructor identity validation.

- `chatappBE/common/common-redis/src/main/java/com/example/common/redis/registry/RedisEventRegistry.java`
  - compatibility adapter over `EventPayloadRegistry` with no Redis-specific behavior.

- `chatappBE/common/common-redis/src/main/java/com/example/common/redis/registry/DefaultRedisEventRegistry.java`
  - compatibility adapter implementation with no behavior beyond `DefaultEventPayloadRegistry`.

- `chatappBE/common/common-kafka/src/main/java/com/example/common/kafka/consumer/KafkaEventHandler.java`
  - optional helper abstraction; internally coherent, but not proven as downstream standard.

- `chatappBE/common/common-kafka/src/main/java/com/example/common/kafka/consumer/KafkaEventDispatcher.java`
  - same status as `KafkaEventHandler`.

- `chatappBE/common/common-events/src/test/java/com/example/common/event/contract/SharedEventModelContractTest.java`
  - `catalog_enforceContract_passesForPayloadBearingEventWithPayload` currently passes `new Object()` for a payload-bearing event, which demonstrates and locks in the missing payload-class enforcement.

No current source file for the old `com.example.common.integration.kafka.event.*` wrapper events, old `com.example.common.redis.message.RedisMessage`, old `com.example.common.kafka.api.*`, or old Redis subscriber APIs exists inside the three scoped modules.

## 17. Freeze-Readiness Verdict

- Is it clean enough?
  - Structurally, mostly yes inside the three scoped modules. Ownership and dependency direction are clean, semantic duplication in Kafka is gone, Redis/Kafka boundaries are coherent, and scoped tests pass.

- Is it strict enough?
  - No. It is strict enough for payload presence/absence, but not strict enough for the full shared payload mapping contract. Publishers do not verify that payload-bearing events carry the canonical payload class.

- Is it ready to freeze?
  - Not yet as a strict stable common standard.
  - The three modules are close, but freezing now would bless an incomplete runtime contract and a downstream ecosystem that currently does not compile against the standard.

- What exact blockers still remain?
  - True in-scope freeze blocker: publish-time validation must enforce the canonical event-to-payload-class mapping from `SharedEventCatalog`, with Redis and Kafka tests proving wrong-class payloads are rejected before I/O.
  - True policy blocker if closed-world semantics are desired: decide whether unknown/off-catalog event types are allowed. If not, publishers must reject them. If yes, document and test the service-local event policy explicitly.
  - True repository-wide freeze blocker: downstream services must migrate off removed wrapper events, removed Redis message/subscriber/logger APIs, removed semantic `KafkaTopics.*` aliases, and conflicting service-local registry mappings.
  - Cleanup debt only: reduce `EventContractValidator.validateMetadata(...)` overlap, decide whether Redis registry adapters remain long-term, and either adopt or remove Kafka dispatcher helpers after downstream migration.

Minimum remaining work to freeze the scoped foundation:

1. Expose or use a catalog payload-class lookup in `SharedEventCatalog`.
2. Enforce expected payload class in both publishers during `VALIDATE`.
3. Add common-events, common-redis, and common-kafka tests for wrong-class payload rejection.
4. Make the unknown/off-catalog event policy explicit in code and tests.

Minimum remaining work to freeze repository-wide:

1. Complete the downstream migrations listed in section 15.
2. Restore all affected service compile tasks to green.
3. Remove service-local shared-event re-registration that conflicts with `SharedEventCatalog`.
