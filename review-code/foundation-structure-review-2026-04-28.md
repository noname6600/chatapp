# Foundation Structure Review

Scope: foundation-level code only. This review covers `common/*`, shared DTO/event placement, base response/error handling, exception structure, security utilities, Redis/Kafka/WebSocket abstractions, config classes, constants/enums, package naming, and dependency direction. Business logic was intentionally not reviewed.

## 1. Foundation structure summary

The backend is split into focused common modules plus service modules:

- `common-web`: API response envelope, error model, exception handler, base controller, CORS properties, Feign trace config.
- `common-events`: cross-service payloads and event-type enums for account, chat, friendship, notification, presence, and websocket event wrappers.
- `common-kafka`: Kafka publisher abstraction, auto-config, topic constants, and Kafka event wrapper classes.
- `common-redis`: Redis pub/sub abstraction, serializer/registry/dispatcher, auto-config.
- `common-redis-cache`: custom Redis cache manager abstraction.
- `common-websocket`: handshake helpers, broadcaster base class, session/broadcaster interfaces, websocket outgoing wrapper.
- `common-core`: generic pipeline framework, shared Jackson config, upload metadata DTO.

What is working well:

- Transport concerns are already separated from most service business packages.
- Redis/Kafka/WebSocket common modules mostly expose infrastructure primitives rather than domain logic.
- Chat service is the only place currently using the generic pipeline core, which keeps the abstraction contained for now.

What is structurally inconsistent:

- Common modules mix three different levels of abstraction: API/web conventions, infrastructure adapters, and cross-service integration contracts.
- Several common modules depend upward on `common-events`, even when they do not appear to need business event contracts.
- Package naming is inconsistent across services: `config` vs `configuration`, flat-by-layer services vs feature-sliced chat modules.

## 2. High-risk design problems

### 2.1 `common-web` is not only "web"; it also hard-codes security and controller behavior

- [BaseController](D:/Work/PET/chatappPET/chatapp/chatappBE/common/common-web/src/main/java/com/example/common/web/controller/BaseController.java:12) couples the common controller base to Spring Security `Jwt`.
- [CorsProperties](D:/Work/PET/chatappPET/chatapp/chatappBE/common/common-web/src/main/java/com/example/common/web/security/CorsProperties.java:12) is fine as shared config, but the controller helper is no longer transport-only.

Risk:

- Any service that wants the shared response helpers also inherits a JWT-specific controller base.
- This makes `common-web` a policy module, not just a web utility module.

Recommendation:

- Keep `ApiResponse`, `ApiError`, `BusinessException`, `IErrorCode`, and `GlobalExceptionHandler` in `common-web`.
- Remove `currentUserId(Jwt)` from `BaseController`, or replace `BaseController` with stateless response helpers only.
- Let each service own its auth principal extraction.

### 2.2 Service domain/application code depends directly on web-layer exceptions

Representative examples:

- [MessageAggregate](D:/Work/PET/chatappPET/chatapp/chatappBE/chat-service/src/main/java/com/example/chat/modules/message/domain/model/MessageAggregate.java:8)
- [MessageQueryService](D:/Work/PET/chatappPET/chatapp/chatappBE/chat-service/src/main/java/com/example/chat/modules/message/application/query/MessageQueryService.java:6)
- [RoomService](D:/Work/PET/chatappPET/chatapp/chatappBE/chat-service/src/main/java/com/example/chat/modules/room/service/impl/RoomService.java:29)
- [FriendCommandService](D:/Work/PET/chatappPET/chatapp/chatappBE/friendship-service/src/main/java/com/example/friendship/service/impl/FriendCommandService.java:8)
- [UserProfileService](D:/Work/PET/chatappPET/chatapp/chatappBE/user-service/src/main/java/com/example/user/service/impl/UserProfileService.java:6)

Risk:

- Domain/application logic is now coupled to HTTP-oriented error handling from `common-web`.
- This makes service internals harder to reuse outside REST and weakens dependency direction.

Recommendation:

- Keep `BusinessException` shared if you want a single exception type.
- But treat it as a core/application exception, not a web exception. Either move it out of `common-web`, or stop importing it inside domain packages.
- Minimum safe step: move domain/application throws behind service-local error code enums and only translate to HTTP in `GlobalExceptionHandler`.

### 2.3 Kafka event wrappers are split across module identity and package identity

Examples:

- File path under `common-kafka`: [AccountCreatedEvent](D:/Work/PET/chatappPET/chatapp/chatappBE/common/common-kafka/src/main/java/com/example/common/kafka/event/AccountCreatedEvent.java:1)
- But package is `com.example.common.integration.kafka.event`
- Same pattern for [AbstractKafkaEvent](D:/Work/PET/chatappPET/chatapp/chatappBE/common/common-kafka/src/main/java/com/example/common/kafka/event/AbstractKafkaEvent.java:1), [ChatMessageSentEvent](D:/Work/PET/chatappPET/chatapp/chatappBE/common/common-kafka/src/main/java/com/example/common/kafka/event/ChatMessageSentEvent.java:1), [FriendshipEvent](D:/Work/PET/chatappPET/chatapp/chatappBE/common/common-kafka/src/main/java/com/example/common/kafka/event/FriendshipEvent.java:1)

Risk:

- The module says "Kafka infrastructure", but the package says "integration contract".
- That blurs whether these classes are transport contracts or Kafka adapter classes.

Recommendation:

- Pick one owner.
- Best fit: keep topic/publisher/auto-config in `common-kafka`, and keep event wrapper classes with integration contracts in `common-events` or a later-renamed `common-integration`.

### 2.4 Duplicate websocket envelope classes exist in two common modules

- [WsOutgoingMessage](D:/Work/PET/chatappPET/chatapp/chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/dto/WsOutgoingMessage.java:15)
- [WsEvent](D:/Work/PET/chatappPET/chatapp/chatappBE/common/common-events/src/main/java/com/example/common/integration/websocket/WsEvent.java:12)

Risk:

- Same shape, same purpose, different names, different modules.
- Services already use both patterns, which will keep drifting.

Recommendation:

- Keep one websocket envelope type only.
- If it is an internal socket wire contract, it belongs with integration/shared transport contracts, not both there and in `common-websocket`.

### 2.5 Base common modules depend upward on business integration contracts

Build-time examples:

- [common-core/build.gradle](D:/Work/PET/chatappPET/chatapp/chatappBE/common/common-core/build.gradle:33)
- [common-redis-cache/build.gradle](D:/Work/PET/chatappPET/chatapp/chatappBE/common/common-redis-cache/build.gradle:33)
- [common-websocket/build.gradle](D:/Work/PET/chatappPET/chatapp/chatappBE/common/common-websocket/build.gradle:33)
- [common-redis/build.gradle](D:/Work/PET/chatappPET/chatapp/chatappBE/common/common-redis/build.gradle:33)
- [common-kafka/build.gradle](D:/Work/PET/chatappPET/chatapp/chatappBE/common/common-kafka/build.gradle:34)

Risk:

- Low-level shared infrastructure now points toward higher-level service event contracts.
- That makes the "base" layer harder to reason about and harder to reuse safely.

Recommendation:

- `common-core` and `common-redis-cache` should not depend on `common-events` unless a real code dependency exists.
- `common-websocket` should depend on contract code only if it owns the shared websocket envelope; otherwise remove that dependency too.

### 2.6 Shared DTO placement is inconsistent; some service client DTOs live in the wrong package

Examples:

- [friendship-service/dto/UserProfileResponse](D:/Work/PET/chatappPET/chatapp/chatappBE/friendship-service/src/main/java/com/example/friendship/dto/UserProfileResponse.java:14)
- [friendship-service/client/UserClient](D:/Work/PET/chatappPET/chatapp/chatappBE/friendship-service/src/main/java/com/example/friendship/client/UserClient.java:18)
- [chat-service/infrastructure/client/UserBasicProfile](D:/Work/PET/chatappPET/chatapp/chatappBE/chat-service/src/main/java/com/example/chat/modules/message/infrastructure/client/UserBasicProfile.java:10)
- [user-service/dto/UserBasicProfile](D:/Work/PET/chatappPET/chatapp/chatappBE/user-service/src/main/java/com/example/user/dto/UserBasicProfile.java:11)

Risk:

- Some DTOs are actually external-client read models, but they are stored as if they were the service's own public DTOs.
- This causes accidental duplication and makes it unclear which service owns the contract.

Recommendation:

- If a DTO is only used by one client inside one service, keep it local under `client/.../dto` or `infrastructure/client/...`.
- Do not move these into a global common module unless multiple consumers truly require the exact same shape.

### 2.7 There are obvious copied foundation classes across services

Examples:

- `SecurityConfig` repeated in auth/user/friendship/notification/chat/upload/gateway
- `RedisCacheConfig` repeated in chat/user/presence
- `KafkaConfiguration` or `KafkaConsumerConfig` repeated in auth/chat/user/notification
- `SwaggerConfig` repeated in auth/user/friendship/notification/chat
- `CloudinaryConfig` repeated in user/chat/upload

Representative files:

- [auth SecurityConfig](D:/Work/PET/chatappPET/chatapp/chatappBE/auth-service/src/main/java/com/example/auth/configuration/SecurityConfig.java:26)
- [user SecurityConfig](D:/Work/PET/chatappPET/chatapp/chatappBE/user-service/src/main/java/com/example/user/configuration/SecurityConfig.java:28)
- [chat SecurityConfig](D:/Work/PET/chatappPET/chatapp/chatappBE/chat-service/src/main/java/com/example/chat/config/SecurityConfig.java:24)
- [chat RedisCacheConfig](D:/Work/PET/chatappPET/chatapp/chatappBE/chat-service/src/main/java/com/example/chat/config/RedisCacheConfig.java:17)
- [user RedisCacheConfig](D:/Work/PET/chatappPET/chatapp/chatappBE/user-service/src/main/java/com/example/user/configuration/RedisCacheConfig.java:19)
- [auth KafkaConfiguration](D:/Work/PET/chatappPET/chatapp/chatappBE/auth-service/src/main/java/com/example/auth/kafka/KafkaConfiguration.java:10)
- [chat KafkaConfiguration](D:/Work/PET/chatappPET/chatapp/chatappBE/chat-service/src/main/java/com/example/chat/modules/message/infrastructure/kafka/KafkaConfiguration.java:10)

Risk:

- Shared behavior will drift silently.
- Fixes to CORS, object mapping, Redis serialization, or Swagger metadata will require many service edits.

Recommendation:

- Only extract the identical parts.
- Leave per-service endpoint rules local, but centralize builders/default beans where the implementation is byte-for-byte the same.

## 3. Files/packages that should move

Move or remove first:

- [friendship-service/src/main/java/com/example/friendship/dto/UpdateProfileRequest.java](D:/Work/PET/chatappPET/chatapp/chatappBE/friendship-service/src/main/java/com/example/friendship/dto/UpdateProfileRequest.java:1)
  This looks misplaced in friendship and appears unrelated to friendship's own API surface.

- [friendship-service/src/main/java/com/example/friendship/dto/UserProfileResponse.java](D:/Work/PET/chatappPET/chatapp/chatappBE/friendship-service/src/main/java/com/example/friendship/dto/UserProfileResponse.java:1)
  Move to a client-specific package such as `com.example.friendship.client.user.dto`.

- [chat-service/src/main/java/com/example/chat/modules/message/infrastructure/client/UserBasicProfile.java](D:/Work/PET/chatappPET/chatapp/chatappBE/chat-service/src/main/java/com/example/chat/modules/message/infrastructure/client/UserBasicProfile.java:1)
  Keep local, but place clearly under a client-contract package, not as a quasi-shared DTO.

- Kafka wrapper classes currently under `common-kafka/src/main/java/com/example/common/kafka/event/*`
  Move them to the module/package that owns integration contracts, because their package already says `com.example.common.integration.kafka.event`.

- One of these two websocket envelope classes should be removed:
  [common-events/.../WsEvent.java](D:/Work/PET/chatappPET/chatapp/chatappBE/common/common-events/src/main/java/com/example/common/integration/websocket/WsEvent.java:1)
  [common-websocket/.../WsOutgoingMessage.java](D:/Work/PET/chatappPET/chatapp/chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/dto/WsOutgoingMessage.java:1)

Potential move after cleanup:

- `BusinessException` and `IErrorCode` out of `common-web` if you want strict dependency direction between core/application and web.

## 4. What should stay in common

Keep these as shared foundation code:

- `ApiResponse` / `ApiError` response envelope in `common-web`
- `GlobalExceptionHandler` in `common-web`
- Shared error-code interface in common
- `CorsProperties` and Feign trace support in `common-web`
- Kafka publisher abstraction, topic registry, and Kafka auto-config in `common-kafka`
- Redis serializer/registry/dispatcher/publisher infrastructure in `common-redis`
- Time-based Redis cache manager in `common-redis-cache`
- WebSocket handshake helpers, broadcaster base class, and minimal broadcaster/session interfaces in `common-websocket`
- Generic pipeline primitives in `common-core` as long as only truly generic pipeline code stays there
- Cross-service event payloads/enums in `common-events`
- `UploadAssetMetadata` in `common-core` because both upload and user currently use the same validation metadata shape

## 5. What should be service-specific

These should stay owned by the service unless multiple services truly share the exact same contract and lifecycle:

- All controller request/response DTOs for a service's public API
- Feign/client DTOs that exist only to consume one downstream endpoint
- Domain enums such as chat message model enums, friendship status, notification persistence enums
- WebSocket session registries and room membership state
- Redis channel names like [ChatRedisChannels](D:/Work/PET/chatappPET/chatapp/chatappBE/chat-service/src/main/java/com/example/chat/constants/ChatRedisChannels.java:1) and service-specific equivalents
- Security endpoint allowlists inside each service `SecurityConfig`
- Service-local schema patch runners such as each `DatabaseSchemaFixer`
- Cloudinary-specific config and DTOs unless you deliberately create a shared upload adapter later
- "Unread count" DTOs and similar tiny response wrappers; these are too small and too semantic to force into common

## 6. Suggested target base/common structure

Keep the existing module count, but make responsibilities cleaner:

- `common-web`
  Response envelope, exception translation, shared web config helpers, trace/correlation helpers.

- `common-events`
  Cross-service transport contracts only:
  account/chat/friendship/notification/presence payloads, event wrapper classes, topic-name constants if you want a single integration-contract home.

- `common-kafka`
  Kafka infrastructure only:
  publisher interface, default publisher, auto-config, logging, maybe serializers if later needed.

- `common-redis`
  Redis pub/sub infrastructure only:
  message abstraction, serializer, registry, dispatcher, listener, publisher.

- `common-redis-cache`
  Redis cache manager only.

- `common-websocket`
  Handshake/auth helpers, abstract broadcaster, minimal session/broadcaster interfaces.
  Do not also own a second websocket wire-contract model if `common-events` already does.

- `common-core`
  Small truly generic utilities only:
  pipeline primitives, upload metadata, shared Jackson config if you want one source of truth.

Service package direction:

- Prefer one convention across services.
- Low-risk choice: `config`, `controller`, `application`, `domain`, `infrastructure`, `client`.
- For services that are still flat, do not rewrite everything now; just stop adding new packages under both `config` and `configuration`.

## 7. Safe refactor order

1. Remove or relocate obviously misplaced DTOs first.
   Start with friendship's copied user DTOs and the stray `UpdateProfileRequest`.

2. Collapse duplicate websocket envelope classes into one shared type.
   This is high value and low blast radius if done with compatibility aliases.

3. Clean common module dependency direction.
   Remove unnecessary `common-events` dependencies from `common-core` and `common-redis-cache` first.

4. Standardize client-contract placement.
   Move downstream-consumption DTOs under `client/.../dto` or `infrastructure/client/...` per service.

5. Extract only identical config fragments.
   Good first targets: Redis cache manager construction, Kafka `ObjectMapper` bean, shared CORS builder method.

6. Decouple domain/application from `common-web`.
   Move shared exception primitives to a more neutral home, or at minimum stop importing them in domain packages going forward.

7. Normalize package naming for new code only.
   Pick `config` or `configuration`, then stop creating both. Do the same for websocket package placement.

---

Bottom line: the codebase already has a usable common/base split, but it is drifting because transport contracts, infrastructure adapters, and service-local client DTOs are not consistently separated. The safest improvement is not a rewrite. It is a boundary cleanup: one websocket envelope, one owner for integration event wrappers, fewer upward dependencies from common infrastructure modules, and clearer placement for client DTOs versus real shared contracts.
