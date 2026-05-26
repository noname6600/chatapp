# Common Messaging Post-Follow-Up-Refactor Review

## 1. Scope Reviewed

Reviewed only:

- `chatappBE/common/common-events`
- `chatappBE/common/common-redis`
- `chatappBE/common/common-kafka`

Read first, in the exact requested order:

1. `review code/common-messaging-review.md`
2. `review code/common-messaging-refactor-result.md`
3. `review code/common-messaging-post-refactor-review.md`
4. `review code/common-messaging-second-refactor-result.md`
5. `review code/common-messaging-post-second-refactor-review.md`
6. `review code/common-messaging-final-cleanup-result.md`
7. `review code/common-messaging-post-final-cleanup-review.md`
8. `review code/common-messaging-next-refactor-result.md`
9. `review code/common-messaging-post-next-refactor-review.md`
10. `review code/common-messaging-followup-refactor-result.md`

Minimal out-of-scope checks were used only to confirm dependency direction, compatibility risk, and surviving references to removed aliases/classes/constants:

- `chatappBE/chat-service/src/main/java/com/example/chat/config/ChatRedisEventConfig.java`
- `chatappBE/presence-service/src/main/java/com/example/presence/configuration/PresenceRedisRegistryConfig.java`
- `chatappBE/chat-service/src/main/java/com/example/chat/config/ChatRedisListenerConfig.java`
- `chatappBE/presence-service/src/main/java/com/example/presence/configuration/PresenceRedisListenerConfig.java`
- `chatappBE/auth-service/src/main/java/com/example/auth/kafka/AccountCreatedEventProducer.java`
- `chatappBE/notification-service/src/main/java/com/example/notification/kafka/NotificationEventProducer.java`
- `chatappBE/friendship-service/src/main/java/com/example/friendship/kafka/FriendshipEventProducer.java`
- `chatappBE/friendship-service/src/main/java/com/example/friendship/kafka/FriendshipRequestEventConsumer.java`
- `chatappBE/notification-service/src/main/java/com/example/notification/kafka/FriendRequestEventConsumer.java`
- `chatappBE/chat-service/src/main/java/com/example/chat/modules/message/infrastructure/kafka/KafkaChatMessageEventPublisher.java`
- `chatappBE/chat-service/src/main/java/com/example/chat/modules/message/infrastructure/kafka/KafkaReactionEventPublisher.java`
- repository-wide `rg` scans for `KafkaEventProducer`, `KafkaEventHandler`, `RedisEventHandler`, `RedisEventSubscriber`, removed `KafkaTopics.*` aliases, removed `FriendRequestEvent`, and old Redis logger/message APIs

Verification:

- `.\gradlew.bat :common:common-events:test :common:common-redis:test :common:common-kafka:test --no-daemon --rerun-tasks`
- Result: `BUILD SUCCESSFUL`

## 2. Executive Summary

The latest follow-up pass fixed the concrete regression from the previous review: `common-kafka` now contains the deprecated compatibility producer types again, the three common modules compile, and the scoped test run passes. `common-events` is also materially cleaner than before: duplicate contract DTOs remain removed, `MessagePinPayload` is now shared, constructor-level validation exists in `EventMetadata` and `EventEnvelope`, and `KafkaTopics` is no longer a second owner of semantic event names.

The messaging foundation is still not ready to freeze as the stable common standard. The main blocker is that the canonical contract is still declarative, not enforced. `SharedEventCatalog` claims authority over payload-bearing versus payload-less events, but neither `DefaultRedisEventPublisher` nor `DefaultKafkaEventPublisher` validates an outgoing envelope against that catalog. A producer can still publish `chat.message.sent` with `null` payload or with the wrong payload type, and the common layer will allow it. Redis only rejects that on deserialize, after the bad event has already been emitted; Kafka has no equivalent common inbound guard at all.

There is also still heavy external migration debt. Outside the three reviewed modules, services continue to inject `KafkaEventProducer`, use removed `KafkaTopics.*` aliases, consume removed `FriendRequestEvent`, and in Redis still implement old `RedisEventSubscriber` / old logger / old message APIs while wiring the new listener/dispatcher path. So the internals are cleaner, but the foundation is not yet settled enough to freeze as the repository-wide standard.

## 3. What Improved Since The Previous Review

- The previous high regression is fixed: `common-kafka` again provides `com.example.common.kafka.producer.KafkaEventProducer` and `com.example.common.kafka.producer.DefaultKafkaEventProducer`, and `KafkaAutoConfiguration` now wires the compat bean again:
  - `chatappBE/common/common-kafka/src/main/java/com/example/common/kafka/producer/KafkaEventProducer.java:5-25`
  - `chatappBE/common/common-kafka/src/main/java/com/example/common/kafka/producer/DefaultKafkaEventProducer.java:6-23`
  - `chatappBE/common/common-kafka/src/main/java/com/example/common/kafka/config/KafkaAutoConfiguration.java:27-38`

- The scoped-module verification story is now honest again. Unlike the previous review state, the current working tree passes:
  - `:common:common-events:test`
  - `:common:common-redis:test`
  - `:common:common-kafka:test`

- `EventMetadata` and `EventEnvelope` are no longer validity-soft containers. Construction now fails early on missing required identity fields / missing metadata. That earlier medium issue is genuinely fixed:
  - `chatappBE/common/common-events/src/main/java/com/example/common/event/EventMetadata.java`
  - `chatappBE/common/common-events/src/main/java/com/example/common/event/EventEnvelope.java`

- `SharedEventCatalog` no longer contains the earlier transport-ownership Javadoc leak that mentioned Redis/Kafka registry overrides. The current catalog text is transport-neutral:
  - `chatappBE/common/common-events/src/main/java/com/example/common/event/SharedEventCatalog.java:32-92`

- Publisher lifecycle labeling is now improved in both transports. `VALIDATE` and `PUBLISH` are separated instead of being collapsed under one generic publish-stage wrapper:
  - `chatappBE/common/common-redis/src/main/java/com/example/common/redis/publisher/DefaultRedisEventPublisher.java:28-49`
  - `chatappBE/common/common-kafka/src/main/java/com/example/common/kafka/producer/DefaultKafkaEventPublisher.java:34-59`

- `NotificationEventType.REQUESTED` was standardized to `NOTIFICATION_REQUESTED`, which removes the earlier constant-name inconsistency inside `common-events`.

## 4. Problems Fully Fixed

- Previous problem: `common-kafka` failed its own scoped tests because compat producer types were missing.  
  Fully fixed by restoring `KafkaEventProducer` and `DefaultKafkaEventProducer` and rewiring `KafkaAutoConfiguration`.

- Previous problem: `EventMetadata` and `EventEnvelope` allowed invalid core objects by construction.  
  Fully fixed by construction-time validation in the model layer.

- Previous problem: `SharedEventCatalog` Javadoc still leaked transport-specific ownership.  
  Fully fixed in current source.

- Previous problem: publisher exceptions did not distinguish validation failures from actual send/publish failures.  
  Fully fixed in both `DefaultRedisEventPublisher` and `DefaultKafkaEventPublisher`.

- Previous problem: `NotificationEventType` constant naming was internally uneven.  
  Fully fixed by `NOTIFICATION_REQUESTED`.

- Previous problem: top-level duplicate/misplaced contract DTOs inside `common-events` (`FriendRequestEvent`, `NotificationEvent`, `UserPresencePayload`) were still present.  
  Still fixed. Those types remain absent from `common-events` main source.

- Previous problem: `KafkaTopics` duplicated semantic event-name literals.  
  Still fixed. The current `KafkaTopics` contains only transport routes:
  - `chatappBE/common/common-kafka/src/main/java/com/example/common/kafka/topic/KafkaTopics.java:23-29`

## 5. Problems Partially Fixed

- `common-events` is now the declaration authority for semantic event names and shared payload mappings, but not the enforcement authority. `SharedEventCatalog` declares the contract, while runtime publish validation still ignores payload-bearing versus payload-less rules:
  - `chatappBE/common/common-events/src/main/java/com/example/common/event/SharedEventCatalog.java:63-137`
  - `chatappBE/common/common-redis/src/main/java/com/example/common/redis/publisher/DefaultRedisEventPublisher.java:28-49`
  - `chatappBE/common/common-kafka/src/main/java/com/example/common/kafka/producer/DefaultKafkaEventPublisher.java:34-59`

- Redis-specific registry wrappers were minimized honestly, but not eliminated. They are now clearly adapters, yet they are still public API and still the auto-configured bean type:
  - `chatappBE/common/common-redis/src/main/java/com/example/common/redis/registry/RedisEventRegistry.java:6-16`
  - `chatappBE/common/common-redis/src/main/java/com/example/common/redis/registry/DefaultRedisEventRegistry.java:5-13`
  - `chatappBE/common/common-redis/src/main/java/com/example/common/redis/config/RedisAutoConfiguration.java:35-50`

- Kafka compatibility surface is now real and explicitly temporary, but only for the producer side. The consumer helper surface (`KafkaEventHandler` / `KafkaEventDispatcher`) remains optional and still has no downstream adopters confirmed by repository scan:
  - `chatappBE/common/common-kafka/src/main/java/com/example/common/kafka/consumer/KafkaEventHandler.java:6-35`
  - `chatappBE/common/common-kafka/src/main/java/com/example/common/kafka/consumer/KafkaEventDispatcher.java:12-85`
  - `chatappBE/common/common-kafka/src/main/java/com/example/common/kafka/config/KafkaAutoConfiguration.java:40-47`

- Redis missing-payload enforcement is only partially fixed. It is enforced on deserialize, not on publish:
  - fixed inbound guard: `chatappBE/common/common-redis/src/main/java/com/example/common/redis/serialization/JsonRedisEventSerializer.java:71-91`
  - still missing outbound guard: `chatappBE/common/common-redis/src/main/java/com/example/common/redis/publisher/DefaultRedisEventPublisher.java:28-49`

## 6. Remaining Problems

### High

- The canonical shared contract is still not enforced at the publish boundary. `SharedEventCatalog` claims to be the authoritative source of truth for payload-bearing and payload-less shared events (`SharedEventCatalog.java:35-37`, `63-137`), but both publishers validate only event-name syntax and identity metadata:
  - `chatappBE/common/common-redis/src/main/java/com/example/common/redis/publisher/DefaultRedisEventPublisher.java:28-49`
  - `chatappBE/common/common-kafka/src/main/java/com/example/common/kafka/producer/DefaultKafkaEventPublisher.java:34-59`
  
  Neither publisher checks that:
  - payload-bearing shared events have non-null payloads
  - payload-less shared events are actually payload-less
  - the payload object matches the canonical payload class for the event type
  
  Redis only catches missing payloads later on deserialize (`JsonRedisEventSerializer.java:71-91`), after the bad event has already been published. Kafka has no equivalent common inbound enforcement path. This means `common-events` is not yet the operational single source of truth; it is still a catalog plus conventions.

- External migration risk is still active enough that freezing the standard now would formalize a split architecture. Confirmed surviving references include:
  - `chatappBE/chat-service/src/main/java/com/example/chat/config/ChatRedisEventConfig.java:38-45` re-registers `ChatEventType.MESSAGE_PINNED` / `MESSAGE_UNPINNED` to service-local `RoomMessagePinEventPayload`, conflicting with canonical `MessagePinPayload` in `SharedEventCatalog.java:103-106`
  - `chatappBE/auth-service/src/main/java/com/example/auth/kafka/AccountCreatedEventProducer.java:17-31` still injects `KafkaEventProducer` and uses removed `KafkaTopics.ACCOUNT_CREATED`
  - `chatappBE/notification-service/src/main/java/com/example/notification/kafka/NotificationEventProducer.java:15-34` still injects `KafkaEventProducer` and uses removed `KafkaTopics.NOTIFICATION_REQUESTED`
  - `chatappBE/friendship-service/src/main/java/com/example/friendship/kafka/FriendshipEventProducer.java:25-69` still injects `KafkaEventProducer`, uses removed `KafkaTopics.FRIENDSHIP_EVENTS` / `FRIENDSHIP_REQUEST_EVENTS`, and still uses removed `FriendRequestEvent`
  - `chatappBE/friendship-service/src/main/java/com/example/friendship/kafka/FriendshipRequestEventConsumer.java:21-49` still listens to removed `KafkaTopics.FRIENDSHIP_REQUEST_EVENTS` and consumes removed `FriendRequestEvent`
  - `chatappBE/notification-service/src/main/java/com/example/notification/kafka/FriendRequestEventConsumer.java:23-101` still listens to removed `KafkaTopics.FRIENDSHIP_REQUEST_EVENTS` and consumes removed `FriendRequestEvent`
  - `chatappBE/chat-service/src/main/java/com/example/chat/modules/message/infrastructure/kafka/KafkaChatMessageEventPublisher.java:34-108` still injects `KafkaEventProducer` and uses removed `KafkaTopics.CHAT_MESSAGE_SENT`, `CHAT_MESSAGE_EDITED`, `CHAT_MESSAGE_DELETED`
  - `chatappBE/chat-service/src/main/java/com/example/chat/modules/message/infrastructure/kafka/KafkaReactionEventPublisher.java:16-35` still injects `KafkaEventProducer` and uses removed `KafkaTopics.CHAT_REACTION_UPDATED`

- Redis consumer migration is also not actually settled outside scope. The new `RedisEventListener` / `RedisEventDispatcher` path is being wired, but downstream consumers still implement old subscriber/message/logger APIs:
  - `chatappBE/chat-service/src/main/java/com/example/chat/config/ChatRedisListenerConfig.java:19-21,38-40` wires `RedisEventListener` with old `IRedisPubSubLogger`
  - `chatappBE/presence-service/src/main/java/com/example/presence/configuration/PresenceRedisListenerConfig.java:23-30` wires `RedisEventListener` with old `RedisPubSubLogger`
  - `chatappBE/chat-service/src/main/java/com/example/chat/realtime/subscriber/ChatMessageSentRedisSubscriber.java:16-29` still implements old `RedisEventSubscriber<RedisMessage<...>>`
  - `chatappBE/presence-service/src/main/java/com/example/presence/redis/UserOnlineSubscriber.java:14-25` still implements old `RedisEventSubscriber<RedisMessage<...>>`
  
  Repository scan found no downstream `implements RedisEventHandler` usages. So the common Redis handler/dispatcher path is not yet the real repository standard either.

### Medium

- `common-events` still has split authority instead of one self-checking contract surface. `SharedEventCatalog` manually maintains payload-bearing registrations and a separate payload-less set (`SharedEventCatalog.java:63-137`), while `EventContractValidator.isRegisteredEventType(...)` independently scans enums (`EventContractValidator.java:150-160`). There is no common-events-layer test that exhaustively asserts every enum constant is represented exactly once as either payload-bearing or payload-less. Drift would still be possible without an immediate failure in `common-events` itself.

- `PresenceEventType` still contains leftover compatibility behavior in the supposedly final canonical contract layer. `fromValue(...)` lowercases inbound values before matching:
  - `chatappBE/common/common-events/src/main/java/com/example/common/integration/presence/PresenceEventType.java:33-44`
  
  That means non-canonical case variants are still accepted here even though `EventContractValidator` rejects them as non-canonical event names.

- `KafkaEventHandler` / `KafkaEventDispatcher` are still not proven as the real consumer abstraction. The code is internally coherent, but repository scan found no out-of-module implementations or injections of that path. So `common-kafka` still ships a consumer abstraction that currently looks more aspirational than canonical:
  - `chatappBE/common/common-kafka/src/main/java/com/example/common/kafka/consumer/KafkaEventHandler.java:6-35`
  - `chatappBE/common/common-kafka/src/main/java/com/example/common/kafka/consumer/KafkaEventDispatcher.java:12-85`
  - `chatappBE/common/common-kafka/src/main/java/com/example/common/kafka/config/KafkaAutoConfiguration.java:40-47`

- Redis registry wrappers remain non-canonical duplicate surface even though they are now explicitly documented as adapters. `RedisAutoConfiguration` still exposes the adapter type, not the transport-neutral `EventPayloadRegistry`, which keeps the duplicate transport-specific contract alive:
  - `chatappBE/common/common-redis/src/main/java/com/example/common/redis/registry/RedisEventRegistry.java:6-16`
  - `chatappBE/common/common-redis/src/main/java/com/example/common/redis/config/RedisAutoConfiguration.java:35-50`

- `DefaultEventPayloadRegistry` is now safely conflict-detecting, but its idempotent same-class re-registration behavior still allows duplicate ownership claims to remain invisible during migration. That is why `PresenceRedisRegistryConfig` can keep re-registering shared types without any signal. This is not a runtime bug inside the three modules, but it does allow fake ownership cleanup to survive outside scope.

### Low

- `SharedEventModelContractTest` has a new alignment problem: `validator_validateMetadata_rejectsBlankEventId` is now misnamed and asserts the opposite of what its name says:
  - `chatappBE/common/common-events/src/test/java/com/example/common/event/contract/SharedEventModelContractTest.java:153-160`
  
  That is a small but real post-follow-up regression in test clarity.

- `JsonRedisEventSerializer` still throws `RedisPubSubException` with channel `"unknown"` for serialize/deserialize failures:
  - `chatappBE/common/common-redis/src/main/java/com/example/common/redis/serialization/JsonRedisEventSerializer.java:36-40,51-114`
  
  The listener logs the real channel separately, so operations are not blind anymore, but `RedisPubSubException.getChannel()` is still not useful on the most failure-prone path.

## 7. Common-Events Review

- `common-events` is now the only in-scope owner of shared semantic event-name literals. That part is genuinely cleaned up. `KafkaTopics` no longer duplicates those names.

- The current canonical mapping itself is correct by inspection. Every event in:
  - `AccountEventType`
  - `ChatEventType`
  - `FriendshipEventType`
  - `NotificationEventType`
  - `PresenceEventType`
  - `UserEventType`
  
  is currently represented either in `SharedEventCatalog.registerAll(...)` or in `PAYLOAD_LESS_EVENT_TYPES`:
  - `chatappBE/common/common-events/src/main/java/com/example/common/event/SharedEventCatalog.java:63-137`

- Payload-bearing versus payload-less events are explicitly modeled and the current catalog entries are internally consistent. `MESSAGE_PINNED` / `MESSAGE_UNPINNED` now correctly map to shared `MessagePinPayload`, which was one of the important earlier contract gaps.

- Deprecated / duplicate / misplaced top-level contract DTOs that earlier reviews flagged are gone from `common-events` main source. That cleanup is real.

- Transport leakage has been removed from ownership, but the layer is still not minimal enough to freeze. The biggest issue is not transport naming anymore; it is that the contract catalog is not the same thing as contract enforcement.

- Docs/Javadocs/tests are only mostly aligned. The catalog Javadoc is now fine, but common-events tests still do not directly prove catalog completeness, and `SharedEventModelContractTest` contains the misnamed test mentioned above.

- Strict verdict on `common-events`: close to canonical, but not final. It now owns the contract definitions, not the full runtime contract guarantee.

## 8. Redis Review

- `common-redis` is now properly scoped to Redis pub/sub concerns. It owns channels, serialization, listener/dispatcher flow, observability, route context, exception mapping, and auto-configuration. It no longer owns semantic event names.

- Redis integrates with the shared contract correctly at bootstrap and on deserialize:
  - `RedisAutoConfiguration.redisEventRegistry()` preloads `SharedEventCatalog`
  - `JsonRedisEventSerializer` uses `EventPayloadRegistry`
  - payload-less shared events bypass payload lookup
  - unknown event types, invalid payloads, bad timestamps, and invalid metadata are separated clearly

- The earlier listener bug is fixed. `RedisEventListener` now separates deserialize failures from dispatch failures, so handler errors are no longer misreported as deserialization errors:
  - `chatappBE/common/common-redis/src/main/java/com/example/common/redis/listener/RedisEventListener.java`

- Redis is still not minimal enough to freeze for two reasons:
  - outbound publish does not validate against the shared catalog
  - the adapter registry surface still exists as a first-class API because downstream services still depend on it

- Strict Redis verdict: the in-module design is coherent and much cleaner, but the canonical contract is still enforced too late and the external migration story is not finished.

## 9. Kafka Review

- `common-kafka` is much cleaner than it was in the previous review. `KafkaTopics` now contains only real transport routes:
  - `chatappBE/common/common-kafka/src/main/java/com/example/common/kafka/topic/KafkaTopics.java:23-29`

- Semantic alias clutter is removed from Kafka main source. Notification naming is now consistent with `common-events`, because Kafka no longer carries its own semantic notification aliases.

- The producer compatibility story is now real and clearly temporary. The deprecated compat interface/class are present, marked `forRemoval`, and wired by the auto-configuration:
  - `KafkaEventProducer.java:5-25`
  - `DefaultKafkaEventProducer.java:6-23`
  - `KafkaAutoConfiguration.java:27-38`

- The remaining Kafka problem is on the consumer/helper side. `KafkaEventHandler` / `KafkaEventDispatcher` are internally coherent, but repository scan found no downstream adoption outside common-kafka tests. So this abstraction is still not clearly part of the actual stable standard.

- Kafka also has the same contract-enforcement gap as Redis publish. It validates metadata only, not shared-catalog payload rules:
  - `chatappBE/common/common-kafka/src/main/java/com/example/common/kafka/producer/DefaultKafkaEventPublisher.java:34-59`

- Strict Kafka verdict: the module is now internally consistent and transport-scoped, but it still is not the final frozen shape because its consumer abstraction is unproven and its producer path does not enforce the canonical payload contract.

## 10. Cross-Module Consistency Review

- Ownership is substantially better aligned than in earlier reviews:
  - `common-events` owns shared semantics
  - `common-redis` owns Redis transport
  - `common-kafka` owns Kafka transport

- Naming consistency is improved where responsibility matches:
  - both transports use `handle(...)` on their handler contracts
  - both publishers reject blank route inputs
  - both publishers now split `VALIDATE` and `PUBLISH`
  - both transports expose routing-context records

- The remaining important differences are not all truly transport-specific:
  - Redis enforces payload-bearing rules only on inbound deserialize
  - Kafka has no common inbound enforcement path
  - neither outbound path enforces the shared catalog
  - Kafka consumer abstraction is unused outside tests
  - Redis consumer abstraction is still being migrated to outside scope

- So the cleanup is not fake inside the three modules, but it is still incomplete at the standardization level. Ownership moved in the right direction, yet runtime enforcement and downstream adoption still lag behind.

## 11. Dependency Direction Review

- Build-level dependency direction is correct:
  - `chatappBE/common/common-redis/build.gradle` -> `api project(':common:common-events')`
  - `chatappBE/common/common-kafka/build.gradle` -> `api project(':common:common-events')`
  - `chatappBE/common/common-events/build.gradle` has no dependency on Redis or Kafka modules

- Compile-time direction inside the three modules is clean. `common-events` does not import Kafka/Redis transport code.

- The remaining dependency problem is operational, not compile-time:
  - downstream services still depend on deprecated producer aliases
  - downstream Kafka code still depends on removed `KafkaTopics.*` aliases and old typed wrapper events
  - downstream Redis code still depends on old subscriber/message/logger contracts

- Verdict on dependency direction: the module graph is clean enough, but the repository migration around that graph is still incomplete.

## 12. Leftover Deprecated / Duplicate / Misplaced / Non-Canonical Code

- No deprecated top-level contract DTO aliases remain inside `common-events` main source. That part is genuinely clean.

- Leftover non-canonical or not-yet-final code inside the three reviewed modules:
  - `chatappBE/common/common-events/src/main/java/com/example/common/event/SharedEventCatalog.java:63-137`
  - Reason: canonical mapping exists here, but enforcement still lives elsewhere or nowhere; the catalog is still advisory at publish time

  - `chatappBE/common/common-events/src/main/java/com/example/common/event/validation/EventContractValidator.java:150-160`
  - Reason: separate enum-scan authority duplicates part of the contract model instead of deriving from one catalog surface

  - `chatappBE/common/common-events/src/main/java/com/example/common/integration/presence/PresenceEventType.java:33-44`
  - Reason: leftover compatibility parsing still accepts non-canonical case variants

  - `chatappBE/common/common-redis/src/main/java/com/example/common/redis/registry/RedisEventRegistry.java`
  - `chatappBE/common/common-redis/src/main/java/com/example/common/redis/registry/DefaultRedisEventRegistry.java`
  - Reason: transport-specific duplicate adapter surface still exists

  - `chatappBE/common/common-kafka/src/main/java/com/example/common/kafka/consumer/KafkaEventHandler.java`
  - `chatappBE/common/common-kafka/src/main/java/com/example/common/kafka/consumer/KafkaEventDispatcher.java`
  - Reason: still optional and unadopted outside common-kafka tests; not yet clearly part of the real stable standard

- Leftover non-canonical external compatibility debt outside scope, but still important to note:
  - removed Kafka topic aliases still referenced by services
  - removed `FriendRequestEvent` still referenced by services
  - old `com.example.common.integration.kafka.event.*` wrapper-event style still referenced by services
  - old Redis subscriber/message/logger API still referenced by services
  - `chat-service` still conflicts with canonical pin/unpin payload mapping

## 13. Final Verdict

- Is it now clean enough?  
  No. It is clearly cleaner than the previous review state, and the compile/test regression is fixed, but the foundation is still not canonical enough to freeze because the shared catalog is not enforced on publish and the downstream migration is still materially incomplete.

- Is it ready to become the stable standard?  
  No. `common-events` is now the declaration owner, but not yet the full runtime authority. `common-redis` and `common-kafka` are cleaner transport modules, but both still allow contract-invalid outbound envelopes, Kafka’s consumer helper path is not proven, and Redis’s new consumer path is still competing with old external APIs.

- What follow-up work, if any, remains later outside scope?  
  Later outside scope, services still need to migrate off:
  - `KafkaEventProducer`
  - removed `KafkaTopics.*` aliases
  - old `com.example.common.integration.kafka.event.*` wrapper events
  - removed `FriendRequestEvent`
  - old Redis `RedisEventSubscriber` / logger / message APIs
  
  `chat-service` also still needs to stop re-registering `MESSAGE_PINNED` / `MESSAGE_UNPINNED` to `RoomMessagePinEventPayload` and align with shared `MessagePinPayload`.

  Inside the three reviewed modules, the remaining freeze blocker is one level deeper: enforce `SharedEventCatalog` on outbound publish so the common standard is not just documented, but actually guaranteed.
