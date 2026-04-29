# Code Structure Review

## 1. Current structure summary

The backend has three different structural styles living side by side:

1. Flat package-by-type services
   - `auth-service`, `user-service`, `friendship-service`, `notification-service`, `presence-service`
   - Typical layout: `controller`, `dto`, `entity`, `repository`, `service`, plus transport-specific packages like `kafka`, `redis`, `websocket`

2. Partially layered / modular service
   - `chat-service`
   - `message` module has `application`, `domain`, `infrastructure`, `controller`, `event`
   - `room` module remains closer to package-by-type and does not match the `message` module structure
   - separate top-level `realtime` package exists beside `modules`

3. Small utility-style services
   - `upload-service`, `gateway-service`
   - `upload-service` has `domain`, `service`, `dto`, `config`
   - `gateway-service` is operationally focused: `config`, `controller`, `filter`, `health`

Common modules are also inconsistent:

- `common-kafka`: `api`, `config`, `core`, `event`, `observability`
- `common-redis`: `api`, `autoconfigure`, `dispatcher`, `listener`, `message`, `publisher`, `registry`
- `common-websocket`: `dto`, `handshake`, `session`, `util`
- `common-events`: transport-neutral integration payloads under `integration/*`

The result is a codebase where the package name does not always tell you whether a class is domain-facing, application-facing, or infrastructure-facing.

## 2. Problems

### High

#### 1. The codebase uses multiple incompatible structural conventions
- `chat-service` uses `application/domain/infrastructure` for `message`
- `room` inside the same service does not
- most other services use flat package-by-type
- this makes cross-service navigation and onboarding much harder

#### 2. Layering is inconsistent even within the same service
- `chat.modules.message` is layered
- `chat.modules.room` is not layered the same way
- `chat.realtime` sits outside the module structure entirely
- this creates unclear ownership boundaries between domain, application, and transport concerns

#### 3. Infrastructure concerns are mixed into service/application/event packages
- service classes often own cache, websocket push, kafka publish, or external client calls directly
- event factories and service classes sometimes perform writes or cross-service enrichment
- this weakens package meaning and makes refactoring risky

#### 4. DTO naming is inconsistent across transport boundaries
- `Request`, `Response`, `Payload`, `Command`, `Event`, and `OutgoingMessage` are used without a stable convention
- some `dto` packages contain API models, websocket commands, and event payloads together

### Medium

#### 5. `config` vs `configuration` is inconsistent across services
- `chat`, `gateway`, `upload` use `config`
- `auth`, `user`, `presence`, `friendship`, `notification` use `configuration`
- `friendship-service` uses both `config` and `configuration`

#### 6. Cross-service client DTOs are duplicated inside domain services
- services redefine foreign-service response models locally
- these copies live in generic `dto` packages, which makes them look like local API contracts

#### 7. Realtime support code is duplicated across services
- multiple nearly identical `SessionRegistry`, `WebSocketHandler`, and broadcaster patterns
- a commented shared registry exists in `common-websocket`, which suggests the duplication was known but left unresolved

#### 8. Common modules use different naming rules than services
- `common-redis` uses `autoconfigure`
- `common-kafka` uses `config`
- `common-websocket` uses `dto` while `common-events` uses `integration.websocket`

### Low

#### 9. Some packages are too broad to communicate intent
- `dto`
- `service`
- `utils`
- `kafka`
- `websocket`

#### 10. Some classes are structurally misplaced by name
- payload/event-facing classes live in `dto`
- infra-facing helpers live in `service`
- runtime-specific configuration objects live beside domain helpers in generic config packages

## 3. Concrete examples from code

### Structural inconsistency

- `chat-service` message module is layered:
  - `chatappBE/chat-service/src/main/java/com/example/chat/modules/message/application`
  - `chatappBE/chat-service/src/main/java/com/example/chat/modules/message/domain`
  - `chatappBE/chat-service/src/main/java/com/example/chat/modules/message/infrastructure`

- `chat-service` room module is not equivalently layered:
  - `chatappBE/chat-service/src/main/java/com/example/chat/modules/room/controller`
  - `chatappBE/chat-service/src/main/java/com/example/chat/modules/room/dto`
  - `chatappBE/chat-service/src/main/java/com/example/chat/modules/room/repository`
  - `chatappBE/chat-service/src/main/java/com/example/chat/modules/room/service`

- realtime code is split outside feature modules:
  - `chatappBE/chat-service/src/main/java/com/example/chat/realtime`

### Mixed responsibilities

- `UserProfileService` owns both domain/profile behavior and Redis cache plumbing:
  - [UserProfileService.java](D:/Work/PET/chatappPET/chatapp/chatappBE/user-service/src/main/java/com/example/user/service/impl/UserProfileService.java:42)
  - cache manager access and cache bootstrapping at lines 42-52

- `NotificationCommandService` mixes persistence-side command handling with websocket push triggering:
  - [NotificationCommandService.java](D:/Work/PET/chatappPET/chatapp/chatappBE/notification-service/src/main/java/com/example/notification/service/impl/NotificationCommandService.java:22)
  - push calls at lines 86, 105, 119, 126, 137

- `ChatMessagePayloadFactory` is in `event.factory` but performs cross-service enrichment and repository writes:
  - [ChatMessagePayloadFactory.java](D:/Work/PET/chatappPET/chatapp/chatappBE/chat-service/src/main/java/com/example/chat/modules/message/event/factory/ChatMessagePayloadFactory.java:33)
  - saves a member at line 73
  - calls `userClient.getUsersBulk` at line 118

- `UploadSigningService` is a service-layer class but directly owns Cloudinary provider details and signing:
  - [UploadSigningService.java](D:/Work/PET/chatappPET/chatapp/chatappBE/upload-service/src/main/java/com/example/upload/service/UploadSigningService.java:26)

- `FriendCommandService` mixes command orchestration, external user lookup, and event publication:
  - [FriendCommandService.java](D:/Work/PET/chatappPET/chatapp/chatappBE/friendship-service/src/main/java/com/example/friendship/service/impl/FriendCommandService.java:31)
  - uses `UserClient`
  - publishes via `producer.publish*`

### Naming inconsistency

- `config` and `configuration` both exist:
  - [chat `config`](D:/Work/PET/chatappPET/chatapp/chatappBE/chat-service/src/main/java/com/example/chat/config/WebSocketConfig.java:1)
  - [auth `configuration`](D:/Work/PET/chatappPET/chatapp/chatappBE/auth-service/src/main/java/com/example/auth/configuration/SecurityConfig.java:1)
  - [friendship both](D:/Work/PET/chatappPET/chatapp/chatappBE/friendship-service/src/main/java/com/example/friendship/config/FeignConfig.java:1) and [configuration](D:/Work/PET/chatappPET/chatapp/chatappBE/friendship-service/src/main/java/com/example/friendship/configuration/SecurityConfig.java:1)

- websocket naming is inconsistent:
  - [WsOutgoingMessage.java](D:/Work/PET/chatappPET/chatapp/chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/dto/WsOutgoingMessage.java:13) uses `data`
  - [WsEvent.java](D:/Work/PET/chatappPET/chatapp/chatappBE/common/common-events/src/main/java/com/example/common/integration/websocket/WsEvent.java:10) uses `payload`
  - [PresenceWsCommand.java](D:/Work/PET/chatappPET/chatapp/chatappBE/presence-service/src/main/java/com/example/presence/dto/PresenceWsCommand.java:11)
  - [WsIncomingMessage.java](D:/Work/PET/chatappPET/chatapp/chatappBE/chat-service/src/main/java/com/example/chat/realtime/websocket/WsIncomingMessage.java:10)

- `dto` packages mix API and event-ish shapes:
  - [RoomMessagePinEventPayload.java](D:/Work/PET/chatappPET/chatapp/chatappBE/chat-service/src/main/java/com/example/chat/modules/room/dto/RoomMessagePinEventPayload.java:1)
  - [RoomMemberJoinedPayload.java](D:/Work/PET/chatappPET/chatapp/chatappBE/chat-service/src/main/java/com/example/chat/modules/room/dto/RoomMemberJoinedPayload.java:1)
  - these sit beside request/response DTOs in the same package

- duplicated names appear in unrelated services:
  - [user/UserProfileResponse.java](D:/Work/PET/chatappPET/chatapp/chatappBE/user-service/src/main/java/com/example/user/dto/UserProfileResponse.java:14)
  - [friendship/UserProfileResponse.java](D:/Work/PET/chatappPET/chatapp/chatappBE/friendship-service/src/main/java/com/example/friendship/dto/UserProfileResponse.java:14)
  - [user/UpdateProfileRequest.java](D:/Work/PET/chatappPET/chatapp/chatappBE/user-service/src/main/java/com/example/user/dto/UpdateProfileRequest.java:10)
  - [friendship/UpdateProfileRequest.java](D:/Work/PET/chatappPET/chatapp/chatappBE/friendship-service/src/main/java/com/example/friendship/dto/UpdateProfileRequest.java:10)
  - [notification/UnreadCountResponse.java](D:/Work/PET/chatappPET/chatapp/chatappBE/notification-service/src/main/java/com/example/notification/dto/UnreadCountResponse.java:12)
  - [friendship/UnreadCountResponse.java](D:/Work/PET/chatappPET/chatapp/chatappBE/friendship-service/src/main/java/com/example/friendship/dto/UnreadCountResponse.java:12)

### Duplicated patterns

- nearly identical session registries:
  - [NotificationSessionRegistry.java](D:/Work/PET/chatappPET/chatapp/chatappBE/notification-service/src/main/java/com/example/notification/websocket/NotificationSessionRegistry.java:14)
  - [FriendshipSessionRegistry.java](D:/Work/PET/chatappPET/chatapp/chatappBE/friendship-service/src/main/java/com/example/friendship/websocket/FriendshipSessionRegistry.java:14)
  - [PresenceSessionRegistry.java](D:/Work/PET/chatappPET/chatapp/chatappBE/presence-service/src/main/java/com/example/presence/websocket/session/PresenceSessionRegistry.java:18)
  - [ChatSessionRegistry.java](D:/Work/PET/chatappPET/chatapp/chatappBE/chat-service/src/main/java/com/example/chat/realtime/websocket/session/ChatSessionRegistry.java:20)

- there is even a commented shared implementation:
  - [common `WebSocketSessionRegistry.java`](D:/Work/PET/chatappPET/chatapp/chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/session/WebSocketSessionRegistry.java:1)

- repeated config classes across services:
  - `SecurityConfig`
  - `SwaggerConfig`
  - `RedisCacheConfig`
  - `KafkaConfiguration` / `KafkaConsumerConfig`

## 4. Suggested target structure (standardized)

Do not rewrite service behavior. Standardize the package contract.

### Service-level standard

For business services with more than one capability:

```text
com.example.<service>
  config
  feature/
    <feature-name>/
      api/
        controller/
        request/
        response/
      application/
        service/
        command/
        query/
        mapper/
      domain/
        model/
        entity/
        valueobject/
        event/
        repository/
        service/
      infrastructure/
        persistence/
        messaging/
          kafka/
          redis/
        client/
        websocket/
        cache/
```

For small operational services like `gateway-service`:

```text
com.example.gateway
  config
  http
    controller
    filter
  health
```

### Naming standard

- use `config` everywhere, not `configuration`
- use `api.request` and `api.response` for HTTP models
- reserve `payload` for integration or transport payloads
- reserve `event` for domain or integration event envelopes
- reserve `command` for internal application commands
- reserve `dto` only if the team accepts it as a neutral transfer-model bucket; otherwise phase it out

### Shared conventions

- cross-service Feign models go under:
  - `infrastructure.client.<target>.model`
  - not in top-level `dto`

- websocket transport models go under:
  - `infrastructure.websocket.message`
  - not mixed with HTTP DTOs

- Redis/Kafka publishers and consumers go under:
  - `infrastructure.messaging.kafka`
  - `infrastructure.messaging.redis`

## 5. Migration plan WITHOUT breaking logic

1. Standardize package naming first
   - pick `config`
   - keep class names unchanged
   - move only packages, update imports

2. Split DTO packages by transport, not by implementation timing
   - create `api/request`, `api/response`
   - create `infrastructure/websocket/message`
   - create `infrastructure/client/<service>/model`
   - move files without changing fields or logic

3. Normalize one service at a time
   - start with `friendship-service` and `notification-service`
   - they are flatter and smaller than `chat-service`
   - use them as the standard for later package moves

4. Align `chat-service` internally
   - keep `message` structure
   - move `room` toward the same `application/domain/infrastructure/api` pattern
   - fold `realtime` under `infrastructure/websocket`

5. Move mixed-responsibility helpers to clearer packages
   - cache-aware services stay behaviorally unchanged, but move Redis-specific support into `infrastructure/cache`
   - websocket push helpers move into `infrastructure/websocket`
   - external-client enrichment helpers move into `infrastructure/client` or application orchestrators

6. Remove duplicate local copies gradually
   - keep classes first, only relocate them under clearer names
   - after package cleanup, deduplicate obvious copies like foreign-service response models

7. Consolidate shared websocket scaffolding
   - extract the repeated registry/broadcaster pattern into `common-websocket`
   - migrate one service at a time behind current interfaces
   - do not change websocket behavior while doing this

8. Add package-level rules to stop regression
   - feature packages own their API, application, domain, and infrastructure
   - no `dto` package at service root for new code
   - no new `configuration` package
   - no repository or client writes from `event.factory`

