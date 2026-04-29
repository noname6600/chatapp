# Foundation Structure Review

## 1. Foundation structure summary

The shared foundation is spread across seven modules:

- `common-core`
- `common-events`
- `common-kafka`
- `common-redis`
- `common-redis-cache`
- `common-web`
- `common-websocket`

At a high level, the intent is reasonable:

- `common-web`: API wrapper, exceptions, controller helper, trace filter, CORS properties
- `common-events`: cross-service payloads and enums
- `common-kafka`: Kafka publisher abstraction, topic constants, Kafka event wrappers
- `common-redis`: Redis Pub/Sub abstraction and serializer/dispatcher
- `common-redis-cache`: cache manager extension
- `common-websocket`: websocket handshake, broadcaster interfaces, outgoing message helpers
- `common-core`: generic pipeline helpers and upload metadata

The main structural issue is not missing abstractions. It is that the foundation is **inconsistently scoped**:

- some modules are true shared utilities
- some are transport libraries mixed with domain contracts
- some contain unused or half-abandoned base code
- some “common” classes already know service-specific concepts like chat errors or presence semantics

## 2. High-risk design problems

### 1. `common-web` contains service-specific error semantics

`common-web` should provide reusable exception mechanics, not a global enum that already knows chat and group-specific errors.

Evidence:
- [ErrorCode.java](D:/Work/PET/chatappPET/chatapp/chatappBE/common/common-web/src/main/java/com/example/common/web/exception/ErrorCode.java:22)
- includes `MESSAGE_NOT_FOUND`, `REPLY_MESSAGE_NOT_FOUND`, `MESSAGE_CONTENT_EMPTY`, `MESSAGE_DELETED`, `REACTION_INVALID`, `REACTION_NOT_ALLOWED`, `REMOVED_FROM_GROUP`, `BLOCKED_SEND`

Why this is risky:
- it makes `common-web` a hidden dependency of service domain vocabulary
- every new service-specific error pressures the shared enum to grow
- “common” becomes a dumping ground for business-adjacent codes

### 2. WebSocket foundation is split across two shared modules with two message models

There are two websocket message shapes:

- [WsOutgoingMessage.java](D:/Work/PET/chatappPET/chatapp/chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/dto/WsOutgoingMessage.java:13) with fields `type` + `data`
- [WsEvent.java](D:/Work/PET/chatappPET/chatapp/chatappBE/common/common-events/src/main/java/com/example/common/integration/websocket/WsEvent.java:10) with fields `type` + `payload`

Why this is risky:
- same concept, different modules, different field names
- services now choose different websocket “standards”
- `common-events` stops being purely cross-service integration contracts and starts carrying client transport models

### 3. `common-websocket` is not a neutral websocket abstraction; it is coupled to one auth strategy and one transport convention

Evidence:
- [common-websocket build.gradle](D:/Work/PET/chatappPET/chatapp/chatappBE/common/common-websocket/build.gradle:34) depends on websocket and OAuth2 resource server
- [AbstractJwtHandshakeInterceptor.java](D:/Work/PET/chatappPET/chatapp/chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/handshake/AbstractJwtHandshakeInterceptor.java:58) hardcodes `token` query parameter extraction
- same class also owns diagnostic headers, Micrometer metrics, and rejection semantics

Why this is risky:
- the module name suggests “websocket base”, but the implementation is specifically “JWT-over-query-param websocket handshake”
- changing websocket auth conventions now means changing a shared base module
- shared code is mixing transport, security, diagnostics, and observability

### 4. `common-kafka` mixes infrastructure abstraction with domain event catalog

Evidence:
- infrastructure API/publisher classes live beside domain-specific wrappers such as [ChatMessageSentEvent.java](D:/Work/PET/chatappPET/chatapp/chatappBE/common/common-kafka/src/main/java/com/example/common/kafka/event/ChatMessageSentEvent.java:1)
- topic catalog lives in [Topics.java](D:/Work/PET/chatappPET/chatapp/chatappBE/common/common-kafka/src/main/java/com/example/common/kafka/Topics.java:1)

Why this is risky:
- `common-kafka` is not just “Kafka plumbing”; it also owns shared business event contracts
- adding or evolving a domain event requires touching the transport module
- transport abstraction and integration contract evolution are coupled together

### 5. `common-web` is too broad as a module boundary

Evidence:
- [common-web build.gradle](D:/Work/PET/chatappPET/chatapp/chatappBE/common/common-web/build.gradle:41) includes `spring-webmvc`
- [common-web build.gradle](D:/Work/PET/chatappPET/chatapp/chatappBE/common/common-web/build.gradle:42) includes OAuth2 resource server
- [common-web build.gradle](D:/Work/PET/chatappPET/chatapp/chatappBE/common/common-web/build.gradle:46) includes OpenFeign
- the same module also contains response models, exception handler, base controller, filter, and CORS properties

Why this is risky:
- it merges HTTP API conventions, MVC support, security dependencies, and Feign infra into one “common” bucket
- services that only need response wrappers also inherit broader web/client concerns conceptually

### 6. Presence-specific concepts leaked into the websocket base package

Evidence:
- [IPresenceQuery.java](D:/Work/PET/chatappPET/chatapp/chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/session/IPresenceQuery.java:6)

Why this is risky:
- a supposedly generic websocket session package now knows about “presence”
- this is a domain concern, not a generic websocket primitive

### 7. The foundation contains dead or abandoned shared code

Evidence:
- commented shared registry: [WebSocketSessionRegistry.java](D:/Work/PET/chatappPET/chatapp/chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/session/WebSocketSessionRegistry.java:17)
- empty helper: [JsonMessageSender.java](D:/Work/PET/chatappPET/chatapp/chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/util/JsonMessageSender.java:3)
- commented cache config: [common-redis-cache RedisCacheConfig.java](D:/Work/PET/chatappPET/chatapp/chatappBE/common/common-redis-cache/src/main/java/com/example/common/redis/config/RedisCacheConfig.java:9)
- unused shared contracts: [NotificationEvent.java](D:/Work/PET/chatappPET/chatapp/chatappBE/common/common-events/src/main/java/com/example/common/integration/notification/NotificationEvent.java:15), [UserPresencePayload.java](D:/Work/PET/chatappPET/chatapp/chatappBE/common/common-events/src/main/java/com/example/common/integration/user/UserPresencePayload.java:13)

Why this is risky:
- dead foundation code creates false standards
- teams copy around it or work around it instead of trusting the base layer

### 8. Package and namespace boundaries are blurred between `common-redis` and `common-redis-cache`

Evidence:
- `common-redis-cache` classes live under `com.example.common.redis.*`
- example: [TimeRedisCacheManager.java](D:/Work/PET/chatappPET/chatapp/chatappBE/common/common-redis-cache/src/main/java/com/example/common/redis/core/TimeRedisCacheManager.java:1)

Why this is risky:
- two separate Gradle modules present themselves as one package tree
- ownership is harder to understand
- “redis pub/sub” and “redis cache” look like one module even though they are different concerns

### 9. Shared ObjectMapper base is only partially shared

Evidence:
- shared mapper: [common-core JacksonConfig.java](D:/Work/PET/chatappPET/chatapp/chatappBE/common/common-core/src/main/java/com/example/common/core/config/JacksonConfig.java:10)
- duplicated mapper configs exist in services:
  - [auth KafkaConfiguration.java](D:/Work/PET/chatappPET/chatapp/chatappBE/auth-service/src/main/java/com/example/auth/kafka/KafkaConfiguration.java:10)
  - [user KafkaConfiguration.java](D:/Work/PET/chatappPET/chatapp/chatappBE/user-service/src/main/java/com/example/user/kafka/KafkaConfiguration.java:10)
  - [chat KafkaConfiguration.java](D:/Work/PET/chatappPET/chatapp/chatappBE/chat-service/src/main/java/com/example/chat/modules/message/infrastructure/kafka/KafkaConfiguration.java:10)
  - [presence JacksonConfig.java](D:/Work/PET/chatappPET/chatapp/chatappBE/presence-service/src/main/java/com/example/presence/configuration/JacksonConfig.java:10)
  - [friendship JacksonConfig.java](D:/Work/PET/chatappPET/chatapp/chatappBE/friendship-service/src/main/java/com/example/friendship/configuration/JacksonConfig.java:8)
  - [notification KafkaConsumerConfig.java](D:/Work/PET/chatappPET/chatapp/chatappBE/notification-service/src/main/java/com/example/notification/kafka/KafkaConsumerConfig.java:24)

Why this is risky:
- the base serialization convention is not actually centralized
- shared payload modules depend on consistent serialization, but config is duplicated

## 3. Files/packages that should move

### Move out of `common-events`

- `common-events/src/main/java/com/example/common/integration/websocket/WsEvent.java`
  - move beside websocket transport code, not integration event contracts

- `common-events/src/main/java/com/example/common/integration/notification/NotificationEvent.java`
  - currently unused and duplicates notification-type ownership
  - either remove, or move to a real shared notification contract package if it becomes used

- `common-events/src/main/java/com/example/common/integration/user/UserPresencePayload.java`
  - currently unused
  - remove or relocate only if a real cross-service user-presence contract appears

### Move or rename inside websocket foundation

- `common-websocket/src/main/java/com/example/common/websocket/dto/WsOutgoingMessage.java`
  - move to `...websocket/message` or `...websocket/protocol`
  - align naming with `WsEvent`

- `common-websocket/src/main/java/com/example/common/websocket/session/IPresenceQuery.java`
  - move out of generic websocket session abstractions
  - it is presence-specific

- `common-websocket/src/main/java/com/example/common/websocket/handshake/*`
  - keep shared if needed, but move under a clearly scoped package like `websocket/security/jwt`

### Move or slim inside common web

- `common-web/src/main/java/com/example/common/web/config/FeignTraceConfig.java`
  - move out of generic `config`
  - place under `web/feign` or `web/observability`

- `common-web/src/main/java/com/example/common/web/exception/ErrorCode.java`
  - split shared HTTP/platform errors from service/domain-specific error codes

### Move or clean inside messaging foundation

- `common-kafka/src/main/java/com/example/common/kafka/event/*`
  - domain-specific wrappers should not be indistinguishable from pure Kafka infrastructure
  - keep them shared, but place them in a contract-oriented package, not the transport-core package

- `common-kafka/src/main/java/com/example/common/kafka/Topics.java`
  - shared topic names are valid common code, but they belong with shared messaging contracts, not with publisher plumbing

### Remove or archive

- `common-websocket/src/main/java/com/example/common/websocket/session/WebSocketSessionRegistry.java`
- `common-websocket/src/main/java/com/example/common/websocket/util/JsonMessageSender.java`
- `common-redis-cache/src/main/java/com/example/common/redis/config/RedisCacheConfig.java`

## 4. What should stay in common

These are good shared candidates and should remain in the foundation:

### Stable API/base web utilities
- [ApiResponse.java](D:/Work/PET/chatappPET/chatapp/chatappBE/common/common-web/src/main/java/com/example/common/web/response/ApiResponse.java:1)
- [ApiError.java](D:/Work/PET/chatappPET/chatapp/chatappBE/common/common-web/src/main/java/com/example/common/web/response/ApiError.java:1)
- [BusinessException.java](D:/Work/PET/chatappPET/chatapp/chatappBE/common/common-web/src/main/java/com/example/common/web/exception/BusinessException.java:1)
- [GlobalExceptionHandler.java](D:/Work/PET/chatappPET/chatapp/chatappBE/common/common-web/src/main/java/com/example/common/web/exception/GlobalExceptionHandler.java:1)
- [TraceIdFilter.java](D:/Work/PET/chatappPET/chatapp/chatappBE/common/common-web/src/main/java/com/example/common/web/filter/TraceIdFilter.java:1)
- [BaseController.java](D:/Work/PET/chatappPET/chatapp/chatappBE/common/common-web/src/main/java/com/example/common/web/controller/BaseController.java:1)
  - but only as a thin helper base

### Stable cross-service payload contracts
- `common-events/integration/account/*`
- `common-events/integration/chat/*`
- `common-events/integration/friendship/*`
- `common-events/integration/presence/*`
- `common-events/integration/enums/*`
- `NotificationRequestedPayload` can stay shared if it is the real cross-service notification request contract

### Generic messaging/runtime abstractions
- Kafka publisher interfaces and publisher implementation
- Redis message serializer, registry, publisher, listener, dispatcher
- cache manager extension if Redis cache fallback behavior is still wanted

### Generic reusable primitives
- `common-core/pipeline/*`
- `common-core/upload/UploadAssetMetadata.java`

## 5. What should be service-specific

These should not be promoted further into shared foundation:

### Service-specific domain semantics
- notification domain enum/types owned by `notification-service`
- chat-specific error names
- presence-specific session/query semantics

### Service-specific runtime config
- `SecurityConfig`
- `SwaggerConfig`
- `DatabaseSchemaFixer`
- service-local Kafka listener factories when they embed service-specific retry/dead-letter policy

### Service-local DTO copies for internal APIs
- if a service consumes another service’s API, the client model should be scoped as a client contract, not moved into generic common by default

### Service-specific websocket protocol commands
- inbound chat commands
- inbound presence commands
- friendship websocket payload formatting
- notification unread/update message shapes

## 6. Suggested target base/common structure

Keep the number of modules roughly the same. Clarify scope instead of expanding architecture.

### Suggested shared structure

```text
common-core
  pipeline/
  upload/
  json/

common-web
  response/
  exception/
  controller/
  filter/
  cors/
  feign/

common-contracts
  account/
  chat/
  friendship/
  notification/
  presence/
  messaging/
    kafka/
      topics/
      event/

common-kafka
  api/
  publisher/
  autoconfigure/
  observability/

common-redis
  pubsub/
    api/
    message/
    registry/
    serialization/
    listener/
    publisher/
    dispatcher/
  cache/
    api/
    core/

common-websocket
  protocol/
  broadcaster/
  session/
  security/
    jwt/
```

Notes:

- `common-events` is essentially a contracts module. Renaming conceptually to `common-contracts` would better match its real role.
- websocket protocol types should live together in one shared place, not split across `common-events` and `common-websocket`
- Kafka infra should not also look like the owner of all domain contracts
- Redis Pub/Sub and Redis cache are related technologies, but different concerns

## 7. Safe refactor order

1. Remove dead shared code first
   - delete or archive:
     - commented `WebSocketSessionRegistry`
     - empty `JsonMessageSender`
     - commented `common-redis-cache` config
     - unused `NotificationEvent`
     - unused `UserPresencePayload`

2. Unify websocket message contracts
   - choose one shared websocket envelope
   - move `WsEvent` and `WsOutgoingMessage` into one package
   - keep fields stable during the move

3. Split shared vs service-specific error codes
   - keep `BusinessException` and `GlobalExceptionHandler`
   - reduce `common` error codes to platform-level ones
   - let service-specific codes live in service modules

4. Clarify package ownership without changing runtime behavior
   - move `FeignTraceConfig` into a narrower package
   - move JWT handshake code under `websocket.security.jwt`
   - move `IPresenceQuery` out of generic websocket session abstractions

5. Separate messaging contracts from messaging plumbing
   - keep `common-kafka` publisher abstractions
   - relocate topic/event contract classes into the shared contract module namespace
   - do not change producer/consumer logic yet

6. Clean up Redis module boundaries
   - keep current behavior
   - make cache-vs-pubsub ownership explicit in package names
   - avoid two modules pretending to be one package tree

7. Normalize shared JSON/ObjectMapper ownership
   - decide whether shared ObjectMapper configuration truly belongs in common
   - if yes, consume it consistently
   - if no, keep it service-local and remove the illusion of a common default

