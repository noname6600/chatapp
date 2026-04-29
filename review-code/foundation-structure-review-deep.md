# Foundation Structure Review (Deep Pass)

Scope: foundation-level/shared code only. This review excludes business logic and individual service behavior except where a service-local duplicate shows that the shared/base layer is structurally incomplete or inconsistent.

Reviewed modules:
- `common-core`
- `common-events`
- `common-kafka`
- `common-redis`
- `common-redis-cache`
- `common-web`
- `common-websocket`

## 1. Foundation structure summary

The shared/base layer is organized as several technology-oriented modules, but their internal boundaries are inconsistent:

- `common-core` is the cleanest module. It mostly contains generic utilities and technical helpers such as [JacksonConfig](D:/Work/PET/chatappPET/chatapp/chatappBE/common/common-core/src/main/java/com/example/common/core/config/JacksonConfig.java), pipeline helpers, and upload metadata.
- `common-events` acts as a cross-service contract module, holding integration DTOs grouped by domain (`account`, `chat`, `friendship`, `notification`, `presence`, `user`). However, it also contains websocket-shaped transport DTOs under `integration.websocket`.
- `common-kafka` contains both Kafka infrastructure (`api`, `config`, `core`, `exception`, `observability`) and domain-specific event wrappers plus topic ownership.
- `common-redis` contains Redis pub/sub abstractions and technical plumbing and is structurally coherent on its own.
- `common-redis-cache` is a second Redis-focused module, but it shares the same Java package root as `common-redis`, so the module split is not reflected in package ownership.
- `common-web` bundles response models, exception handling, controller helpers, tracing/filter, CORS properties, and Feign-related config. It is usable, but too broad for a stable base module.
- `common-websocket` contains broadcaster/session abstractions, websocket transport DTOs, JWT handshake logic, and websocket principal handling. It is partly infrastructure-neutral and partly security-policy-specific.

At the dependency level, the direction is mostly reasonable:
- `common-kafka -> common-events`
- `common-redis -> common-events`
- `common-websocket -> common-events`

The larger issue is not the arrow direction itself, but that several modules mix transport plumbing with shared contracts or service-adjacent policy.

## 2. High-risk design problems

### High

**1. `common-web` contains service/domain-specific error semantics**

Evidence:
- [ErrorCode.java](D:/Work/PET/chatappPET/chatapp/chatappBE/common/common-web/src/main/java/com/example/common/web/exception/ErrorCode.java)

Problem:
- This file mixes base HTTP/application errors (`BAD_REQUEST`, `UNAUTHORIZED`, `RESOURCE_NOT_FOUND`) with chat/group-specific ones (`MESSAGE_NOT_FOUND`, `MESSAGE_DELETED`, `REACTION_INVALID`, `REMOVED_FROM_GROUP`, `BLOCKED_SEND`).

Why this is risky:
- It turns the global web foundation into a domain catalog.
- Any new service is encouraged to keep adding business-specific codes into global common.
- It makes `common-web` depend semantically on chat/group concerns even though there is no formal module dependency.

**2. WebSocket message contracts are split across two shared modules**

Evidence:
- [WsOutgoingMessage.java](D:/Work/PET/chatappPET/chatapp/chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/dto/WsOutgoingMessage.java)
- [WsEvent.java](D:/Work/PET/chatappPET/chatapp/chatappBE/common/common-events/src/main/java/com/example/common/integration/websocket/WsEvent.java)

Problem:
- Both represent outbound websocket-style envelopes.
- One uses `data`, the other uses `payload`.
- One lives in the websocket transport module, the other in shared integration contracts.

Why this is risky:
- Naming and ownership are inconsistent at the foundation level.
- Services can choose different envelopes and drift further.
- The common layer has no single authoritative websocket message contract.

**3. `common-kafka` mixes transport infrastructure with shared domain event ownership**

Evidence:
- Kafka infra: [KafkaEvent.java](D:/Work/PET/chatappPET/chatapp/chatappBE/common/common-kafka/src/main/java/com/example/common/kafka/api/KafkaEvent.java), [DefaultKafkaEventPublisher.java](D:/Work/PET/chatappPET/chatapp/chatappBE/common/common-kafka/src/main/java/com/example/common/kafka/core/DefaultKafkaEventPublisher.java)
- Domain event wrappers: `common-kafka/src/main/java/com/example/common/kafka/event/*`
- Topic catalogs: [KafkaTopics.java](D:/Work/PET/chatappPET/chatapp/chatappBE/common/common-kafka/src/main/java/com/example/common/integration/kafka/KafkaTopics.java), [Topics.java](D:/Work/PET/chatappPET/chatapp/chatappBE/common/common-kafka/src/main/java/com/example/common/kafka/Topics.java)

Problem:
- The module is partly Kafka transport abstraction and partly shared business event catalog.
- It also contains two topic constants classes, one of which is deprecated.

Why this is risky:
- Shared contracts and transport details evolve at different speeds.
- The deprecated mirror class keeps backward compatibility debt inside the foundation.
- Naming is inconsistent even within one module: `com.example.common.integration.kafka.KafkaTopics` vs `com.example.common.kafka.Topics`.

**4. `common-websocket` is not a pure websocket base; it carries a concrete auth/handshake policy**

Evidence:
- [AbstractJwtHandshakeInterceptor.java](D:/Work/PET/chatappPET/chatapp/chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/handshake/AbstractJwtHandshakeInterceptor.java)
- [JwtHandshakeInterceptor.java](D:/Work/PET/chatappPET/chatapp/chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/handshake/JwtHandshakeInterceptor.java)
- [JwtHandshakeHandler.java](D:/Work/PET/chatappPET/chatapp/chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/handshake/JwtHandshakeHandler.java)
- [common-websocket/build.gradle](D:/Work/PET/chatappPET/chatapp/chatappBE/common/common-websocket/build.gradle)

Problem:
- The module is named like a general websocket abstraction, but it includes:
  - JWT decoding
  - token lookup from query parameter `token`
  - handshake rejection header policy
  - Micrometer-based handshake metrics
  - principal creation

Why this is risky:
- It makes “common websocket” implicitly mean “websocket with this specific JWT handshake flow.”
- That is reusable today, but it is not neutral base infrastructure.
- If another socket auth mode is needed later, this module boundary will fight the design.

**5. Redis common is split into two modules without a distinct package boundary**

Evidence:
- `common-redis` packages: `com.example.common.redis.*`
- `common-redis-cache` packages: also `com.example.common.redis.*`

Problem:
- The build system says there are two separate modules.
- The Java package structure says they are one conceptual namespace.

Why this is risky:
- Ownership is unclear.
- Imports do not show which module a class truly belongs to.
- Future cleanup is harder because physical and logical boundaries disagree.

### Medium

**6. `common-web` is too broad for a stable base module**

Evidence:
- [common-web/build.gradle](D:/Work/PET/chatappPET/chatapp/chatappBE/common/common-web/build.gradle)
- packages: `controller`, `exception`, `filter`, `response`, `security`, `config`

Problem:
- It bundles:
  - response envelope
  - error handling
  - trace filter
  - controller helper
  - CORS properties
  - Feign tracing config
  - Spring Security resource-server dependency
  - OpenFeign dependency

Why this matters:
- This is a “common-everything-web” module, not a focused HTTP foundation.
- The more concerns it holds, the more often unrelated services inherit dependencies and conventions they may not need.

**7. Base abstractions exist, but the shared layer is still not authoritative enough to prevent service duplication**

Evidence:
- Shared interfaces: [IWebSocketSessionRegistry.java](D:/Work/PET/chatappPET/chatapp/chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/session/IWebSocketSessionRegistry.java), [IRoomSessionRegistry.java](D:/Work/PET/chatappPET/chatapp/chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/session/IRoomSessionRegistry.java)
- Duplicates in services:
  - [ChatSessionRegistry.java](D:/Work/PET/chatappPET/chatapp/chatappBE/chat-service/src/main/java/com/example/chat/realtime/websocket/session/ChatSessionRegistry.java)
  - [FriendshipSessionRegistry.java](D:/Work/PET/chatappPET/chatapp/chatappBE/friendship-service/src/main/java/com/example/friendship/websocket/FriendshipSessionRegistry.java)
  - [NotificationSessionRegistry.java](D:/Work/PET/chatappPET/chatapp/chatappBE/notification-service/src/main/java/com/example/notification/websocket/NotificationSessionRegistry.java)

Problem:
- The common layer offers interfaces, but not a clear reusable base implementation for the repeated session-registry pattern.

Why this matters:
- The duplication itself is in services, but the structural signal is that the shared abstraction is incomplete.

**8. Shared JSON ownership is unclear**

Evidence:
- shared mapper: [common-core JacksonConfig](D:/Work/PET/chatappPET/chatapp/chatappBE/common/common-core/src/main/java/com/example/common/core/config/JacksonConfig.java)
- service-local duplicates:
  - [presence-service JacksonConfig](D:/Work/PET/chatappPET/chatapp/chatappBE/presence-service/src/main/java/com/example/presence/configuration/JacksonConfig.java)
  - [friendship-service JacksonConfig](D:/Work/PET/chatappPET/chatapp/chatappBE/friendship-service/src/main/java/com/example/friendship/configuration/JacksonConfig.java)

Problem:
- There is already a shared ObjectMapper config, but multiple services still define local versions.

Why this matters:
- The foundation does not clearly own JSON defaults.
- The result is drift in serialization behavior and package structure.

### Low

**9. Naming conventions are inconsistent within the common layer**

Evidence:
- `ErrorCode` plus `IErrorCode`
- `KafkaTopics` plus deprecated `Topics`
- `WsOutgoingMessage` vs `WsEvent`
- `autoconfigure` in Redis but `config` in Kafka and Web

Problem:
- The shared layer does not use one consistent naming rule for contracts, implementations, and configuration packages.

**10. `BaseController` is common, but it is not truly generic**

Evidence:
- [BaseController.java](D:/Work/PET/chatappPET/chatapp/chatappBE/common/common-web/src/main/java/com/example/common/web/controller/BaseController.java)

Problem:
- It mixes generic response helpers with JWT-specific `currentUserId(Jwt jwt)`.

Why this matters:
- A base controller helper should be either transport-agnostic or clearly security-oriented.
- This is a small issue, but it contributes to `common-web` becoming a grab bag.

## 3. Files/packages that should move

These are structural move candidates, not rewrite requests.

### Move out of generic common-web
- [ErrorCode.java](D:/Work/PET/chatappPET/chatapp/chatappBE/common/common-web/src/main/java/com/example/common/web/exception/ErrorCode.java)
  - Keep only shared/system-level error codes in common.
  - Move domain-specific codes to service-level enums implementing `IErrorCode`.

### Consolidate websocket contract ownership
- [WsEvent.java](D:/Work/PET/chatappPET/chatapp/chatappBE/common/common-events/src/main/java/com/example/common/integration/websocket/WsEvent.java)
  - Either move under websocket protocol ownership or remove in favor of a single shared websocket envelope.
- [WsOutgoingMessage.java](D:/Work/PET/chatappPET/chatapp/chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/dto/WsOutgoingMessage.java)
  - Keep only if it becomes the single websocket envelope.

### Separate transport plumbing from domain contract ownership
- `common-kafka/src/main/java/com/example/common/kafka/event/*`
  - These event wrappers belong conceptually with shared contracts, not Kafka plumbing.
- [KafkaTopics.java](D:/Work/PET/chatappPET/chatapp/chatappBE/common/common-kafka/src/main/java/com/example/common/integration/kafka/KafkaTopics.java)
  - Move to the shared contract area if topics are treated as integration contract.
- [Topics.java](D:/Work/PET/chatappPET/chatapp/chatappBE/common/common-kafka/src/main/java/com/example/common/kafka/Topics.java)
  - Remove after usage is migrated; it is only a deprecated alias layer.

### Narrow websocket foundation
- handshake package under `common-websocket`
  - [AbstractJwtHandshakeInterceptor.java](D:/Work/PET/chatappPET/chatapp/chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/handshake/AbstractJwtHandshakeInterceptor.java)
  - [JwtHandshakeInterceptor.java](D:/Work/PET/chatappPET/chatapp/chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/handshake/JwtHandshakeInterceptor.java)
  - [JwtHandshakeHandler.java](D:/Work/PET/chatappPET/chatapp/chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/handshake/JwtHandshakeHandler.java)
  - These should stay shared only if the team accepts this JWT handshake model as the one standard socket-auth policy. Otherwise they belong in a narrower security/websocket-support area.

### Clarify Redis package ownership
- `common-redis-cache/src/main/java/com/example/common/redis/*`
  - If cache remains a separate module, its packages should not masquerade as the same root namespace as pub/sub Redis.

## 4. What should stay in common

These are good candidates to remain shared:

### `common-core`
- generic pipeline helpers
- upload metadata types
- shared ObjectMapper defaults if the team wants one baseline mapper

### `common-web`
- [ApiResponse.java](D:/Work/PET/chatappPET/chatapp/chatappBE/common/common-web/src/main/java/com/example/common/web/response/ApiResponse.java)
- [ApiError.java](D:/Work/PET/chatappPET/chatapp/chatappBE/common/common-web/src/main/java/com/example/common/web/response/ApiError.java)
- [BusinessException.java](D:/Work/PET/chatappPET/chatapp/chatappBE/common/common-web/src/main/java/com/example/common/web/exception/BusinessException.java)
- [IErrorCode.java](D:/Work/PET/chatappPET/chatapp/chatappBE/common/common-web/src/main/java/com/example/common/web/exception/IErrorCode.java)
- [GlobalExceptionHandler.java](D:/Work/PET/chatappPET/chatapp/chatappBE/common/common-web/src/main/java/com/example/common/web/exception/GlobalExceptionHandler.java)
- [TraceIdFilter.java](D:/Work/PET/chatappPET/chatapp/chatappBE/common/common-web/src/main/java/com/example/common/web/filter/TraceIdFilter.java)

### `common-events`
- stable cross-service DTOs/events shared between producer and consumer services
- shared enums that are part of integration payload contracts

### `common-kafka`
- `KafkaEvent` interface
- publisher abstraction
- autoconfiguration
- transport exception and observability helpers

### `common-redis`
- Redis publish/subscribe abstraction
- serializer/dispatcher/listener plumbing
- Redis message base types if they are transport-level and not domain-level

### `common-websocket`
- broadcaster base
- websocket principal helper
- session/broadcaster interfaces
- one agreed shared websocket envelope, if the team wants a standard outbound protocol

## 5. What should be service-specific

These do not belong in shared common unless every service truly depends on the same semantics:

- domain-specific error codes such as message, reaction, group-removal, or blocked-send errors
- service-specific websocket command/request payloads
- presence-specific online query semantics
- any domain event type that is not actually cross-service
- Kafka topic names that are merely internal implementation detail rather than shared producer-consumer contract
- service-local Jackson/Kafka config overrides where behavior intentionally differs
- controller helpers that assume JWT-based identity extraction if not all HTTP services share that exact pattern

## 6. Suggested target base/common structure

This is a conservative target, not a clean-architecture rewrite.

### `common-core`
- `config`
- `pipeline`
- `upload`

### `common-web`
- `response`
- `exception`
- `filter`
- `controller`
- `security` only for truly HTTP-cross-cutting pieces

Rules:
- only generic/shared web concerns
- no service/domain error catalogs

### `common-contracts` (conceptual target for what is currently `common-events`)
- `account`
- `chat`
- `friendship`
- `notification`
- `presence`
- `user`
- optional `messaging` or `topics` package for shared transport-facing contract constants

Rules:
- owns cross-service DTOs and integration enums
- does not own websocket transport helper DTOs unless websocket is explicitly standardized as an integration contract

### `common-kafka`
- `api`
- `autoconfigure` or `config`
- `core`
- `exception`
- `observability`

Rules:
- transport plumbing only
- no domain event catalog if those events are really shared contracts

### `common-redis`
Option A:
- keep one Redis shared module with subpackages `pubsub` and `cache`

Option B:
- keep two modules, but give them distinct package roots so ownership is visible

### `common-websocket`
- `protocol`
- `broadcaster`
- `session`
- `security` or `handshake`

Rules:
- one websocket envelope only
- handshake/security policy clearly marked as policy-specific, not hidden inside generic websocket naming

## 7. Safe refactor order

1. **Freeze the ownership rules for each common module**
   - Decide what each shared module is allowed to contain.
   - This should happen before moving files.

2. **Split shared vs service-specific error codes**
   - Keep `IErrorCode`, `BusinessException`, `GlobalExceptionHandler`.
   - Reduce `common-web` error catalog to base/shared codes only.

3. **Choose one websocket envelope**
   - Standardize `WsOutgoingMessage` vs `WsEvent`.
   - Remove the second shape after usages are migrated.

4. **Clean Kafka shared ownership**
   - Pick one home for topic constants.
   - Move shared event contracts out of Kafka-plumbing packages if contract ownership is meant to be separate.
   - Remove deprecated alias classes after migration.

5. **Fix Redis package/module mismatch**
   - Either merge `common-redis` and `common-redis-cache`, or keep both but give them distinct package roots.

6. **Narrow `common-websocket` naming**
   - Keep shared handshake helpers only if that JWT query-param approach is the deliberate foundation standard.
   - Otherwise isolate them as websocket-security support, not generic websocket base.

7. **Normalize shared JSON/config ownership**
   - Decide whether `common-core` owns the default `ObjectMapper`.
   - After that, remove unnecessary local duplicates service by service.

8. **Only then use the cleaned foundation as the template for service refactors**
   - Service-level package cleanup will be much safer once shared naming and ownership are stable.

## Bottom line

The biggest issue in the foundation layer is not bad code quality; it is inconsistent ownership. The common modules use different partitioning rules at the same time:

- some are technology modules,
- some are contract modules,
- some are cross-cutting utility modules,
- and some mix all three.

If the team fixes that first, the later service-structure cleanup becomes much simpler and far less risky.
