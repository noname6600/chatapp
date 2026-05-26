# Common Modules Review

## 1. Executive Summary

The `chatappBE/common` area currently contains these shared modules:

- `common-core`: generic exception contract and a generic pipeline executor.
- `common-web`: REST response wrappers, global exception handling, trace-id filter, CORS properties, and realtime flow classification policy.
- `common-security`: a small JWT helper.
- `common-events`: cross-service integration payloads and event-type enums for account, chat, friendship, notification, presence, user, and realtime contract validation.
- `common-kafka`: Kafka publisher abstractions, auto-configuration, Kafka topics, and Kafka event envelope classes.
- `common-redis`: Redis pub/sub message abstractions, serializer, dispatcher, publisher, listener, registry, logging, and auto-configuration.
- `common-redis-cache`: Redis cache manager/cache extensions with TTL operations.
- `common-websocket`: WebSocket session/broadcaster interfaces, JWT handshake support, outbound WebSocket message DTOs.
- `common-feign`: Feign trace propagation configuration.
- `common-media`: currently has build metadata and empty source directories, but no active Java source.

The split is directionally reasonable: core, web, security, events, Kafka, Redis, WebSocket, Feign, and cache are recognizable concerns. The current boundaries are not clean enough for a scalable microservice architecture yet. The biggest issues are misplaced realtime policy in `common-web`, duplicated/overlapping event-envelope DTOs between `common-events`, `common-kafka`, `common-redis`, and `common-websocket`, and package/module mismatches in `common-kafka`.

Dependency direction is mostly acyclic at the Gradle level. `common-core` is still the lowest module by dependency graph, but it is not perfectly generic because `IErrorCode` exposes `httpStatus()`, making the core exception contract aware of HTTP semantics. Transport modules mostly depend sideways or downward, but `common-redis-cache` and `common-websocket` declare `common-events` dependencies they do not appear to use. `common-redis` and `common-kafka` depend on `common-events` for `RealtimeContractValidator`, which is reasonable only if `common-events` is treated as a transport-neutral contract module.

Overall verdict: the structure is messy but recoverable. The design can scale if event contracts are centralized, transport-specific wrappers stay in transport modules, realtime policy moves out of `common-web`, and package names are aligned with module ownership.

## 2. Module-by-Module Review

### `common-core`

Responsibility: lowest-level shared Java primitives with no transport, Spring Web, Kafka, Redis, or domain-specific assumptions.

Currently contains:

- `com.example.common.core.exception.BusinessException`
- `com.example.common.core.exception.IErrorCode`
- `com.example.common.core.exception.CommonErrorCode`
- `com.example.common.core.pipeline.PipelineStep`
- `com.example.common.core.pipeline.StepCondition`
- `com.example.common.core.pipeline.StepRetryPolicy`
- `com.example.common.core.pipeline.PipelineStepDescriptor`
- `com.example.common.core.pipeline.PipelineGraphResolver`
- `com.example.common.core.pipeline.PipelineFactory`
- `com.example.common.core.pipeline.PipelineExecutor`

What is correct:

- No dependency on other common modules.
- Pipeline classes are generic and do not know service/domain names.
- `BusinessException` is generic enough to be reused by services.

What is misplaced:

- `IErrorCode.httpStatus()` and `CommonErrorCode` HTTP status values put HTTP response semantics into the lowest layer. This is convenient, but it means `common-core` is not fully transport-neutral.

What should be added:

- Tests for `PipelineGraphResolver`, `PipelineExecutor`, retry, async, timeout, and cycle behavior.
- A transport-neutral error severity/category contract if services also need non-HTTP transports.

What should be removed:

- Consider removing `httpStatus()` from `IErrorCode` and moving HTTP mapping into `common-web`, or rename the contract to make the HTTP coupling explicit.

Name accuracy: mostly accurate, but `common-core` should be stricter about staying transport-neutral.

Package consistency: good. `com.example.common.core.exception` and `com.example.common.core.pipeline` match the module.

### `common-web`

Responsibility: HTTP/REST shared concerns.

Currently contains:

- `com.example.common.web.response.ApiResponse`
- `com.example.common.web.response.ApiError`
- `com.example.common.web.exception.GlobalExceptionHandler`
- `com.example.common.web.filter.TraceIdFilter`
- `com.example.common.web.cors.CorsProperties`
- `com.example.common.web.controller.BaseController`
- `com.example.common.realtime.policy.RealtimeFlowId`
- `com.example.common.realtime.policy.RealtimeFlowType`
- `com.example.common.realtime.policy.RealtimeFlowClassificationPolicy`

What is correct:

- `ApiResponse`, `ApiError`, `BaseController`, `TraceIdFilter`, `CorsProperties`, and `GlobalExceptionHandler` are HTTP/web concerns.
- Dependency on `common-core` is correct because web maps core exceptions into HTTP responses.
- Moving `FeignTraceConfig` out of this module into `common-feign` was a good boundary improvement.

What is misplaced:

- `RealtimeFlowId`, `RealtimeFlowType`, and `RealtimeFlowClassificationPolicy` do not belong in `common-web`. They are cross-transport realtime delivery policy used by Kafka, Redis, WebSocket, and service orchestration.
- `GlobalExceptionHandler` directly handles `org.springframework.security.access.AccessDeniedException` and `org.springframework.security.core.AuthenticationException`. That can be acceptable in web, but it creates an implicit dependency on security behavior from a module named only `common-web`.

What should be added:

- If keeping security exception mapping here, document that `common-web` includes REST security exception translation.
- Spring Boot auto-configuration metadata for `TraceIdFilter`, `CorsProperties`, and `GlobalExceptionHandler` if services should opt in cleanly instead of relying on component scanning.

What should be removed:

- Move `com.example.common.realtime.policy.*` to a transport-neutral module such as `common-events`, `common-realtime`, or `common-contracts`.

Name accuracy: accurate for web classes, inaccurate for realtime policy.

Package consistency: mixed. `com.example.common.web.*` is consistent; `com.example.common.realtime.policy` inside `common-web` is not.

### `common-security`

Responsibility: reusable security helpers and, if expanded, security auto-configuration.

Currently contains:

- `com.example.common.security.jwt.JwtHelper`

What is correct:

- `JwtHelper.extractUserId(Jwt)` is small, focused, and reusable.
- Dependency on `common-core` is declared but not used by current source; this should be removed unless future security errors use it.

What is misplaced:

- No misplaced active class found in source.
- Related JWT handshake implementation is in `common-websocket`, which is correct because it is WebSocket-specific, but shared JWT subject parsing could reuse `JwtHelper` to avoid duplicate UUID parsing behavior.

What should be added:

- Optional shared JWT claim constants if all services use the same subject/claim conventions.
- Tests for malformed subject, null JWT, and valid UUID subject.

What should be removed:

- Remove the unused `implementation project(':common:common-core')` dependency from `common-security/build.gradle` unless intentionally reserved.

Name accuracy: accurate.

Package consistency: good.

### `common-events`

Responsibility: transport-neutral integration contracts shared across services.

Currently contains:

- Account contracts: `AccountCreatedPayload`, `AccountEventType`
- Chat contracts: `ChatMessagePayload`, `AttachmentPayload`, `MessageBlockPayload`, `MessageDeletedPayload`, `MessageUpdatedPayload`, `ReactionPayload`, `RoomInvitePayload`, `ChatEventType`
- Cross-chat enums: `AttachmentType`, `MessageType`, `ReactionAction`
- Friendship contracts: `FriendRequestEvent`, `FriendshipPayload`, `FriendshipEventType`
- Notification contracts: `NotificationEvent`, `NotificationRequestedPayload`, `NotificationEventType`
- Presence contracts: `PresenceEventType`, `PresenceMode`, `PresenceStatus`, `PresenceRoomJoinPayload`, `PresenceRoomLeavePayload`, `PresenceTypingPayload`, `PresenceStopTypingPayload`, `PresenceUserOnlinePayload`, `PresenceUserOfflinePayload`, `PresenceUserStatePayload`, `RoomOnlineUsersPayload`, `GlobalOnlineUsersPayload`
- User contracts: `UserPresencePayload`, `UserEventType`
- Contract validation: `RealtimeContractConventions`, `RealtimeContractValidator`

What is correct:

- Cross-service DTOs belong in a shared contract module if multiple services publish/consume them.
- `RealtimeContractValidator` and `RealtimeContractConventions` are transport-neutral enough for a contract module.

What is misplaced:

- Some payloads are very close to service-domain read models, especially `ChatMessagePayload`, `AttachmentPayload`, `MessageBlockPayload`, and `NotificationEvent`. If these are exposed to clients or only used by one service, they should move to service-level modules.
- `NotificationEvent.NotificationType` is nested inside the payload, while other event types are top-level enums. This is inconsistent and makes type reuse awkward.
- Empty directory `common-events/src/main/java/com/example/common/integration/websocket` suggests a leftover package after `WsEvent` was removed.

What should be added:

- A clear rule: this module contains only integration contracts, not persistence entities, controller DTOs, or frontend-specific payloads.
- Versioning/package strategy for events, for example `integration.chat.v1`.
- Tests that all event type values accepted by publishers pass `RealtimeContractValidator`.

What should be removed:

- Remove stale empty package directories.
- Move service-specific DTOs out if they are not true integration contracts.

Name accuracy: `common-events` is acceptable, though `common-contracts` or `common-integration-contracts` would be more precise.

Package consistency: mostly consistent under `com.example.common.integration.*`, but `enums` is too generic. Prefer domain packages like `integration.chat.MessageType` unless the enum is truly cross-domain.

### `common-kafka`

Responsibility: Kafka transport primitives, publisher abstraction, Kafka auto-configuration, Kafka-specific event envelopes/topics.

Currently contains:

- `com.example.common.kafka.api.KafkaEvent`
- `com.example.common.kafka.api.KafkaEventPublisher`
- `com.example.common.kafka.core.DefaultKafkaEventPublisher`
- `com.example.common.kafka.config.KafkaAutoConfiguration`
- `com.example.common.kafka.observability.KafkaEventLogger`
- `com.example.common.kafka.exception.KafkaPubSubException`
- `com.example.common.integration.kafka.KafkaTopics`
- Kafka envelope classes in files under `common-kafka/src/main/java/com/example/common/kafka/event`, but declared as package `com.example.common.integration.kafka.event`: `AbstractKafkaEvent`, `AccountCreatedEvent`, `ChatMessageSentEvent`, `ChatMessageEditedEvent`, `ChatMessageDeletedEvent`, `ChatReactionUpdatedEvent`, `FriendRequestKafkaEvent`, `FriendshipEvent`, `NotificationRequestedEvent`

What is correct:

- `DefaultKafkaEventPublisher`, `KafkaEventPublisher`, `KafkaEventLogger`, and `KafkaAutoConfiguration` belong here.
- Dependency on `common-events` is reasonable because Kafka wrappers use integration payloads and contract validation.

What is misplaced:

- Kafka event files are physically under `com/example/common/kafka/event` but declare `package com.example.common.integration.kafka.event`. This is a package/file-path mismatch and an ownership smell.
- `KafkaTopics` is in `common-kafka` but package `com.example.common.integration.kafka`; this is partly contract and partly transport. If topic names are integration contracts, they may belong in `common-events` or `common-contracts`; if Kafka-specific, use `com.example.common.kafka.topic`.
- Kafka envelope classes such as `ChatMessageSentEvent` and `NotificationRequestedEvent` combine transport envelope with domain payload. That is acceptable in `common-kafka`, but their package should be `com.example.common.kafka.event` or they should move to `common-events` as transport-neutral envelopes.
- `DefaultKafkaEventPublisher` imports `ObjectMapper` but does not use it.

What should be added:

- Auto-configuration registration metadata if this library is expected to be imported automatically by Spring Boot.
- Tests for publisher validation failures, logger behavior, and topic/event-name validation.

What should be removed:

- Remove unused imports from `DefaultKafkaEventPublisher` and `KafkaAutoConfiguration`.
- Move or rename package declarations for Kafka event classes so file path, package, and module ownership match.

Name accuracy: accurate for most classes, but `com.example.common.integration.kafka.event` inside `common-kafka` blurs module boundaries.

Package consistency: weak. This is one of the most visible structure problems.

### `common-redis`

Responsibility: Redis pub/sub transport primitives and infrastructure.

Currently contains:

- API: `IRedisMessage`, `IRedisPublisher`, `IRedisSubscriber`
- Message model: `AbstractRedisMessage`, `RedisMessage`
- Serialization: `IRedisMessageSerializer`, `JsonRedisMessageSerializer`
- Registry: `IRedisMessageRegistry`, `DefaultRedisMessageRegistry`
- Dispatch/listener: `RedisMessageDispatcher`, `DefaultRedisMessageListener`
- Publisher: `DefaultRedisPublisher`
- Observability: `IRedisPubSubLogger`, `RedisPubSubLogger`
- Config: `RedisAutoConfiguration`
- Constants/exceptions: `RedisMessageFields`, `RedisPubSubException`

What is correct:

- Pub/sub concerns are largely grouped together.
- `DefaultRedisPublisher` depends on `RealtimeContractValidator` from `common-events`, which is acceptable if event identity/naming conventions are shared across transports.
- Transport-specific logging and serialization are in the transport module.

What is misplaced:

- `IRedisMessage` defaults `getEventId()` and `getCorrelationId()` to `getMessageId()`, while `AbstractRedisMessage` declares separate `eventId` and `correlationId` fields but does not override those methods. This is a contract mismatch inside the module.
- `DefaultRedisMessageRegistry` is annotated with `@Component` while `RedisAutoConfiguration` manually wires related beans. Mixing component scanning with explicit auto-configuration makes adoption less predictable.
- `RedisAutoConfiguration.redisListener()` requires concrete `RedisPubSubLogger` instead of interface `IRedisPubSubLogger`, unlike other wiring.
- `DefaultRedisMessageListener` swallows deserialization/dispatch exceptions after logging. That may be intentional for pub/sub, but the behavior should be explicit and documented.

What should be added:

- A consistent envelope contract for message ID, event ID, correlation ID, source service, created time, and payload.
- Tests for serializer round-trip, unknown event type, duplicate subscriber event type, and validation failures.

What should be removed:

- Remove unused import `com.example.common.redis.api.IRedisMessage` from `IRedisMessageRegistry`.
- Avoid `@Component` on classes that should be supplied by auto-configuration, or make all Redis infrastructure component-scanned consistently.

Name accuracy: accurate for pub/sub.

Package consistency: internally consistent, but it overlaps package names with `common-redis-cache`.

### `common-redis-cache`

Responsibility: Redis-backed cache extensions, especially cache operations with per-entry TTL.

Currently contains:

- `com.example.common.redis.api.ITimeRedisCache`
- `com.example.common.redis.api.ITimeRedisCacheManager`
- `com.example.common.redis.core.TimeRedisCache`
- `com.example.common.redis.core.TimeRedisCacheManager`
- `com.example.common.redis.exception.CreateCacheException`

What is correct:

- TTL cache behavior is a distinct concern from Redis pub/sub.
- `TimeRedisCache` and `TimeRedisCacheManager` wrap Spring Redis cache classes rather than duplicating the entire cache implementation.

What is misplaced:

- The module uses the same root package as `common-redis`: `com.example.common.redis.*`. This means consumers cannot tell whether `com.example.common.redis.api.*` comes from pub/sub or cache.
- `ITimeRedisCacheManager` imports and casts to concrete `TimeRedisCache`, so the interface is not a clean abstraction.
- `common-redis-cache/build.gradle` declares `implementation project(':common:common-events')`, but no current Java source imports `common-events`. This is an unnecessary dependency direction.
- `TimeRedisCacheManager` requires `serviceName` for key prefixing. This is infrastructure configuration, but if service naming rules become business-specific they should stay in service config, not the shared cache module.
- Empty package directory `common-redis-cache/src/main/java/com/example/common/redis/config` exists.

What should be added:

- Auto-configuration or documented factory usage for services to instantiate `TimeRedisCacheManager`.
- Tests for TTL put, service-name prefixing, Redis connection failure behavior, and cache fail recovery.

What should be removed:

- Remove unused `common-events` dependency.
- Rename packages to `com.example.common.redis.cache.*` or module to `common-cache-redis`.

Name accuracy: module name is accurate, package name is not specific enough.

Package consistency: weak because it collides with `common-redis`.

### `common-websocket`

Responsibility: WebSocket transport primitives, session/broadcast contracts, and handshake support.

Currently contains:

- Session/broadcaster interfaces: `IWebSocketSessionRegistry`, `IRoomSessionRegistry`, `IUserBroadcaster`, `IRoomBroadcaster`, `IGlobalBroadcaster`
- Handshake: `AbstractJwtHandshakeInterceptor`, `IJwtHandshakeInterceptor`, `JwtHandshakeInterceptor`, `JwtHandshakeHandler`, `WsPrincipal`
- Broadcasting: `AbstractWebSocketBroadcaster`
- Outbound DTOs: `WsOutgoingMessage`, `RealtimeWsEvent`

What is correct:

- WebSocket handshake and broadcaster code belongs in `common-websocket`.
- `JwtHandshakeInterceptor` being WebSocket-specific is a clean separation from REST security.

What is misplaced:

- `WsOutgoingMessage` and `RealtimeWsEvent` are structurally duplicate DTOs: both have `type` and `payload`, both alias `data` to `payload`.
- `common-websocket/build.gradle` declares dependency on `common-events`, but current Java source does not import `com.example.common.integration.*`.
- `AbstractJwtHandshakeInterceptor` logs token prefixes and diagnostic headers. That is useful during debugging, but it is risky as a default common library behavior.
- `JwtHandshakeInterceptor` duplicates UUID subject parsing already provided by `common-security.JwtHelper`.
- Source includes mojibake/non-ASCII corruption in comments/log messages in `AbstractJwtHandshakeInterceptor` and `JwtHandshakeInterceptor`, which hurts maintainability.
- Empty package directory `common-websocket/src/main/java/com/example/common/websocket/util` exists.

What should be added:

- A single outbound WebSocket envelope class.
- Optional integration with `common-security.JwtHelper`.
- Tests for missing token, invalid token, non-UUID subject, and successful principal assignment.

What should be removed:

- Remove unused `common-events` dependency.
- Remove one of `WsOutgoingMessage` or `RealtimeWsEvent`.
- Remove token-prefix logging from default shared behavior.

Name accuracy: accurate.

Package consistency: good, except duplicate DTO naming.

### `common-feign`

Responsibility: OpenFeign-specific shared configuration.

Currently contains:

- `com.example.common.feign.FeignTraceConfig`

What is correct:

- Feign trace propagation is transport-specific and no longer belongs in `common-web`.
- Module name and package match.

What is misplaced:

- No misplaced source found.

What should be added:

- Tests or a simple contract test for propagating `X-Trace-Id`.
- Optional auto-configuration metadata if this should be automatically applied.

What should be removed:

- Nothing from source.

Name accuracy: accurate.

Package consistency: good.

### `common-media`

Responsibility: shared media/upload contracts or media utilities.

Currently contains:

- `build.gradle`
- Empty source directories under `src/main/java/com/example/common/media`
- No active Java source files under `common-media/src`

What is correct:

- A separate media module can make sense if multiple services share media metadata contracts or MIME/image helpers.

What is misplaced:

- The module currently has no active source, so it is dead weight as a shared module.
- Build dependencies on Jackson/Lombok exist without source using them.

What should be added:

- Only add source if there are real cross-service media contracts, for example immutable upload asset metadata used by both upload-service and chat-service.

What should be removed:

- Remove the module from common if media contracts now live in a service-specific module.
- Remove empty source directories if the module remains intentionally empty temporarily.

Name accuracy: accurate conceptually, but inaccurate operationally because the module is empty.

Package consistency: no active package structure to evaluate.

## 3. Cross-Module Problems

### High

1. Realtime delivery policy is in the wrong module.

- Exact files/classes: `common-web/src/main/java/com/example/common/realtime/policy/RealtimeFlowId.java`, `RealtimeFlowType.java`, `RealtimeFlowClassificationPolicy.java`
- Why it is a problem: these classes encode Kafka/Redis/WebSocket delivery semantics and service flow names, but they live in `common-web`. Non-HTTP modules or services should not need a web module to classify realtime delivery.
- Current dependencies: `common-web` depends on `common-core`; the realtime policy itself depends only on Java collections/enums.
- Should depend on instead: move to `common-events`, `common-contracts`, or a new `common-realtime` module with no dependency on `common-web`.

2. Kafka event package does not match file path or module boundary.

- Exact files/classes: all files in `common-kafka/src/main/java/com/example/common/kafka/event/*.java`, including `AbstractKafkaEvent`, `ChatMessageSentEvent`, `ChatReactionUpdatedEvent`, `NotificationRequestedEvent`
- Why it is a problem: files are under `common/kafka/event` but declare `package com.example.common.integration.kafka.event`. This blurs whether the classes belong to Kafka infrastructure or integration contracts.
- Current dependencies: these classes depend on `common-events` payloads and `common-kafka.api.KafkaEvent`.
- Should depend on instead: either use package `com.example.common.kafka.event` and keep them in `common-kafka`, or move them physically to `common-events` if they are considered integration contract envelopes.

3. Event naming conventions conflict with actual event values.

- Exact files/classes: `RealtimeContractValidator`, `RealtimeContractConventions`, `PresenceEventType`
- Why it is a problem: `RealtimeContractConventions.EVENT_NAME_PATTERN` allows lower-dot and hyphen segments, but `PresenceEventType` includes values with underscores: `presence.user.status_changed`, `presence.room.stop_typing`, `presence.global.online_users`, `presence.room.online_users`. These will fail validation in `DefaultRedisPublisher` or `DefaultKafkaEventPublisher` if used as event names.
- Current dependencies: Redis/Kafka publishers depend on `common-events` validation.
- Should depend on instead: not a dependency change; fix the convention or fix event names so all shared contracts use the same naming rule.

4. Duplicate realtime/WebSocket envelope DTOs.

- Exact files/classes: `common-websocket/src/main/java/com/example/common/websocket/dto/WsOutgoingMessage.java`, `common-websocket/src/main/java/com/example/common/websocket/protocol/RealtimeWsEvent.java`
- Why it is a problem: both expose `type` and `payload` with `@JsonAlias("data")`; consumers can diverge by choosing different classes for the same wire shape.
- Current dependencies: both depend only on Jackson/Lombok.
- Should depend on instead: keep one canonical WebSocket envelope in `common-websocket.protocol`, or define a transport-neutral realtime envelope in `common-events` and adapt it in WebSocket code.

### Medium

1. `common-core` is not fully transport-neutral.

- Exact files/classes: `common-core/src/main/java/com/example/common/core/exception/IErrorCode.java`, `CommonErrorCode.java`
- Why it is a problem: `IErrorCode.httpStatus()` bakes HTTP response status into the lowest module.
- Current dependencies: no external module dependency, but conceptual dependency on HTTP.
- Should depend on instead: move HTTP mapping into `common-web`; keep `IErrorCode` limited to stable code/name/message semantics.

2. `common-redis-cache` overlaps package namespace with `common-redis`.

- Exact files/classes: `ITimeRedisCache`, `ITimeRedisCacheManager`, `TimeRedisCache`, `TimeRedisCacheManager`, `CreateCacheException`
- Why it is a problem: both modules publish `com.example.common.redis.*` packages, making ownership unclear and increasing collision risk.
- Current dependencies: `common-redis-cache` declares `common-events` and Spring Redis dependencies.
- Should depend on instead: no `common-events`; package should be `com.example.common.redis.cache.*` or module should merge into `common-redis` under a `cache` subpackage.

3. Unused common module dependencies.

- Exact files/classes/builds: `common-websocket/build.gradle` declares `implementation project(':common:common-events')`; `common-redis-cache/build.gradle` declares `implementation project(':common:common-events')`; `common-security/build.gradle` declares `implementation project(':common:common-core')`
- Why it is a problem: unused dependencies make modules look higher-level than they are and increase transitive coupling.
- Current dependencies: declared but not used by current Java source.
- Should depend on instead: remove until a source-level dependency exists.

4. Redis message identity contract is internally inconsistent.

- Exact files/classes: `IRedisMessage`, `AbstractRedisMessage`, `RedisMessage`, `DefaultRedisPublisher`
- Why it is a problem: `IRedisMessage` defaults `eventId` and `correlationId` to `messageId`, while `AbstractRedisMessage` has separate fields. `DefaultRedisPublisher` validates `getEventId()` and `getCorrelationId()`, but those may not reflect the separate fields.
- Current dependencies: Redis message classes depend on Java/Lombok; publisher depends on `RealtimeContractValidator`.
- Should depend on instead: not a dependency issue; align the interface and base class so event identity is explicit.

5. Domain-specific contracts may be too rich for common.

- Exact files/classes: `ChatMessagePayload`, `AttachmentPayload`, `MessageBlockPayload`, `NotificationEvent`, `PresenceUserStatePayload`, `RoomOnlineUsersPayload`
- Why it is a problem: common modules should not become shared domain model dumping grounds. Some of these classes look like service read models or client DTOs rather than minimal integration events.
- Current dependencies: these are in `common-events` and depend on Jackson/Lombok/Java.
- Should depend on instead: if only one service owns or consumes a type, move it to that service. Keep only cross-service event contracts in common.

### Low

1. Naming style uses `I` prefixes inconsistently.

- Exact files/classes: `IErrorCode`, `IRedisMessage`, `IRedisPublisher`, `IRedisSubscriber`, `ITimeRedisCache`, `IWebSocketSessionRegistry`, `IJwtHandshakeInterceptor`
- Why it is a problem: Java conventions usually avoid `I` prefixes, and the codebase mixes concrete and interface names in a less idiomatic way.
- Current dependencies: not a dependency issue.
- Should depend on instead: use names like `ErrorCode`, `RedisMessage`, `RedisPublisher`, `RedisSubscriber`, `TimeRedisCacheManager`, or keep the prefix consistently if that is the chosen house style.

2. Empty/stale packages remain.

- Exact directories: `common-events/src/main/java/com/example/common/integration/websocket`, `common-web/src/main/java/com/example/common/web/config`, `common-web/src/main/java/com/example/common/web/security`, `common-redis-cache/src/main/java/com/example/common/redis/config`, `common-websocket/src/main/java/com/example/common/websocket/util`, `common-media/src/main/java/com/example/common/media`
- Why it is a problem: stale packages confuse ownership and suggest incomplete moves.
- Current dependencies: none.
- Should depend on instead: remove empty packages or add intended source.

3. Logging/debug behavior is too noisy or risky for shared defaults.

- Exact files/classes: `AbstractJwtHandshakeInterceptor`, `JwtHandshakeInterceptor`, `RedisPubSubLogger`
- Why it is a problem: token prefix logging and full raw Redis payload logging can expose sensitive data or create noisy logs across every service.
- Current dependencies: Micrometer, Spring WebSocket/Security, SLF4J.
- Should depend on instead: use configurable logging levels/redaction policy supplied by service config.

## 4. Dependency Direction Review

The lowest layer should be `common-core`. It should have no dependency on Spring, HTTP, Kafka, Redis, WebSocket, Feign, security, or service domains.

Recommended dependency order:

1. `common-core`: generic Java primitives only.
2. `common-events` or renamed `common-contracts`: transport-neutral integration contracts, event naming conventions, and shared payloads.
3. `common-security`: JWT/security helper utilities. It may depend on `common-core` only if it uses core exceptions/contracts.
4. Transport modules:
   - `common-web` depends on `common-core` and optionally `common-security`.
   - `common-feign` depends on no common modules, or optionally on a future `common-observability` for trace constants.
   - `common-kafka` depends on `common-events` and Kafka libraries.
   - `common-redis` depends on `common-events` and Redis libraries.
   - `common-websocket` should depend on `common-security` if it reuses JWT helpers, and on `common-events` only if it uses shared envelopes/contracts.
   - `common-redis-cache` should depend on Redis/Spring cache only; it does not need `common-events`.
5. Optional domain-specific contract modules if `common-events` grows too large: `common-chat-contracts`, `common-presence-contracts`, `common-notification-contracts`, etc.

Current inverted or suspicious directions:

- `common-web` owns `com.example.common.realtime.policy.*`, which creates a conceptual dependency from transport-neutral realtime logic to HTTP/web.
- `common-redis-cache` depends on `common-events` without source usage.
- `common-websocket` depends on `common-events` without source usage.
- `common-security` depends on `common-core` without source usage.
- `common-core` has an HTTP-shaped `IErrorCode.httpStatus()` contract even though it has no Gradle dependency on web.

No Gradle circular dependency was found inside the current common modules from the inspected build files.

## 5. Misplaced Code

Classes that belong in another common module:

- `common-web/src/main/java/com/example/common/realtime/policy/RealtimeFlowId.java`: move to `common-events`, `common-contracts`, or new `common-realtime`.
- `common-web/src/main/java/com/example/common/realtime/policy/RealtimeFlowType.java`: move with `RealtimeFlowId`.
- `common-web/src/main/java/com/example/common/realtime/policy/RealtimeFlowClassificationPolicy.java`: move with realtime contracts.
- `common-kafka/src/main/java/com/example/common/integration/kafka/KafkaTopics.java`: move to `common-events`/`common-contracts` if topic names are integration contracts, or rename package to `com.example.common.kafka.topic` if Kafka-only.
- `common-kafka/src/main/java/com/example/common/kafka/event/*.java`: either move package/file path to `com.example.common.kafka.event` or move files to `common-events` if they are integration envelopes.
- `common-websocket/src/main/java/com/example/common/websocket/dto/WsOutgoingMessage.java` and `common-websocket/src/main/java/com/example/common/websocket/protocol/RealtimeWsEvent.java`: merge into one canonical class.
- `common-redis-cache/src/main/java/com/example/common/redis/*`: rename packages to `com.example.common.redis.cache.*` or merge into `common-redis` under `cache`.

Classes that may belong in service-level modules:

- `ChatMessagePayload`: move to chat-service if it is mostly chat read model/client message shape rather than a minimal cross-service event contract.
- `AttachmentPayload`: move to upload-service or chat-service if only upload/chat owns these fields.
- `MessageBlockPayload`: move to chat-service if blocks are internal chat message structure.
- `NotificationEvent`: move to notification-service if it is a notification read/client DTO rather than a consumed integration event.
- `PresenceUserStatePayload`, `GlobalOnlineUsersPayload`, `RoomOnlineUsersPayload`: move to presence-service if only presence publishes and consumes them.

Classes too domain-specific for generic common but acceptable in an integration-contract module:

- `ChatEventType`, `FriendshipEventType`, `NotificationEventType`, `PresenceEventType`, `AccountEventType`, `UserEventType`
- `MessageType`, `AttachmentType`, `ReactionAction`

These should stay out of `common-core`, `common-web`, `common-redis`, `common-kafka`, and `common-websocket`.

## 6. Naming and Structure Consistency

Package naming:

- Good: `com.example.common.core.*`, `com.example.common.web.*`, `com.example.common.security.*`, `com.example.common.redis.*`, `com.example.common.websocket.*`, `com.example.common.feign.*`.
- Weak: `common-kafka` files under `common/kafka/event` declare `com.example.common.integration.kafka.event`.
- Weak: `common-redis-cache` shares `com.example.common.redis.*` with `common-redis`.
- Weak: `common-web` contains `com.example.common.realtime.policy`, which does not include `.web`.

Class naming:

- `BusinessException`, `CommonErrorCode`, `ApiResponse`, `ApiError`, `TraceIdFilter`, `FeignTraceConfig`, `JwtHelper` are clear.
- `NotificationEvent` is vague compared with `NotificationRequestedPayload`.
- `FriendRequestEvent` in `common-events` and `FriendRequestKafkaEvent` in `common-kafka` are easy to confuse.
- `WsOutgoingMessage` and `RealtimeWsEvent` are duplicate names for the same shape.

Interface naming:

- `I*` prefix is used consistently in Redis/WebSocket/cache/core interfaces but is not idiomatic Java. Either keep it as a house style or remove it in a planned sweep.
- `ITimeRedisCacheManager` is not a clean interface because it imports/casts to concrete `TimeRedisCache`.

Config naming:

- `KafkaAutoConfiguration` and `RedisAutoConfiguration` are clear.
- `FeignTraceConfig` is clear but should be explicitly auto-configured if services are not expected to import it manually.
- `CorsProperties` is well named, but the legacy fallback to `common.security.cors` belongs in migration notes or a bounded deprecation plan.

Util/helper naming:

- `JwtHelper` is acceptable but very narrow. If more helpers are added, prefer specific names like `JwtSubjectExtractor`.
- `RealtimeContractValidator` is clear and reusable.

Folder layout:

- Multiple empty directories remain after moves/deletions.
- Build outputs exist under common module directories; those should not be part of architectural source review and should remain ignored/untracked.
- Active source layout should avoid empty `config`, `security`, `util`, and `websocket` leftovers unless they are about to receive code.

## 7. Recommended Target Structure

Recommended modules to remain:

- `common-core`
- `common-web`
- `common-security`
- `common-feign`
- `common-events` renamed to `common-contracts` or `common-integration-contracts`
- `common-kafka`
- `common-redis`
- `common-websocket`

Recommended modules to merge or remove:

- Remove `common-media` until it has active shared contracts. Re-add it only when more than one service needs media contracts.
- Merge `common-redis-cache` into `common-redis` under `com.example.common.redis.cache.*` if Redis cache is used broadly and the module count should stay small. Keep it separate only if services often want Redis pub/sub without cache.

Recommended modules to split:

- If `common-events` continues to grow, split by bounded context:
  - `common-contracts-core`: shared conventions/envelopes.
  - `common-chat-contracts`
  - `common-presence-contracts`
  - `common-notification-contracts`
  - `common-friendship-contracts`

Suggested new boundaries:

- `common-core`: pure Java, no transport semantics.
- `common-contracts`: event names, shared envelopes, payload DTOs that cross service boundaries, validation conventions.
- `common-web`: REST-only response/error/filter/CORS.
- `common-security`: JWT/security helpers not tied to REST or WebSocket.
- `common-kafka`: Kafka producer/consumer infrastructure and Kafka-specific envelopes if not transport-neutral.
- `common-redis`: Redis pub/sub infrastructure.
- `common-redis-cache`: optional cache-only module or subpackage.
- `common-websocket`: WebSocket handshake/session/broadcast/outbound protocol.
- `common-feign`: OpenFeign client configuration.

Suggested dependency rules:

- `common-core` depends on nothing.
- `common-contracts` depends on Jackson/Lombok only, not Spring transport modules.
- `common-web` may depend on `common-core`; it should not own realtime delivery policy.
- `common-security` may depend on `common-core` only if source uses it.
- `common-kafka` and `common-redis` may depend on `common-contracts`.
- `common-websocket` may depend on `common-security` and `common-contracts` only when source uses shared contracts.
- No common transport module should depend on another common transport module unless there is a deliberate adapter module.

## 8. Refactor Priority Order

1. Fix event-name convention mismatch first: align `PresenceEventType` values or `RealtimeContractConventions.EVENT_NAME_PATTERN` so shared publishers do not reject valid shared events.
2. Move `com.example.common.realtime.policy.*` out of `common-web` into a transport-neutral module.
3. Resolve `common-kafka` package/file-path mismatch for `com.example.common.integration.kafka.event.*`.
4. Merge `WsOutgoingMessage` and `RealtimeWsEvent` into one WebSocket envelope.
5. Remove unused Gradle dependencies from `common-websocket`, `common-redis-cache`, and `common-security`.
6. Rename `common-redis-cache` packages to avoid colliding with `common-redis`, or merge cache into `common-redis/cache`.
7. Clarify `IErrorCode`: either keep it as an HTTP-aware shared error contract or move HTTP status mapping into `common-web`.
8. Audit `common-events` payloads and move any service-only DTOs back into owning service modules.
9. Remove empty/stale package directories.
10. Add focused tests around common contracts, Redis/Kafka validation, WebSocket handshake behavior, and pipeline execution.

## 9. Final Verdict

The current `common` structure is messy but recoverable.

It is not a full redesign situation because the major module categories are recognizable and the Gradle dependency graph is mostly acyclic. The main work is boundary cleanup: move realtime policy out of web, normalize event contracts, remove unused dependencies, stop package overlap between Redis modules, and prevent common contracts from becoming service-domain dumping grounds.

With those changes, the common area can become maintainable for a microservice architecture. Without them, the shared layer will keep accumulating transport leaks and domain-specific payloads, making every service more coupled over time.
