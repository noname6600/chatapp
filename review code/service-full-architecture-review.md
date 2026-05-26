# Service Layer Full Architecture Review

## 1. Scope Reviewed

Reviewed service/app modules under `chatappBE` and outside `chatappBE/common/**`:

- `chatappBE/auth-service`
- `chatappBE/user-service`
- `chatappBE/chat-service`
- `chatappBE/presence-service`
- `chatappBE/notification-service`
- `chatappBE/friendship-service`
- `chatappBE/upload-service`
- `chatappBE/gateway-service`

Explicit exclusions applied:

- `chatappBE/common/**` implementation and redesign
- Frontend code
- Deployment/infrastructure except gateway routing and build/module wiring where needed to understand service boundaries
- Database schema except entities/repositories directly owned or used by the services
- UI/client-side behavior

Validation performed:

- Inspected `chatappBE/settings.gradle` and service `build.gradle` files.
- Ran `.\gradlew.bat projects` from `chatappBE`; discovered the eight service modules above plus common modules.
- Ran service compile discovery with:
  - `.\gradlew.bat :presence-service:compileJava --console=plain`
  - `.\gradlew.bat :auth-service:compileJava :user-service:compileJava :chat-service:compileJava :friendship-service:compileJava :presence-service:compileJava :notification-service:compileJava :upload-service:compileJava :gateway-service:compileJava --continue --console=plain`
- `upload-service` and `gateway-service` compiled or were up to date.
- `auth-service`, `user-service`, `chat-service`, `friendship-service`, `presence-service`, and `notification-service` failed compilation, mostly because service code imports older shared Kafka, Redis, and websocket APIs that are no longer available from the current shared foundation. This review treats those as service alignment and boundary problems only; common-layer redesign remains out of scope.

Boundary limits:

- Common-layer classes are referenced only as dependencies used by service code.
- Findings are limited to service module organization, service ownership, service transport adapters, websocket ownership, and cross-service integration.

## 2. Service Inventory

### `auth-service`

Current purpose:

- Owns account registration/login, JWT/token behavior, OAuth login, email/Resend integration, key management, and account-created publishing.

Actual ownership:

- `controller/AuthController` exposes authentication endpoints.
- `service/AuthService` acts as a facade over local auth, OAuth auth, token handling, email verification, and readiness polling.
- `jwt/*` owns signing/verification/key lifecycle details.
- `integration/resend/*` owns email delivery integration.
- `kafka/AccountCreatedEventProducer` publishes account-created events.
- `service/impl/UserProfileReadinessService` calls `user-service` through `RestClient` and sleeps/retries after registration.

Recommended ownership:

- Own identity, credentials, token lifecycle, OAuth provider integration, email verification, and account-created domain event publication.
- Expose account/auth read APIs needed by other services through explicit contracts.

Should not own:

- User profile readiness polling or blocking coordination with profile creation.
- User profile creation semantics.
- Service-specific knowledge of `user-service` timing.

Overlap/confusion:

- `UserProfileReadinessService` makes `auth-service` partially responsible for profile lifecycle readiness, which belongs to user/profile orchestration.
- `AccountCreatedEventProducer` uses shared event types that no longer compile, showing the service's event contract is not stable.
- `RestClient` usage here differs from Feign-style clients used elsewhere, weakening integration consistency.

### `user-service`

Current purpose:

- Owns user profile records, profile search, cached lookup, avatar metadata application, and profile creation from account-created events.

Actual ownership:

- `controller/UserProfileController` exposes profile APIs.
- `service/impl/UserProfileService` owns profile CRUD/search/avatar update behavior.
- `kafka/AccountCreatedConsumer` creates profiles directly from account-created events.
- `utils/AvatarGenerator` creates generated avatar defaults.
- `service/impl/CloudinaryService` exists but appears unused.
- Build file has `implementation project(':upload-service')` to import `com.example.upload.contract.UploadAssetMetadata`.

Recommended ownership:

- Own user profile state, generated defaults, profile search/read APIs, and applying upload metadata to profile avatar fields.
- Consume account-created events through an application use case.

Should not own:

- Upload signing or Cloudinary SDK behavior.
- Compile-time dependency on `upload-service`.
- Event consumer business logic directly in Kafka adapter classes.

Overlap/confusion:

- `UploadAssetMetadata` imported from `upload-service` creates a service-to-service module dependency. That makes `upload-service` behave like a library, which is the wrong direction for a deployable service boundary.
- `service/impl/CloudinaryService` duplicates upload/storage concerns and should be removed if unused.

### `chat-service`

Current purpose:

- Owns chat rooms, room memberships, messages, attachments, reactions, pins, read state, websocket chat endpoint, Redis fanout, Kafka chat event production/consumption, avatar upload support for rooms, and cross-service block/user lookups.

Actual ownership:

- `modules/message/*` is organized around message domain/application/infrastructure with send/edit/delete/reaction pipelines.
- `modules/room/*` owns room lifecycle, membership, bans, avatar updates, read state, and system messages.
- `realtime/*` owns websocket endpoint, session registry, broadcasters, and websocket frame handling.
- `config/WebSocketConfig` registers `/ws/chat`.
- `modules/message/infrastructure/kafka/*` publishes and consumes chat/reaction Kafka events.
- `modules/message/infrastructure/redis/*` converts Redis events to websocket broadcasts.
- `modules/room/service/impl/CloudinaryService` and `GroupAvatarGenerator` own room avatar/storage behavior.

Recommended ownership:

- Own chat business capabilities: rooms, memberships, messages, reactions, pins, read state, and chat domain events.
- Publish chat events for downstream notification/realtime consumers.
- Keep room and message capabilities inside the same service only if boundaries are explicit and package structure preserves separate application/domain modules.

Should not own:

- Browser websocket session management in the target architecture.
- Generic realtime frame, subscription, dedupe, and fanout concerns.
- Cloudinary/upload mechanics that are already a separate upload capability.
- Event payload factories that perform persistence side effects or remote service lookups.

Overlap/confusion:

- `realtime/websocket/*` makes `chat-service` both a chat domain service and a realtime edge service.
- `ChatWebSocketHandler` accepts chat commands over websocket and invokes application services directly, duplicating REST/application orchestration paths.
- `RoomService` is very broad and mixes room lifecycle, membership, avatar upload, read state, realtime publication, cache invalidation, and external user lookup.
- `ChatMessagePayloadFactory` performs user-client lookups and may create/save `RoomMember` records while building event payloads; that is misplaced side-effectful orchestration.

### `presence-service`

Current purpose:

- Owns user online/offline state, heartbeat, room presence, typing state, presence websocket endpoint, Redis state, and Redis fanout.

Actual ownership:

- `service/PresenceService` owns presence transitions and publishes presence realtime events.
- `state/port` and `state/redis` separate presence state persistence behind ports.
- `websocket/PresenceWebSocketHandler` registers sessions and handles heartbeat, room join/leave, typing, and stop-typing frames.
- `redis/*` contains Redis pub/sub subscribers, key expiration listeners, and websocket broadcast adapters.
- `configuration/WebSocketConfig` registers `/ws/presence`.

Recommended ownership:

- Own presence domain state and presence events: online/offline, heartbeat, room presence, typing.
- Keep state storage behind ports.
- Publish presence events to the realtime delivery path.

Should not own:

- Browser websocket session registry, handshake, generic room/user broadcasters, or global websocket fanout in the target architecture.
- Transport-level command parsing mixed with state transition logic.

Overlap/confusion:

- `PresenceWebSocketHandler` is both transport adapter and application orchestrator.
- `redis/*` and `state/redis/*` split Redis concerns inconsistently: one package is persistence state, the other is pub/sub and expiration handling.
- Compile failures in `PresenceRedisListenerConfig` show service adapters are coupled to obsolete shared Redis listener APIs.

### `notification-service`

Current purpose:

- Owns persisted notifications, unread counts, notification preferences, Kafka consumers for notification-generating events, websocket notification endpoint, and Redis/websocket fanout.

Actual ownership:

- `service/impl/NotificationCommandService` creates notifications, persists them, and immediately pushes realtime updates.
- `service/impl/NotificationDomainService` creates notification entities and publishes notification-requested events.
- `kafka/*` consumers classify chat/friend/account events and create notifications.
- `websocket/NotificationWebSocketHandler` registers/unregisters sessions.
- `websocket/WebSocketUserBroadcaster` sends websocket messages.
- `websocket/redis/*` uses raw Redis pub/sub and JSON parsing for notification fanout.
- `controller/RoomMuteController` maps under both `/api/v1/rooms` and `/api/v1/notifications/rooms`.

Recommended ownership:

- Own notification preferences, notification creation rules, persisted notification state, read/unread state, and notification domain events.
- Consume domain events through application use cases.

Should not own:

- Browser websocket session management and raw websocket delivery in the target architecture.
- `/api/v1/rooms` URL ownership, which collides with `chat-service`.
- Multiple competing event paths for the same notification creation lifecycle.

Overlap/confusion:

- `MessageCreatedEventConsumer`, `ReactionEventConsumer`, `FriendRequestEventConsumer`, and `AccountCreatedEventConsumer` contain business classification/idempotency and should be thin adapters delegating to application use cases.
- `ChatMessageEventConsumer` logs/ignores a chat message event that another consumer handles, making the event topology unclear.
- `RoomMuteController` exposes notification preferences through a chat-like route prefix and can conflict with gateway route ownership.
- Notification Redis fanout uses a custom raw channel style while chat/presence use typed shared Redis event abstractions.

### `friendship-service`

Current purpose:

- Owns friend requests, friendships, block state, internal block checks, event publication, Kafka-to-websocket push, and friendship websocket endpoint.

Actual ownership:

- `service/impl/FriendCommandService` owns relationship commands and rules.
- `controller/FriendController` exposes friendship APIs.
- `controller/InternalFriendController` exposes block check API used by chat.
- `client/UserClient` enriches friendship behavior with user data.
- `kafka/FriendshipEventProducer` publishes friendship events and performs display-name enrichment.
- `kafka/FriendshipEventConsumer` and `FriendshipRequestEventConsumer` consume events and push websocket updates.
- `websocket/*` and `realtime/*` own endpoint/session/broadcaster/publisher behavior.
- `configuration/FriendshipWebSocketConfig` registers `/ws/friendship`.

Recommended ownership:

- Own relationship graph, friend request lifecycle, block/unblock rules, and block-check APIs.
- Publish friendship domain events.
- Provide clean query APIs for other services that need relationship/block state.

Should not own:

- Browser websocket endpoint and local session fanout in the target architecture.
- Event producer display-name enrichment logic.
- Cross-instance realtime delivery mechanics.

Overlap/confusion:

- Friendship has direct local websocket fanout but no Redis fanout path like chat/presence/notification, so multi-instance behavior is inconsistent.
- `FriendshipWebSocketPublisher` contains large event-to-frame mapping logic that belongs in a realtime adapter or realtime edge, not near relationship domain rules.

### `upload-service`

Current purpose:

- Owns upload signing, upload policy validation, upload confirmation, and upload metadata contracts.

Actual ownership:

- `controller/UploadController` exposes `/api/v1/uploads`.
- `service/UploadSigningService` prepares and confirms uploads.
- `domain/UploadPolicy` and `application/UploadPolicyRegistry` define policy behavior.
- `contract/UploadAssetMetadata` is imported by `user-service`.

Recommended ownership:

- Own upload policy, signing, confirmation, and storage-provider interaction.
- Return upload metadata through APIs/events without forcing other services to depend on this service module.

Should not own:

- Consumer-facing shared DTOs as a compile-time library for other services.
- User profile mutation.

Overlap/confusion:

- `UploadAssetMetadata` is treated like a shared library type by `user-service`. That should be an API DTO copied at the boundary, generated client contract, or stable shared contract outside the deployable service module.
- `UploadController.currentUserId()` uses `Authentication.getName()` while many other services parse JWTs directly, showing security principal propagation is not standardized.

### `gateway-service`

Current purpose:

- Owns edge routing, JWT propagation, CORS, rate limiting, and websocket route forwarding.

Actual ownership:

- `config/SecurityConfig` permits `/ws/**` and relies on downstream services to authenticate websocket handshakes.
- `filter/JwtAuthFilterGatewayFilterFactory` validates JWT and forwards `X-User-Id`.
- `application.yaml` defines REST and websocket routes for all services.

Recommended ownership:

- Own HTTP/websocket edge routing, coarse security, propagated identity headers, rate limits, and gateway health.
- Route to service APIs using stable path ownership.

Should not own:

- Domain logic.
- Compensation for inconsistent service route naming.

Overlap/confusion:

- Gateway forwards `X-User-Id`, but many services still parse JWTs directly, so identity propagation is not consistently adopted.
- Gateway has websocket routes to four downstream services, reinforcing fragmented realtime ownership.
- Gateway route predicates are inconsistent with service controller paths:
  - `friendship-service` controller uses `/api/v1/friends`, while gateway routes use `/api/v1/friendship/**` and rewrite paths.
  - `upload-service` controller uses `/api/v1/uploads`, while gateway routes use `/api/v1/upload/**` and rewrite paths.
  - `notification-service` exposes room notification settings under `/api/v1/rooms`, overlapping the chat route namespace.

## 3. Current Structure Assessment

The services are not organized around one consistent architectural style.

Broad structural patterns:

- `chat-service` partially uses a capability/module layout with `modules/message` and `modules/room`, then mixes in a top-level `realtime` transport area.
- `upload-service` uses a compact `application`, `domain`, `controller`, `service`, `config`, and `contract` layout.
- `presence-service` has a useful port/adapters split for state under `state/port` and `state/redis`, but websocket and pub/sub are top-level transport packages.
- `auth-service`, `user-service`, `friendship-service`, and `notification-service` mostly use technical top-level folders such as `controller`, `service`, `service.impl`, `repository`, `entity`, `kafka`, `websocket`, `configuration`.
- `gateway-service` is appropriately small and technical because it is an edge app, not a domain service.

Inconsistencies:

- Configuration package names alternate between `config` and `configuration`.
- Application services use mixed naming: `AuthService`, `UserProfileService`, `FriendCommandService`, `NotificationCommandService`, `PresenceService`, `RoomService`, `MessageCommandService`.
- Interface style is inconsistent: chat uses `IMessageCommandService`, `IRoomService`, `IReactionCommandService`; other services generally do not use `I` prefixes.
- Kafka adapters are top-level `kafka` in most services but under `modules/message/infrastructure/kafka` in chat.
- Redis adapters are under `modules/message/infrastructure/redis` in chat, top-level `redis` in presence, and `websocket/redis` in notification.
- Websocket adapters are under `realtime/websocket` in chat, top-level `websocket` in presence/friendship/notification, and mixed with `realtime/port` in several services.
- DTOs are top-level in most services, nested under capability packages in chat, and exposed as a `contract` package in upload.
- External HTTP clients are split across `client`, `integration`, `UserClient`, `FriendshipClient`, `RestClient`, and Feign-style clients.

Weak package messages:

- `service.impl` says "implementation detail" but currently contains important application orchestration and business rules.
- `kafka` often contains both transport integration and business decisions.
- `websocket` packages contain transport, session state, fanout, metrics, and domain command invocation.
- `realtime` is used inconsistently. Sometimes it means a port, sometimes websocket, sometimes Redis fanout, and sometimes flow semantics.
- `contract` inside `upload-service` makes a deployable service double as a shared library.

Cleaner direction:

- Capability-first services should use `domain`, `application`, and `adapter` boundaries per capability.
- Transport adapters should live under `adapter/in/rest`, `adapter/in/kafka`, `adapter/in/websocket` while they still exist.
- Persistence, Kafka publishers, Redis publishers, HTTP clients, and storage providers should live under `adapter/out/*`.
- `service.impl` should be replaced gradually by explicit `application/command`, `application/query`, and `domain/service`.
- Realtime browser-facing packages should be removed from business services after a realtime-edge is introduced.

## 4. WebSocket Architecture Review

### Current websocket ownership model

Four business services expose their own websocket endpoints:

- `chat-service`: `config/WebSocketConfig` registers `/ws/chat`; `realtime/websocket/ChatWebSocketHandler`.
- `presence-service`: `configuration/WebSocketConfig` registers `/ws/presence`; `websocket/PresenceWebSocketHandler`.
- `friendship-service`: `configuration/FriendshipWebSocketConfig` registers `/ws/friendship`; `websocket/FriendshipWebSocketHandler`.
- `notification-service`: `configuration/WebSocketConfig` registers `/ws/notifications`; `websocket/NotificationWebSocketHandler`.

`gateway-service` routes all four websocket endpoint families:

- `/ws/chat/**`
- `/ws/presence/**`
- `/ws/friendship/**`
- `/ws/notifications/**`

`gateway-service/config/SecurityConfig` permits `/ws/**`, so each downstream service owns its own websocket handshake/auth behavior.

### Duplicated websocket logic

Duplicated endpoint registration:

- `chat-service/config/WebSocketConfig`
- `presence-service/configuration/WebSocketConfig`
- `friendship-service/configuration/FriendshipWebSocketConfig`
- `notification-service/configuration/WebSocketConfig`

Duplicated session registries:

- `chat-service/realtime/websocket/session/ChatSessionRegistry`
- `presence-service/websocket/session/PresenceSessionRegistry`
- `friendship-service/websocket/FriendshipSessionRegistry`
- `notification-service/websocket/NotificationSessionRegistry`

Repeated registry responsibilities:

- Map websocket session id to user id.
- Map user id to sessions.
- Register/unregister sessions.
- Close or remove stale sessions.
- Resolve sessions by user.
- In chat and presence, track room-to-session and session-to-room membership.
- Read user id from `AbstractJwtHandshakeInterceptor.ATTR_USER_ID`.

Duplicated broadcasters:

- `chat-service/realtime/websocket/broadcast/WebSocketUserBroadcaster`
- `chat-service/realtime/websocket/broadcast/WebSocketRoomBroadcaster`
- `presence-service/websocket/broadcaster/WebSocketUserBroadcaster`
- `presence-service/websocket/broadcaster/WebSocketRoomBroadcaster`
- `presence-service/websocket/broadcaster/WebSocketGlobalBroadcaster`
- `notification-service/websocket/WebSocketUserBroadcaster`
- `friendship-service/websocket/WebSocketFriendshipBroadcaster`

Repeated broadcaster responsibilities:

- Find sessions from a local registry.
- Build or pass through websocket event payloads.
- Send text frames.
- Handle closed sessions and send failures.

Duplicated handler lifecycle:

- `ChatWebSocketHandler.afterConnectionEstablished/afterConnectionClosed`
- `PresenceWebSocketHandler.afterConnectionEstablished/afterConnectionClosed`
- `FriendshipWebSocketHandler.afterConnectionEstablished/afterConnectionClosed`
- `NotificationWebSocketHandler.afterConnectionEstablished/afterConnectionClosed`

Duplicated protocol and delivery responsibilities:

- Handshake/auth dependency on old shared websocket classes.
- Session registration and cleanup.
- User-targeted fanout.
- Room-targeted fanout.
- Event naming and frame mapping.
- Metrics and logging.
- Redis-to-websocket fanout in several services.
- Partial flow semantics using `RealtimeFlowId` and `RealtimeFlowType`, with many TODO/direct-fanout implementations.

### Architectural problems

1. Websocket ownership is fragmented across too many business services.

Each service owns its own client connection lifecycle. This creates four independent realtime edges with duplicated handshake, session, metrics, and fanout code. The architecture scales operational complexity linearly with every new realtime feature.

2. Business services mix domain ownership with realtime edge ownership.

- `ChatWebSocketHandler` parses websocket commands and calls `IMessageCommandService` and `IReactionCommandService`.
- `PresenceWebSocketHandler` parses heartbeat/room/typing commands and calls `PresenceService` plus `PresenceRealtimePort`.
- `FriendshipWebSocketHandler` and `NotificationWebSocketHandler` are thinner, but still own local session lifecycles.

This makes service APIs partly REST, partly websocket-command based, and partly event-driven.

3. Cross-instance delivery is inconsistent.

- Chat uses Kafka plus Redis subscribers and a dedupe guard for websocket fanout.
- Presence uses Redis pub/sub subscribers for presence fanout.
- Notification uses custom raw Redis pub/sub under `websocket/redis`.
- Friendship consumes Kafka and directly pushes to local sessions with no equivalent Redis fanout path.

The same user connected to a different instance can receive different behavior depending on service.

4. Flow semantics are declared but not enforced consistently.

- `ChatRealtimeAdapter` branches on `RealtimeFlowType`, but durable and mixed paths are TODOs and usually call direct fanout.
- `NotificationWebSocketPublisher` has TODO branches and sends through Redis regardless.
- `FriendshipWebSocketPublisher` has TODO branches and directly broadcasts.

This makes `RealtimeFlowId` look architectural while still being a local implementation detail.

5. Websocket registries are fragile.

The registries repeatedly depend on handshake attributes and often insert values into `ConcurrentHashMap` without a clear null guard at the registry boundary. If handshake identity is missing or changed by the common layer, multiple services fail in the same way.

6. Inbound websocket commands bypass clearer API boundaries.

`ChatWebSocketHandler` handles `SEND`, `EDIT`, `DELETE`, and `REACTION`. `PresenceWebSocketHandler` handles heartbeat, room join/leave, and typing. These handlers are transport adapters doing orchestration that should either be REST/application use cases or centralized realtime-edge command routing.

### Recommended websocket ownership model

Adopt a dedicated realtime-edge ownership model.

Recommended target:

- One `realtime-edge-service` owns browser websocket connections.
- Gateway routes browser websocket traffic only to the realtime edge.
- Business services do not expose browser websocket endpoints.
- Business services publish domain/realtime events to Kafka and/or Redis.
- Realtime edge consumes those events and pushes frames to connected clients.
- Realtime edge owns:
  - Handshake/auth/session lifecycle.
  - User/session registry.
  - Room/channel subscriptions.
  - Ping/pong, heartbeat, idle timeout, connection metrics.
  - Frame envelope and outbound protocol versioning.
  - Delivery dedupe.
  - Cross-instance fanout.
  - Backpressure and send-failure handling.
  - User, room, and global broadcaster implementations.

Alternative if a new service is deferred:

- Create one centralized realtime module inside the service layer and migrate all websocket endpoint behavior behind a single shared adapter pattern.
- This is weaker than a dedicated realtime-edge because browser connections remain spread across services, but it can be an interim step.

### What should be centralized

Centralize in realtime edge or a single realtime adapter layer:

- Websocket endpoint registration.
- JWT handshake and identity extraction.
- Session registry and cleanup.
- User/session and room/session subscription maps.
- Outbound frame envelope.
- Error frame format.
- Heartbeat/ping/pong.
- User, room, and global fanout.
- Cross-instance fanout strategy.
- Dedupe/idempotency of pushed frames.
- Metrics for active connections, send failures, and dropped frames.
- Subscription authorization calls into domain services.
- Mapping from domain events to client websocket event names where the mapping is purely presentation/protocol.

### What should remain service-local

Keep these inside business services:

- Domain rules:
  - Chat membership, room permissions, block checks.
  - Presence state transitions and typing rules.
  - Friendship request/block rules.
  - Notification creation and mute/preference rules.
- Application use cases:
  - Send/edit/delete message.
  - Join/leave room as a domain action, if distinct from websocket subscription.
  - Mark notification read.
  - Create/accept/reject friend request.
  - Update presence/heartbeat.
- Domain event creation.
- Persistence and cache invalidation for owned state.
- Service-specific authorization decisions exposed through APIs/events.

### What should be deleted after migration

After realtime edge cutover:

- `chat-service/realtime/websocket/*`
- `presence-service/websocket/*`
- `friendship-service/websocket/*`
- `notification-service/websocket/*`
- Service-local websocket `WebSocketConfig` classes.
- Service-local broadcaster classes.
- Service-local session registries.
- Notification custom raw Redis websocket bridge if replaced by the common event delivery path.
- Chat/presence/friendship/notification direct browser websocket routes in `gateway-service`.

## 5. Problems

### High

#### H1. Business services still depend on obsolete shared event/websocket/Redis APIs

Evidence:

- `auth-service/kafka/AccountCreatedEventProducer`
- `user-service/kafka/AccountCreatedConsumer`
- `chat-service/modules/message/infrastructure/kafka/*`
- `chat-service/realtime/websocket/*`
- `presence-service/configuration/PresenceRedisListenerConfig`
- `presence-service/websocket/*`
- `notification-service/kafka/*`
- `friendship-service/kafka/*`

Why it is a problem:

- Several service modules fail compilation because they import old shared classes such as `com.example.common.integration.kafka.event.*`, `com.example.common.kafka.api.KafkaEvent`, old `KafkaTopics` constants, old websocket handshake/protocol/broadcaster/session types, and old Redis listener/message abstractions.

Impact:

- Service-layer architecture cannot be stabilized while service adapters target incompatible shared contracts.
- This blocks reliable tests and makes any refactor risky.

Recommended fix:

- Freeze service contract changes until all services compile against the current shared foundation.
- Align each service adapter to the current common APIs without redesigning common in this review.
- Remove compatibility-style imports from services once the new event and realtime boundaries are defined.

#### H2. Four business services own independent browser websocket endpoints

Evidence:

- `chat-service/config/WebSocketConfig`
- `presence-service/configuration/WebSocketConfig`
- `friendship-service/configuration/FriendshipWebSocketConfig`
- `notification-service/configuration/WebSocketConfig`
- `gateway-service/src/main/resources/application.yaml`

Why it is a problem:

- Chat, presence, friendship, and notification are domain services, but each acts as a realtime edge.
- Handshake, session registry, broadcaster, and delivery patterns are duplicated.

Impact:

- High operational complexity.
- Inconsistent auth/session behavior.
- Hard multi-instance delivery.
- Harder client protocol evolution.

Recommended fix:

- Introduce a dedicated realtime-edge ownership model.
- Business services should publish events and expose command/query APIs; realtime edge should own browser websocket connections.

#### H3. Websocket handlers contain application orchestration

Evidence:

- `chat-service/realtime/websocket/ChatWebSocketHandler`
- `presence-service/websocket/PresenceWebSocketHandler`

Why it is a problem:

- `ChatWebSocketHandler` handles `JOIN`, `LEAVE`, `SEND`, `EDIT`, `DELETE`, and `REACTION` frames and calls command services directly.
- `PresenceWebSocketHandler` handles heartbeat, room join/leave, typing, and stop-typing frames and calls services/ports directly.
- Transport parsing and application orchestration are coupled.

Impact:

- Business behavior can diverge between REST, websocket, Kafka, and internal service paths.
- Testing application behavior requires websocket transport setup.
- Moving to a centralized realtime edge becomes harder.

Recommended fix:

- Move command handling into application use cases.
- Let websocket adapters be thin translators only while they still exist.
- In the target model, realtime edge either calls service APIs for commands or clients use REST for commands and websockets only for updates.

#### H4. Cross-instance realtime delivery is inconsistent

Evidence:

- `chat-service/modules/message/infrastructure/redis/*Subscriber`
- `presence-service/redis/*Subscriber`
- `notification-service/websocket/redis/*`
- `friendship-service/kafka/FriendshipEventConsumer`
- `friendship-service/kafka/FriendshipRequestEventConsumer`
- `friendship-service/websocket/FriendshipWebSocketPublisher`

Why it is a problem:

- Chat and presence use Redis subscriber fanout.
- Notification uses custom raw Redis pub/sub.
- Friendship pushes directly to local websocket sessions after Kafka consumption.

Impact:

- Multi-instance behavior differs by service.
- Users connected to different instances may miss friendship updates.
- Delivery semantics cannot be reasoned about uniformly.

Recommended fix:

- Centralize fanout in realtime edge.
- Until then, make all realtime services use one consistent cross-instance delivery mechanism and one dedupe strategy.

#### H5. `user-service` has a compile-time dependency on `upload-service`

Evidence:

- `chatappBE/user-service/build.gradle`: `implementation project(':upload-service')`
- `user-service/service/impl/UserProfileService`
- `upload-service/contract/UploadAssetMetadata`

Why it is a problem:

- A deployable service module is used as a shared library.
- This violates service independence and creates accidental coupling between profile and upload implementation.

Impact:

- `upload-service` cannot evolve independently.
- Service builds are coupled for a DTO.
- Ownership of upload metadata contract is unclear.

Recommended fix:

- Replace the module dependency with an API boundary:
  - User service accepts its own request DTO for avatar metadata.
  - Upload service returns metadata through HTTP/event payloads.
  - If a shared type is truly required, place only the stable contract in an appropriate shared contract module, not inside the deployable upload service.

#### H6. Chat room service is too broad

Evidence:

- `chat-service/modules/room/service/impl/RoomService`

Why it is a problem:

- `RoomService` owns room creation, join/leave, rename, add/remove members, ban/unban, ownership transfer, read state, last message updates, avatar upload, system messages, realtime publishing, cache invalidation, and external user lookups.

Impact:

- Difficult to test and change safely.
- Boundaries between room membership, moderation, read state, and room metadata are unclear.
- Realtime and upload responsibilities leak into core chat application logic.

Recommended fix:

- Split by use-case groups under `modules/room/application`:
  - Room lifecycle commands.
  - Membership commands.
  - Moderation commands.
  - Read-state commands.
  - Room metadata/avatar commands.
- Keep persistence and external integrations behind output adapters.

#### H7. Notification route ownership overlaps chat route ownership

Evidence:

- `notification-service/controller/RoomMuteController`
- `chat-service/modules/room/controller/RoomController`
- `gateway-service/src/main/resources/application.yaml`

Why it is a problem:

- `RoomMuteController` maps to both `/api/v1/rooms` and `/api/v1/notifications/rooms`.
- `chat-service` owns room routes under `/api/v1/rooms`.
- Gateway routes `/api/v1/rooms/**` to chat.

Impact:

- Notification room preferences are either unreachable through the intended gateway path or collide with chat service ownership.
- API ownership is ambiguous.

Recommended fix:

- Keep notification preferences under `/api/v1/notifications/rooms/**`.
- Remove the notification-service `/api/v1/rooms` mapping after clients migrate.

### Medium

#### M1. Event consumers contain business logic instead of delegating to application use cases

Evidence:

- `notification-service/kafka/MessageCreatedEventConsumer`
- `notification-service/kafka/ReactionEventConsumer`
- `notification-service/kafka/FriendRequestEventConsumer`
- `notification-service/kafka/AccountCreatedEventConsumer`
- `user-service/kafka/AccountCreatedConsumer`
- `friendship-service/kafka/FriendshipEventConsumer`
- `friendship-service/kafka/FriendshipRequestEventConsumer`

Why it is a problem:

- Kafka adapters classify, enrich, dedupe, or mutate state directly.
- Application behavior becomes tied to Kafka transport.

Impact:

- Same behavior cannot be reused from REST/internal APIs.
- Tests and retries become transport-specific.

Recommended fix:

- Keep consumers thin.
- Convert incoming event to an application command and call an application use case.
- Keep idempotency rules in application services or explicit inbox/idempotency components.

#### M2. Event payload factory performs persistence and remote lookups

Evidence:

- `chat-service/modules/message/infrastructure/event/ChatMessagePayloadFactory`

Why it is a problem:

- A factory/mapper should not call `UserClient` or create/save `RoomMember` records while building payloads.

Impact:

- Event publication has hidden side effects.
- Payload mapping can mutate room membership.
- Failures in user lookup or persistence can affect event creation in surprising ways.

Recommended fix:

- Move enrichment and membership repair into explicit application orchestration.
- Keep payload factories pure or nearly pure.
- Pass already-loaded/enriched data into the factory.

#### M3. Realtime flow abstractions are present but not implemented consistently

Evidence:

- `chat-service/realtime/ChatRealtimeAdapter`
- `notification-service/realtime/NotificationWebSocketPublisher`
- `friendship-service/realtime/FriendshipWebSocketPublisher`

Why it is a problem:

- `RealtimeFlowId` and `RealtimeFlowType` imply direct, durable, or mixed delivery, but several branches are TODOs and fall back to direct fanout.

Impact:

- Callers cannot rely on declared delivery semantics.
- The code communicates stronger guarantees than it provides.

Recommended fix:

- Either implement the flow semantics centrally or remove the abstraction from service-local code until the realtime edge owns it.

#### M4. Gateway identity propagation is not consistently used by services

Evidence:

- `gateway-service/filter/JwtAuthFilterGatewayFilterFactory`
- `chat-service/modules/room/controller/RoomController`
- `upload-service/controller/UploadController`
- Other service controllers using mixed JWT/principal extraction patterns

Why it is a problem:

- Gateway forwards `X-User-Id`, but services still parse JWTs or use principal names differently.

Impact:

- Inconsistent auth behavior.
- Harder testing.
- Increased dependency on token parsing across services.

Recommended fix:

- Standardize service identity access:
  - Either trust gateway-propagated identity headers behind internal routing.
  - Or standardize local JWT parsing in a single service adapter pattern.
- Do not mix per-controller extraction styles.

#### M5. Gateway route predicates do not match service-owned API names cleanly

Evidence:

- `gateway-service/src/main/resources/application.yaml`
- `friendship-service/controller/FriendController`
- `upload-service/controller/UploadController`

Why it is a problem:

- Friendship service owns `/api/v1/friends`, but gateway route naming uses `/api/v1/friendship/**`.
- Upload service owns `/api/v1/uploads`, but gateway route naming uses `/api/v1/upload/**`.

Impact:

- Clients depend on gateway rewrites rather than stable service path ownership.
- Direct service paths and gateway paths differ unnecessarily.

Recommended fix:

- Pick canonical public paths and align controller mappings with gateway routes.
- Avoid rewrite rules that hide service ownership mismatches.

#### M6. Notification event topology is unclear

Evidence:

- `notification-service/service/impl/NotificationDomainService`
- `notification-service/kafka/ChatMessageEventConsumer`
- `notification-service/kafka/MessageCreatedEventConsumer`

Why it is a problem:

- `NotificationDomainService` creates notifications and publishes `NotificationRequested` events, but notification creation is already occurring inside the notification service.
- `ChatMessageEventConsumer` listens to a chat message event and ignores it because another consumer handles it.

Impact:

- It is unclear whether `notification-service` owns notification creation or merely requests notification creation.
- Duplicate or dead event paths increase maintenance cost.

Recommended fix:

- Define one inbound event path per domain event type.
- Keep notification creation inside notification application use cases.
- Publish only meaningful outbound notification-created/unread-count events if another component needs them.
- Remove ignore-only consumers.

#### M7. Chat event publication has hidden async side effects

Evidence:

- `chat-service/modules/message/application/pipeline/PublishMessageEventStep`

Why it is a problem:

- The pipeline updates room last message and read state, then uses `CompletableFuture.runAsync` for event publication without an explicit application executor boundary.

Impact:

- Failures and ordering are harder to reason about.
- Message persistence, room state, read state, and event emission are tightly coupled inside one pipeline step.

Recommended fix:

- Make event publication part of an explicit outbox/event-publisher boundary.
- Use a managed executor if asynchronous behavior is required.
- Keep room state updates as explicit use-case steps with clear transaction semantics.

#### M8. Friendship event producer performs enrichment

Evidence:

- `friendship-service/kafka/FriendshipEventProducer`
- `friendship-service/client/UserClient`

Why it is a problem:

- Event producers should publish already-decided event data, not own display-name lookup behavior.

Impact:

- Publishing becomes dependent on user-service availability.
- Relationship domain events are mixed with presentation enrichment.

Recommended fix:

- Move enrichment into application orchestration before event publication, or let consumers/realtime edge enrich from user read APIs when needed.

#### M9. Redis package boundaries are inconsistent

Evidence:

- `chat-service/modules/message/infrastructure/redis`
- `presence-service/redis`
- `presence-service/state/redis`
- `notification-service/websocket/redis`

Why it is a problem:

- Redis means different things in different services: state store, pub/sub fanout, typed event subscriber, raw websocket bridge.

Impact:

- Similar behavior is hard to locate.
- New engineers cannot infer architecture from package names.

Recommended fix:

- Use explicit adapter packages:
  - `adapter/out/redis/state`
  - `adapter/out/redis/pubsub`
  - `adapter/in/redis/subscriber`
- Move websocket delivery out of Redis packages.

### Low

#### L1. `config` and `configuration` package names are inconsistent

Evidence:

- `chat-service/config`
- `upload-service/config`
- `gateway-service/config`
- `auth-service/configuration`
- `user-service/configuration`
- `presence-service/configuration`
- `friendship-service/configuration`
- `notification-service/configuration`

Why it is a problem:

- It is minor, but it signals no shared service package convention.

Impact:

- Small navigation and consistency cost.

Recommended fix:

- Standardize on `config` or `configuration`; prefer `config` for brevity.

#### L2. Interface naming is inconsistent

Evidence:

- `chat-service/modules/message/application/service/IMessageCommandService`
- `chat-service/modules/message/application/service/IReactionCommandService`
- `chat-service/modules/room/service/IRoomService`
- Other services use concrete service names without `I` prefix.

Why it is a problem:

- Naming convention differs by service and makes architectural roles harder to infer.

Impact:

- Low functional risk, but weakens consistency.

Recommended fix:

- Use role-based names without `I` prefixes: `MessageCommandUseCase`, `ReactionCommandUseCase`, `RoomCommandService`, or similar.

#### L3. Unused Cloudinary service in user-service

Evidence:

- `user-service/service/impl/CloudinaryService`

Why it is a problem:

- It appears unused and duplicates upload/storage concerns.

Impact:

- Dead code and misleading ownership.

Recommended fix:

- Remove after confirming no external reflection/config usage.
- Keep upload/storage mechanics in `upload-service`.

#### L4. Notification websocket metrics are service-local

Evidence:

- `notification-service/websocket/NotificationWebSocketHandler`

Why it is a problem:

- Active session metrics belong with the component that owns websocket connections.

Impact:

- Metrics are fragmented across services.

Recommended fix:

- Move websocket connection metrics to realtime edge.

#### L5. `upload-service` identity extraction differs from other services

Evidence:

- `upload-service/controller/UploadController.currentUserId()`

Why it is a problem:

- It uses `Authentication.getName()` while other services use JWT helper/header patterns.

Impact:

- Small but visible standardization issue.

Recommended fix:

- Adopt the same service identity accessor across all REST adapters.

## 6. Cross-Service Boundary Review

### Boundaries that are broadly correct

`auth-service`:

- Correctly owns credentials, login, OAuth, JWT/key behavior, and account-created event emission.
- Boundary weakens only when it waits for user profile readiness.

`user-service`:

- Correctly owns user profile records, profile search, and avatar metadata fields.
- Boundary weakens through compile-time dependency on `upload-service`.

`chat-service`:

- Correctly owns rooms, memberships, messages, reactions, pins, and read state.
- Keeping room and message in one service is justified because message behavior depends heavily on room membership and room state.

`friendship-service`:

- Correctly owns friend requests, friendships, and block state.
- `InternalFriendController` block check is a valid service API for chat authorization if kept small and stable.

`presence-service`:

- Correctly owns online/offline, heartbeat, typing, and room presence state.
- State ports are a good boundary shape.

`notification-service`:

- Correctly owns notification persistence, read/unread state, and notification preferences.

`upload-service`:

- Correctly owns upload signing, policy, and confirmation.

`gateway-service`:

- Correctly owns edge routing, rate limiting, and coarse security.

### Boundaries that are weak

Auth to user:

- `auth-service/service/impl/UserProfileReadinessService` waits for profile readiness after account creation.
- This couples account registration to profile projection timing.
- Prefer eventual consistency or explicit registration state rather than blocking/sleeping in auth.

User to upload:

- `user-service` imports `upload-service/contract/UploadAssetMetadata`.
- This turns a service module into a library.
- Replace with HTTP/event/API DTO separation.

Chat to upload:

- `chat-service/modules/room/service/impl/CloudinaryService` and room avatar upload behavior duplicate upload responsibility.
- Chat should own room avatar metadata and policy decisions, not Cloudinary integration.

Notification to chat routes:

- `notification-service/controller/RoomMuteController` owning `/api/v1/rooms` overlaps chat route ownership.
- Notification preferences should live under notification paths.

Realtime ownership:

- Chat, presence, friendship, and notification all own browser websocket sessions.
- Realtime edge should be a separate boundary.

Event ownership:

- Notification creation and notification-requested events are blurred.
- Friendship event producer enriches with user display names.
- Chat payload factory performs persistence and remote lookups.

### Services that are too coupled

`chat-service` and `friendship-service`:

- Chat calls friendship block checks through `FriendshipClient` during message sending.
- This is a valid dependency if block state belongs to friendship, but it is a synchronous availability dependency in a hot path.
- Consider cached relationship policy snapshots or event-driven block projection if latency/availability becomes a problem.

`auth-service` and `user-service`:

- Account creation is coupled to profile readiness through polling.
- Account-created event should be enough for user profile projection; auth should not need profile internals for basic registration completion.

`user-service` and `upload-service`:

- Compile dependency is not justified.

`notification-service` and chat/friendship events:

- Event consumers know too much about classification. Notification-specific creation rules belong in notification application services, not Kafka classes.

### Services that should own different responsibilities

Move out of business services:

- Browser websocket sessions from chat/presence/friendship/notification to realtime edge.
- Generic websocket event frame mapping and broadcaster behavior to realtime edge.
- Cloudinary/storage integration from user/chat to upload service.
- Gateway route rewrite compensation into aligned controller/gateway route ownership.

Keep in current services:

- Chat membership/message/reaction/pin/read-state rules in chat.
- Friendship/block rules in friendship.
- Presence state in presence.
- Notification preferences and notification persistence in notification.
- Upload policy/signing in upload.
- Auth identity/token/key behavior in auth.

## 7. Standardization Review

### Best patterns currently present

`chat-service/modules/message`:

- Best current attempt at domain/application/infrastructure separation.
- The send/edit/delete pipeline structure gives a clearer application boundary than most services.
- Weakness: the module still leaks side effects into factories and async event publication.

`presence-service/state/port` and `presence-service/state/redis`:

- Good port/adapters idea for state storage.
- This is a better pattern than direct Redis usage from controllers/handlers.

`upload-service/domain/UploadPolicy` and `application/UploadPolicyRegistry`:

- Small, focused policy model.
- Good fit for a compact service.

`gateway-service`:

- Small edge module with config/filter/health responsibilities.
- It should stay technical and thin.

### Inconsistent patterns

Package naming:

- `config` vs `configuration`.
- `service.impl` vs `application/service`.
- `client` vs `integration` vs direct `RestClient`.
- `websocket` vs `realtime/websocket`.
- `redis` vs `infrastructure/redis` vs `websocket/redis`.

Controller style:

- Some controllers parse JWT directly.
- Some use `Authentication.getName()`.
- Gateway forwards `X-User-Id`, but service controllers do not consistently consume it.

Event style:

- Some services use typed event wrappers.
- Some use old compatibility event classes.
- Notification uses custom raw Redis websocket messages.
- Chat uses Redis typed subscribers and dedupe.
- Friendship directly pushes websocket events after Kafka.

DTO style:

- DTOs are top-level in most services.
- Chat uses capability-local DTOs.
- Upload exposes `contract` DTOs consumed by another service module.

Application service naming:

- `CommandService`, `DomainService`, `Service`, `IService`, and `UseCase` concepts are mixed.

Realtime style:

- Some services handle inbound commands over websocket.
- Some services only register sessions and push outbound events.
- Session registry and broadcaster code is repeated.

### Recommended standard service structure

For domain services, use this template:

```text
com.example.<service>
  <capability>/
    domain/
      model/
      event/
      service/
      repository/        # interfaces/ports only, if using repository ports
    application/
      command/
      query/
      dto/
      mapper/
      port/
    adapter/
      in/
        rest/
        kafka/
        websocket/       # temporary only until realtime edge migration
      out/
        persistence/
        kafka/
        redis/
        http/
        storage/
    config/
```

For small services:

- `upload-service` can keep a compact version of this structure.
- `gateway-service` can remain `config`, `filter`, `controller/health`, and route configuration because it is an edge service rather than a domain service.

Naming rules:

- Prefer `config` consistently.
- Prefer `application/command` and `application/query` over generic `service.impl`.
- Prefer `adapter/in/*` and `adapter/out/*` over top-level `kafka`, `redis`, `client`, and `websocket`.
- Avoid `I` prefixes for interfaces.
- Use `*UseCase` for application ports and `*Service` for domain/application implementations only when the role is clear.

## 8. Target Architecture

### Recommended service model

`gateway-service`:

- Own public HTTP routing, websocket routing to realtime edge, rate limiting, coarse security, CORS, and propagated identity.
- Should not own domain behavior.

`auth-service`:

- Own accounts, credentials, OAuth identities, JWTs, refresh tokens, keys, and auth lifecycle.
- Emit account-created/account-state events.
- Stop blocking on user profile readiness in the main auth flow.

`user-service`:

- Own profile state, display profile, profile search, generated defaults, and avatar metadata application.
- Consume account-created through an application use case.
- Do not depend on `upload-service` as a module.

`chat-service`:

- Own rooms, memberships, moderation, messages, reactions, pins, read state, and chat domain events.
- Do not own browser websocket sessions.
- Do not own Cloudinary/upload signing.

`friendship-service`:

- Own friend requests, friendship graph, block state, and relationship policy APIs.
- Publish friendship events.
- Do not own browser websocket sessions.

`presence-service`:

- Own presence state, heartbeat, typing, and room presence domain events.
- Keep Redis state behind ports.
- Do not own browser websocket sessions in the target model.

`notification-service`:

- Own notification preferences, notification creation rules, persisted notifications, read/unread state, and notification events.
- Consumers should delegate to notification application use cases.
- Do not own browser websocket sessions.

`upload-service`:

- Own upload policy, signing, provider confirmation, and storage metadata production.
- Other services consume upload results through APIs/events or stable external contracts, not by depending on the service module.

`realtime-edge-service`:

- New recommended service.
- Own all browser websocket connections and client realtime protocol.
- Consume events from business services and push to users/rooms/global channels.
- Optionally accept inbound realtime commands and translate them to service APIs, but domain services still own the use cases.

### Recommended websocket model

Client model:

- Client opens one websocket connection to realtime edge.
- Client subscribes to user, room, and global channels through the edge protocol.
- Client sends domain commands through REST where possible.
- If websocket commands remain, realtime edge validates protocol shape and calls service application APIs.

Event model:

- Business services publish domain events:
  - Chat message sent/edited/deleted/reaction/pin/read state.
  - Presence online/offline/typing/room presence.
  - Friendship request/accept/reject/block/unblock.
  - Notification created/read/unread count changed.
- Realtime edge maps domain events to outbound client frames.
- Durable events go through Kafka.
- Ephemeral fanout can use Redis only behind a standard abstraction.

Ownership:

- Realtime edge owns session registry, subscriptions, fanout, dedupe, frame envelope, and metrics.
- Business services own domain authorization and event creation.

### Recommended per-service package template

Target example for `chat-service`:

```text
com.example.chat
  message/
    domain/
    application/
    adapter/in/rest/
    adapter/in/kafka/
    adapter/out/persistence/
    adapter/out/kafka/
    adapter/out/http/
  room/
    domain/
    application/
    adapter/in/rest/
    adapter/out/persistence/
    adapter/out/http/
  config/
```

Target example for `notification-service`:

```text
com.example.notification
  notification/
    domain/
    application/
    adapter/in/rest/
    adapter/in/kafka/
    adapter/out/persistence/
    adapter/out/kafka/
  preference/
    domain/
    application/
    adapter/in/rest/
    adapter/out/persistence/
  config/
```

Target example for `presence-service`:

```text
com.example.presence
  presence/
    domain/
    application/
    adapter/in/rest/
    adapter/in/kafka/
    adapter/out/redis/state/
    adapter/out/kafka/
  config/
```

Temporary websocket adapter rule:

- While websocket endpoints still exist in business services, place them under `adapter/in/websocket`.
- No domain logic in websocket handlers.
- No service-specific session registry variations.
- Mark them for removal after realtime edge cutover.

### Rules for service vs common ownership

Service-owned:

- Business capabilities and rules.
- Application use cases.
- Persistence mappings and repositories.
- Service-specific DTOs.
- Service-specific adapters.
- Service-specific event handling policies.

Common-owned, without redesigning it here:

- Stable cross-service infrastructure abstractions.
- Shared event envelope/protocol primitives if truly cross-service.
- Shared auth identity helper if services standardize on it.
- Shared error and observability infrastructure where appropriate.

Forbidden service-layer pattern:

- No deployable service should be used as another service's compile-time library.
- No controller/websocket/Kafka adapter should contain core business orchestration.
- No browser websocket session registry should be duplicated per business service in the target model.
- No service should expose routes under another service's owned API namespace.

## 9. Phased Reorganization Plan

### Phase 1: Architecture freeze and compile alignment

Do first:

- Freeze new websocket endpoints and new service-to-service module dependencies.
- Align service adapters to the current shared foundation so all service modules compile.
- Document the service package template and route ownership map.
- Pick canonical public paths for friendship and upload.
- Decide whether identity comes from gateway-propagated headers or local JWT parsing, then standardize service adapters.

Leave temporarily:

- Existing websocket endpoints can remain until realtime edge is ready.
- Existing chat room/message persistence and message pipeline should not be reshaped until compile and event contracts stabilize.

Risky to change early:

- Chat message send/edit/delete transaction behavior.
- Notification delivery semantics.
- Common-layer APIs, because common redesign is outside this review.

### Phase 2: Websocket ownership cleanup

Do next:

- Create or designate a realtime-edge service.
- Move handshake/auth/session/subscription/frame/dedupe/metrics ownership to the realtime edge.
- Change gateway websocket routing to point clients to realtime edge.
- Make business services publish events only for outbound realtime updates.
- Define user, room, and global destination semantics centrally.

Incremental migration:

- Start with notification and friendship because their websocket handlers are thinner.
- Move presence after the edge supports ephemeral typing/presence channels.
- Move chat last because it has inbound websocket commands and room subscription behavior.

Freeze:

- No new business logic in service-local websocket handlers.

### Phase 3: Per-service package reorganization

Migrate service by service:

- `notification-service`: move Kafka consumers to `adapter/in/kafka`, notification commands to `application`, persistence to `adapter/out/persistence`, preferences into a clear capability package.
- `friendship-service`: move friend request/block rules into application/domain packages, move UserClient to `adapter/out/http`, move event publication to `adapter/out/kafka`.
- `presence-service`: preserve `state/port` idea but relocate into `presence/application/port` and `adapter/out/redis/state`; separate Redis pub/sub from state.
- `user-service`: move account-created consumer to `adapter/in/kafka`, profile commands/queries to `application`, persistence to `adapter/out/persistence`.
- `auth-service`: separate auth use cases, token/key infrastructure, email adapter, OAuth adapter, and event publisher.
- `chat-service`: keep capability split but rename and normalize `modules/message` and `modules/room` into consistent domain/application/adapter packages.
- `upload-service`: keep compact shape, but remove `contract` as a cross-service module dependency.
- `gateway-service`: keep mostly as-is, but align routes with canonical service paths.

### Phase 4: Integration cleanup

Standardize:

- Feign/RestClient/internal HTTP client style.
- Kafka producer/consumer adapter shape.
- Redis state/pubsub adapter shape.
- Error handling and response mapping.
- Identity extraction.
- Event naming and versioning.

Move:

- Room avatar Cloudinary behavior out of chat into upload workflow.
- User avatar metadata contract away from `upload-service` module dependency.
- Friendship display-name enrichment out of Kafka producer.
- Chat payload enrichment/persistence side effects out of event factories.

### Phase 5: Dead code and duplicate code removal

Remove after replacement:

- `user-service/service/impl/CloudinaryService` if confirmed unused.
- `notification-service/kafka/ChatMessageEventConsumer` if it remains ignore-only.
- `notification-service` `/api/v1/rooms` mapping after client/gateway migration.
- Business-service websocket configs, handlers, registries, and broadcasters after realtime edge cutover.
- Service-local realtime flow TODO branches once delivery semantics are centralized.
- Duplicate Redis/websocket fanout code after edge migration.

Postpone:

- Deep chat service decomposition beyond package boundaries until tests cover message/room behavior.
- Any common-layer redesign.
- Replacing synchronous chat-to-friendship block checks unless performance or availability data shows it is necessary.

## 10. Final Recommendation

The service layer should move toward capability-owned domain services plus one dedicated realtime edge.

The most important next step is not a cosmetic package cleanup. First, stabilize service compilation against the current shared foundation, freeze new websocket duplication, and define the target service package and route ownership standards. After that, migrate websocket ownership out of chat, presence, friendship, and notification into a realtime-edge service.

The highest-value architectural changes inside the service layer are:

- Remove browser websocket ownership from business services.
- Eliminate service-to-service compile dependencies, especially `user-service` depending on `upload-service`.
- Standardize REST/Kafka/Redis/client adapter package structure.
- Move business decisions out of websocket handlers and Kafka consumers into application use cases.
- Clarify API namespace ownership in gateway and controllers.
- Remove dead or duplicate transport code once replacement paths exist.

`common`-layer redesign is explicitly out of scope for this review. Service work should align to whatever shared foundation is selected, but this report does not recommend redesigning `chatappBE/common/**`.
