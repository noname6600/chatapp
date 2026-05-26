# Common Messaging Current-Source Review

## 1. Scope Reviewed

- Reviewed current source only under:
  - `chatappBE/common/common-events`
  - `chatappBE/common/common-redis`
  - `chatappBE/common/common-kafka`
- Used the listed historical review/result markdown files as context only, not as proof of current state.
- Performed limited out-of-scope reads/searches only to confirm dependency direction and external migration risk.
- Verified current scoped tests with:
  - `.\gradlew.bat :common:common-events:test :common:common-redis:test :common:common-kafka:test --no-daemon --rerun-tasks`
  - Result: `BUILD SUCCESSFUL`

## 2. Executive Summary

`common-events` is now the in-scope semantic authority for shared event names and shared payload mappings. The current catalog is complete for the current enums, semantic name duplication inside `common-redis` and `common-kafka` main source is gone, and the three reviewed modules are much cleaner than in the historical review trail.

The foundation is still not strict enough to freeze. The biggest remaining issue is contract enforcement depth: `SharedEventCatalog` defines payload-bearing vs payload-less rules, but `DefaultRedisEventPublisher` and `DefaultKafkaEventPublisher` do not enforce those rules before publish, and `JsonRedisEventSerializer` still silently accepts payload-less event types that arrive with an unexpected payload. Redis also still exposes compatibility registry adapters, and Kafka still ships a consumer helper abstraction that has no current downstream production adoption.

## 3. What Improved Since The Historical Reviews

- The scoped module test story is now real again. The three reviewed modules currently build and test successfully.
- `common-events` now owns the in-scope shared semantic event-name literals; `common-kafka` no longer mirrors semantic names through `KafkaTopics`.
- `SharedEventCatalog` is transport-neutral again. The current Javadocs no longer leak Redis/Kafka ownership into the contract layer.
- `EventMetadata` and `EventEnvelope` now reject invalid core construction instead of allowing validity-soft event model objects.
- `NotificationEventType` naming is now internally coherent: `NOTIFICATION_REQUESTED`, `NOTIFICATION_CREATED`, and `NOTIFICATION_SENT`.
- `KafkaTopics` is now reduced to actual transport route constants only.
- Redis deserialization now correctly rejects missing payloads for payload-bearing shared events.
- Kafka dispatcher observability is now wired and the current module tests no longer disagree with current main source.

## 4. Problems Fully Fixed

- The previous scoped `common-kafka` test/main-source mismatch is fixed. Current `common-kafka` tests pass.
- The earlier transport leakage in `common-events` ownership/Javadocs is fixed. Current `SharedEventCatalog` is transport-neutral.
- The earlier semantic alias clutter in `common-kafka` is fixed. Current `KafkaTopics` contains only true transport route constants.
- The historically flagged top-level duplicate/misplaced shared contract DTO aliases are not present in current `common-events` main source.
- The shared catalog now correctly classifies current payload-bearing vs payload-less event types, including the current `PresenceEventType.USER_STATUS_CHANGED` and the shared `MessagePinPayload` mapping for `MESSAGE_PINNED` / `MESSAGE_UNPINNED`.
- Publisher lifecycle staging is clearer now: both transports distinguish `VALIDATE` failure from `PUBLISH` failure.
- Redis deserialization now rejects payload-bearing events that arrive without a payload.

## 5. Problems Partially Fixed

- `common-events` is now the declaration authority for semantic names and shared payload mappings, but not the single self-enforcing authority. Contract knowledge is still split across domain enums, `SharedEventCatalog`, `PAYLOAD_LESS_EVENT_TYPES`, and parts of `EventContractValidator`, and there is still no completeness test inside `common-events` that proves future enum additions cannot drift.
- Redis adapter cleanup is honest but incomplete. `RedisEventRegistry` and `DefaultRedisEventRegistry` now clearly describe themselves as compatibility adapters, but they still remain public API and the auto-configured bean type.
- Kafka cleanup is real but still incomplete at the standardization level. The main source is cleaner, but Kafka still does not enforce the shared payload contract at publish time, and its consumer helper abstraction is still unproven outside module tests.
- Downstream ownership migration is only partially fixed outside scope. The reviewed modules are cleaner, but several services still depend on removed Kafka alias constants, removed Redis compatibility APIs, or service-local Redis re-registration.

## 6. Remaining Problems
### High

- The canonical payload contract is still not enforced at the publish boundary in either transport. `DefaultRedisEventPublisher` and `DefaultKafkaEventPublisher` validate event-name syntax only, then serialize/send. That means payload-bearing shared events can still be emitted with `null` payload, and Kafka never consults `SharedEventCatalog` at all before publish.
- `JsonRedisEventSerializer` still does not reject unexpected payloads for payload-less event types. For any event type in `SharedEventCatalog.PAYLOAD_LESS_EVENT_TYPES`, it skips payload resolution and returns `payload = null` even if a `payload` field is present, which silently normalizes off-contract input instead of failing fast.
- External migration risk remains high outside scope. Current examples include:
  - `chatappBE/chat-service/src/main/java/com/example/chat/modules/message/infrastructure/kafka/KafkaChatMessageEventPublisher.java`
  - `chatappBE/chat-service/src/main/java/com/example/chat/modules/message/infrastructure/kafka/KafkaReactionEventPublisher.java`
  - `chatappBE/notification-service/src/main/java/com/example/notification/kafka/NotificationEventProducer.java`
  - `chatappBE/auth-service/src/main/java/com/example/auth/kafka/AccountCreatedEventProducer.java`
  - `chatappBE/chat-service/src/main/java/com/example/chat/config/ChatRedisEventConfig.java`
  - `chatappBE/presence-service/src/main/java/com/example/presence/configuration/PresenceRedisRegistryConfig.java`
  - `chatappBE/chat-service/src/main/java/com/example/chat/realtime/subscriber/*`
  - `chatappBE/presence-service/src/main/java/com/example/presence/redis/*`
  These files still show unresolved use of removed common messaging compatibility surfaces or service-local ownership that conflicts with the shared contract direction.

### Medium

- `EventContractValidator` still carries overlapping authority inside `common-events`. `validateIdentityOrThrow(...)` duplicates constructor-level identity checks already enforced by `EventMetadata`, and `isRegisteredEventType(...)` manually scans enums instead of deriving from `SharedEventCatalog`.
- `RedisEventRegistry` and `DefaultRedisEventRegistry` still add no Redis-specific behavior. They remain as compatibility surface only, and `RedisAutoConfiguration` still exposes the adapter type instead of the transport-neutral `EventPayloadRegistry`.
- `KafkaEventHandler` and `KafkaEventDispatcher` are current-source abstractions, but the current repository scan found no downstream production implementations or injections outside `common-kafka` tests. They are coherent code, but not yet proven as the real stable consumer standard.
- `PresenceEventType.fromValue(...)` still lowercases inbound values before matching, which leaves lenient compatibility behavior in the canonical event enum layer.
- The current `common-events` test suite still does not directly enforce enum-to-catalog completeness. I manually verified current completeness in this review; the module does not currently self-enforce it.

### Low

- `SharedEventModelContractTest` still contains a misnamed test method: `validator_validateMetadata_rejectsBlankEventId` asserts success, not rejection.
- `PresenceHeartbeatPayload` Javadoc still carries a historical “previously misplaced” note, which does not belong in a final canonical shared contract layer.
- `JsonRedisEventSerializer` still throws `RedisPubSubException` with channel `"unknown"` for serializer-side failures, so the exception object is less actionable than the surrounding logs.

## 7. Common-Events Review

- `common-events` is now the only in-scope owner of shared semantic event-name literals. No duplicate semantic owner remains in current `common-redis` or `common-kafka` main source.
- The current canonical event-to-payload mapping is correct and complete. I manually verified the current event enums against `SharedEventCatalog`, and all 34 current enum values are represented exactly once as either payload-bearing or payload-less.
- Payload-bearing vs payload-less modeling is currently correct by declaration:
  - payload-bearing shared events are registered in `SharedEventCatalog.registerAll(...)`
  - payload-less shared events are explicitly listed in `SharedEventCatalog.PAYLOAD_LESS_EVENT_TYPES`
- No currently existing top-level deprecated/duplicate shared event DTO alias files were found in `common-events` main source.
- The remaining top-level chat DTOs that are not direct catalog entries, such as `AttachmentPayload`, `MessageBlockPayload`, and `RoomInvitePayload`, are currently used as nested component types inside `ChatMessagePayload`. They are not duplicate event contracts.
- Transport leakage has been removed from current code ownership and current main Javadocs.
- The remaining weakness is not ownership but enforcement. `common-events` defines the contract, but it still does not fully guarantee that the rest of the stack will honor payload-bearing vs payload-less rules at runtime.
- Docs/tests are mostly aligned, but still not fully final:
  - constructor-level validity is now correct
  - `SharedEventModelContractTest` is partly misnamed
  - there is still no direct completeness test for the catalog

## 8. Redis Review

- `common-redis` is now strictly about Redis pub/sub concerns: channels, publisher, serializer, listener, dispatcher, observability, routing context, exception mapping, and auto-configuration.
- Redis uses the shared contract correctly where it actually integrates with it:
  - `RedisAutoConfiguration` pre-populates the registry from `SharedEventCatalog`
  - `JsonRedisEventSerializer` depends on the transport-neutral `EventPayloadRegistry`
- The deserialize failure taxonomy is operationally clear enough. Current failure paths distinguish invalid JSON, missing metadata, missing `eventType`, unknown event type, missing payload, invalid payload, invalid timestamp, and invalid metadata identity.
- The current publish/listen/serialize/dispatch flow is coherent and minimal:
  - publish: `DefaultRedisEventPublisher`
  - serialize/deserialize: `JsonRedisEventSerializer`
  - inbound listener: `RedisEventListener`
  - dispatch: `RedisEventDispatcher`
  - observability: `RedisPubSubObserver`
- Redis now correctly rejects payload-bearing shared events with missing/null payload during deserialize.
- Remaining Redis problems:
  - publish still validates event-name syntax only; it does not enforce shared payload rules before sending
  - payload-less events with unexpected payload are still silently normalized on deserialize
  - `RedisEventRegistry` / `DefaultRedisEventRegistry` are still compatibility adapters rather than real Redis-specific behavior
- Remaining Redis compatibility surface is only temporarily justified. It still exists because downstream services currently inject the Redis-specific adapter type.

## 9. Kafka Review

- `common-kafka` is now much closer to a pure transport module than in the historical review trail.
- `KafkaTopics` now contains only true transport route constants in current source:
  - aggregate friendship routes
  - system dead-letter / retry routes
- The semantic alias clutter is gone from current `common-kafka` main source.
- Notification naming is now coherent with `common-events`; Kafka no longer carries a stray semantic naming layer of its own.
- Current Kafka observer/dispatcher code is internally coherent and current tests pass.
- The remaining Kafka problems are:
  - publish still validates event-name syntax only and never enforces the canonical shared payload contract
  - `common-kafka` does not currently integrate with `SharedEventCatalog` in its main publish path
  - `KafkaEventHandler` / `KafkaEventDispatcher` remain shipped abstractions without confirmed downstream production adoption
- There is no remaining in-module producer compatibility alias layer in current source. That part of the historical review trail no longer applies to the current tree.
- The real Kafka freeze blocker is now external migration, not semantic duplication inside current `common-kafka`.

## 10. Cross-Module Consistency Review

- Ownership is much more aligned now:
  - `common-events` owns shared semantics
  - `common-redis` owns Redis transport concerns
  - `common-kafka` owns Kafka transport concerns
- Naming is mostly consistent where responsibilities match:
  - both transports have routing-context types
  - both transports have observer interfaces plus SLF4J implementations
  - both publishers guard blank route input and wrap failures in transport-specific exceptions
- Validation patterns are only partly consistent:
  - both publishers validate event-name syntax
  - Redis has catalog-backed deserialize validation
  - Kafka has no equivalent catalog-backed enforcement path in current main source
- Observability patterns are coherent enough overall, but Kafka’s consumer-side helper surface is still optional and Redis has a fuller inbound path than Kafka.
- Remaining differences are not all transport-specific. Some are architectural drift:
  - Redis has a canonical deserialize/dispatch path that knows about shared payload types
  - Kafka currently does not
- Fake cleanup was mostly avoided inside the three reviewed modules. The current code is materially cleaner and the scoped tests pass. The remaining gaps are real, not cosmetic, and they are now concentrated around enforcement depth and migration completion.

## 11. Dependency Direction Review

- Build-level dependency direction is correct:
  - `common-events` has no dependency on Redis or Kafka modules
  - `common-redis` depends on `common-events`
  - `common-kafka` depends on `common-events`
- Compile-time direction inside the reviewed modules is clean. I did not find reverse transport imports inside `common-events`.
- The remaining dependency problem is operational, not structural. The module graph is now clean, but downstream services still depend on removed Kafka alias constants, removed Redis compatibility APIs, and Redis-specific adapter injection patterns that belong to migration cleanup outside these three modules.

## 12. Leftover Deprecated / Duplicate / Misplaced / Non-Canonical Code Still Present Now

- `chatappBE/common/common-events/src/main/java/com/example/common/event/validation/EventContractValidator.java`
  - `validateIdentityOrThrow(...)` is redundant with current `EventMetadata` constructor validation.
  - `isRegisteredEventType(...)` is a second manual authority for event registration knowledge, separate from `SharedEventCatalog`.
- `chatappBE/common/common-redis/src/main/java/com/example/common/redis/registry/RedisEventRegistry.java`
  - Current compatibility adapter with no Redis-specific behavior.
- `chatappBE/common/common-redis/src/main/java/com/example/common/redis/registry/DefaultRedisEventRegistry.java`
  - Current compatibility adapter implementation with no added behavior beyond `DefaultEventPayloadRegistry`.
- `chatappBE/common/common-kafka/src/main/java/com/example/common/kafka/consumer/KafkaEventHandler.java`
  - Still present as an optional helper abstraction, but not currently proven by downstream production usage.
- `chatappBE/common/common-kafka/src/main/java/com/example/common/kafka/consumer/KafkaEventDispatcher.java`
  - Same status as `KafkaEventHandler`: coherent current code, but still not obviously the canonical repository-wide consumer standard.
- `chatappBE/common/common-events/src/main/java/com/example/common/integration/presence/PresenceEventType.java`
  - `fromValue(...)` still contains lenient lowercasing behavior in the canonical event enum.
- `chatappBE/common/common-events/src/test/java/com/example/common/event/contract/SharedEventModelContractTest.java`
  - Still contains the misnamed `validator_validateMetadata_rejectsBlankEventId` test.

## 13. Final Verdict
- Is it now clean enough? Not yet. The three modules are much cleaner and currently test-green, but they still leave too much of the shared payload contract unenforced at runtime.
- Is it ready to become the stable standard? Not fully. `common-events` is now the semantic authority in practice, but Redis and Kafka still do not enforce that authority consistently enough at the publish boundary, and Kafka’s optional consumer helper surface is still unproven.
- What follow-up work, if any, remains later outside scope? Finish downstream migration off removed Kafka alias constants and removed Redis compatibility APIs, remove service-local Redis re-registration where the shared catalog already owns the mapping, and if the goal is a frozen standard add a `common-events` completeness test plus transport-side enforcement for both invalid cases: payload-bearing events with missing payload, and payload-less events with unexpected payload.
