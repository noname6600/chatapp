# Full Service Review

## 1. Executive Summary

The current service split is mostly recognizable as a microservice architecture: identity is in `auth-service`, profile state is in `user-service`, social relationships are in `friendship-service`, conversations are in `chat-service`, volatile online state is in `presence-service`, persisted notifications are in `notification-service`, media signing is in `upload-service`, API routing is in `gateway-service`, and `realtime-edge-service` is intended to become the shared websocket edge.

The split is acceptable in concept, but the implementation is not yet coherent at runtime. The largest problem is not that the services are named incorrectly. The largest problem is that realtime ownership, authorization, event delivery, and internal API boundaries are split in two competing ways. Domain services still expose their own websocket endpoints, while `realtime-edge-service` also implements websocket entrypoints and delivery services. The gateway currently routes websocket traffic to the domain services, not to the realtime edge. That means the new edge service is structurally present but not operationally authoritative.

The main issues are a mix of architectural and structural problems:

- Architectural: websocket ownership is duplicated, durable events and transient realtime events are mixed inconsistently, and some service-to-service dependencies are stronger than they should be.
- Structural: service package layouts and adapter naming are inconsistent, especially around realtime, Kafka, Redis, clients, DTOs, and event contracts.
- Implementation-level: several endpoints and websocket flows miss room membership checks, event publishing happens inside transactions or through fire-and-forget async calls, upload confirmation trusts client-provided metadata, and production configuration is too permissive.

This codebase is service-fixable without a major `common-*` redesign. The preferred path is to stabilize service boundaries first: make one websocket owner authoritative, add service-local authorization checks, clean service-local DTO/event registrations, and reduce synchronous cross-service calls where they create runtime coupling. The shared common modules only need to be touched if a service cannot express the correct contract using existing common types.

## 2. Global Findings

### Critical

#### Duplicate websocket ownership makes realtime behavior incoherent

- Affected service(s): `gateway-service`, `chat-service`, `presence-service`, `friendship-service`, `notification-service`, `realtime-edge-service`
- Why it is a problem: The system currently has two realtime architectures at once. Domain services expose websocket endpoints directly, while `realtime-edge-service` also exposes `/realtime` and legacy `/ws/...` aliases. The gateway still routes websocket traffic to the domain services, so the edge service is built but not actually the front door. This creates duplicated handlers, different authorization behavior, and different delivery paths depending on which URL clients use.
- Evidence:
  - `chatappBE/gateway-service/src/main/resources/application.yaml` routes `/ws/chat/**` to `chat-service`, `/ws/presence/**` to `presence-service`, `/ws/friendship/**` to `friendship-service`, and `/ws/notifications/**` to `notification-service`.
  - `chatappBE/realtime-edge-service/src/main/java/com/example/realtime/config/WebSocketConfig.java` also registers `/realtime`, `/ws/notifications`, `/ws/friendship`, `/ws/presence`, and `/ws/chat`.
  - `chatappBE/chat-service/src/main/java/com/example/chat/realtime/websocket/ChatWebSocketHandler.java`
  - `chatappBE/presence-service/src/main/java/com/example/presence/websocket/PresenceWebSocketHandler.java`
  - `chatappBE/friendship-service/src/main/java/com/example/friendship/websocket/FriendshipWebSocketHandler.java`
  - `chatappBE/notification-service/src/main/java/com/example/notification/websocket/NotificationWebSocketHandler.java`
- Recommended fix: Pick the intended websocket owner and make it authoritative. The likely target is `realtime-edge-service`, but it should only become authoritative after the authorization and command-routing bugs below are fixed. Then change gateway websocket routes to the edge service and disable or remove public websocket endpoints from domain services, leaving domain services to publish events and expose internal command/query APIs.
- Fix scope: Service-only.

#### Room membership authorization is missing from high-risk chat and realtime flows

- Affected service(s): `chat-service`, `presence-service`, `realtime-edge-service`, indirectly `gateway-service`
- Why it is a problem: Authenticated users can subscribe to or query room-scoped data without a room membership check in several flows. In a chat app, room membership is a core security boundary. The absence of this check leaks messages, presence, typing state, membership metadata, and reactions across rooms.
- Evidence:
  - `chatappBE/chat-service/src/main/java/com/example/chat/realtime/websocket/session/ChatSessionRegistry.java` has `joinRoom(...)` that blindly associates a websocket session with any room id.
  - `chatappBE/chat-service/src/main/java/com/example/chat/realtime/websocket/ChatWebSocketHandler.java` accepts client `JOIN` messages and calls the registry without a chat membership check.
  - `chatappBE/presence-service/src/main/java/com/example/presence/websocket/session/PresenceSessionRegistry.java` has `joinRoom(...)` with no membership authorization.
  - `chatappBE/presence-service/src/main/java/com/example/presence/websocket/PresenceWebSocketHandler.java` and `chatappBE/presence-service/src/main/java/com/example/presence/controller/PresenceEdgeCommandController.java` accept room presence/typing actions without verifying chat membership.
  - `chatappBE/realtime-edge-service/src/main/java/com/example/realtime/subscription/ChannelSubscriptionManager.java` authorizes `ROOM`, `PRESENCE`, and `TYPING` subscriptions for any authenticated session.
  - `chatappBE/chat-service/src/main/java/com/example/chat/modules/message/application/query/MessageQueryService.java` accepts `currentUserId` but does not enforce room membership before returning messages.
  - `chatappBE/chat-service/src/main/java/com/example/chat/modules/message/application/pipeline/reaction/steps/ValidateReactionStep.java` and `PersistReactionStep.java` validate the message/reaction but not requester membership in the message room.
  - `chatappBE/chat-service/src/main/java/com/example/chat/modules/room/controller/RoomController.java` exposes room members, member count, bulk member lookup, and room code APIs with no obvious room membership or role authorization.
  - `chatappBE/presence-service/src/main/java/com/example/presence/controller/PresenceController.java` exposes `GET /room/{roomId}` without a room membership check.
- Recommended fix: Add service-local membership guards at the application boundary before returning room-scoped data or allowing room-scoped subscriptions. In `chat-service`, centralize checks in room/member application services and call them from message query, reaction, room member, and websocket subscription flows. In `presence-service` and `realtime-edge-service`, either call a minimal chat internal membership-check endpoint or consume a projected room-membership view. Prefer a service-local guard interface so transport handlers do not embed domain rules.
- Fix scope: Service-first, common optional.

#### Realtime edge `/ws/chat` join can mutate chat room membership

- Affected service(s): `realtime-edge-service`, `chat-service`
- Why it is a problem: A websocket `JOIN` should subscribe a connected user to realtime events for a room they already belong to. In the current edge implementation, `JOIN` is routed to a chat REST endpoint that performs a domain join by invite room id. If a user knows a group room UUID, a websocket join can become a membership mutation.
- Evidence:
  - `chatappBE/realtime-edge-service/src/main/java/com/example/realtime/adapter/in/websocket/RealtimeWebSocketHandler.java` handles legacy `/ws/chat` `JOIN`.
  - `chatappBE/realtime-edge-service/src/main/java/com/example/realtime/adapter/out/chat/RestChatCommandRouter.java` posts to `/api/v1/rooms/{roomId}/join`.
  - `chatappBE/chat-service/src/main/java/com/example/chat/modules/room/controller/RoomController.java` maps `POST /{roomId}/join`.
  - `chatappBE/chat-service/src/main/java/com/example/chat/modules/room/service/impl/RoomService.java` implements the endpoint as `joinByInviteRoomId(...)`, which changes membership.
- Recommended fix: Split "subscribe to room events" from "join room by invite". Edge websocket join must only subscribe after membership authorization. If edge needs a downstream call, add or use a chat-service internal endpoint such as `GET /internal/rooms/{roomId}/members/{userId}/authorized` rather than calling the public room-join mutation.
- Fix scope: Service-only.

#### Client-trusted upload confirmation and direct chat uploads break the media boundary

- Affected service(s): `upload-service`, `chat-service`
- Why it is a problem: `upload-service` is meant to own upload signing and confirmation, but confirmation currently trusts client-supplied asset metadata. At the same time, `chat-service` still has a direct Cloudinary upload path. This weakens the boundary, risks storing forged media metadata, and leaves two upload implementations active.
- Evidence:
  - `chatappBE/upload-service/src/main/java/com/example/upload/service/UploadSigningService.java` confirms uploads using client-provided `publicId`, `secureUrl`, `bytes`, dimensions, format, and resource type.
  - `chatappBE/upload-service/src/main/java/com/example/upload/dto/ConfirmUploadRequest.java` is the source of that trusted metadata.
  - `chatappBE/chat-service/src/main/java/com/example/chat/modules/room/controller/RoomController.java` still has `uploadAvatarLegacy(...)`.
  - `chatappBE/chat-service/src/main/java/com/example/chat/modules/room/service/CloudinaryService.java`
  - `chatappBE/chat-service/src/main/java/com/example/chat/config/CloudinaryConfig.java`
- Recommended fix: Keep upload responsibility in `upload-service`. Confirm uploads by verifying the Cloudinary asset server-side or by correlating confirmation to a prepared upload token that encodes owner, policy, folder, and constraints. Remove or disable direct Cloudinary upload endpoints and Cloudinary configuration from `chat-service`; chat should only persist a validated asset reference.
- Fix scope: Service-only.

### High

#### Durable Kafka flow and transient Redis/websocket flow are mixed inconsistently

- Affected service(s): `chat-service`, `presence-service`, `notification-service`, `realtime-edge-service`
- Why it is a problem: Services use Kafka, Redis pub/sub, and local websocket fanout with overlapping meanings. Some durable domain events are sent through Redis, some transient events are published to Kafka, and some flows are direct local fanout only. This creates multi-instance bugs and makes it unclear which event stream is authoritative.
- Evidence:
  - `chatappBE/chat-service/src/main/java/com/example/chat/modules/message/infrastructure/adapter/ChatMessageEventPublisherAdapter.java` publishes message-created events to Redis and Kafka, edits/deletes to Redis only, and reactions to Redis and Kafka.
  - `chatappBE/chat-service/src/main/java/com/example/chat/realtime/infrastructure/ChatRealtimeAdapter.java` classifies flows with `RealtimeFlowClassificationPolicy` but directly fans out for most room/member events and has TODOs for durable-first handling.
  - `chatappBE/presence-service/src/main/java/com/example/presence/redis/PresenceRedisPublisher.java` logs flow type but publishes everything through Redis.
  - `chatappBE/notification-service/src/main/java/com/example/notification/websocket/NotificationWebSocketPublisher.java` classifies notification push flow but has a TODO for Kafka and then sends realtime Redis events.
  - `chatappBE/realtime-edge-service/src/main/java/com/example/realtime/adapter/in/kafka/KafkaEventConsumer.java` has inactive or placeholder consumers with `autoStartup = "false"`.
- Recommended fix: Define service-level ownership first: Kafka for durable cross-service facts, Redis pub/sub for ephemeral fanout between instances, websocket only at the edge. Then update each service locally to publish the correct stream once. Avoid publishing the same semantic event through multiple paths unless one is explicitly a derived realtime projection.
- Fix scope: Service-first, common optional.

#### Event publication happens inside transactions or through unreliable async paths

- Affected service(s): `auth-service`, `friendship-service`, `notification-service`, `chat-service`
- Why it is a problem: Publishing before a transaction commits can emit facts that later roll back. Fire-and-forget async publication can lose events without retry or observability. Both patterns create distributed consistency bugs.
- Evidence:
  - `chatappBE/auth-service/src/main/java/com/example/auth/service/impl/LocalAuthService.java` publishes account-created events during registration flow.
  - `chatappBE/auth-service/src/main/java/com/example/auth/service/impl/OAuthAuthService.java` publishes account-created events during OAuth account creation/linking flow.
  - `chatappBE/friendship-service/src/main/java/com/example/friendship/service/impl/FriendCommandService.java` publishes friendship events from transactional command methods.
  - `chatappBE/notification-service/src/main/java/com/example/notification/service/impl/NotificationCommandService.java` calls `pushService.pushToUser(...)` inside the transactional create path.
  - `chatappBE/chat-service/src/main/java/com/example/chat/modules/message/application/pipeline/send/steps/PublishMessageEventStep.java` uses `CompletableFuture.runAsync(...)` for message event publication.
  - `chatappBE/chat-service/src/main/java/com/example/chat/modules/room/service/impl/SystemMessageService.java` and `RoomPinService.java` already show a better service-local after-commit pattern.
- Recommended fix: Standardize service-local after-commit publication for events derived from database mutations. Where reliability matters, add an outbox later. Do not start with a common refactor; first remove direct in-transaction publishes and common-pool fire-and-forget paths inside services.
- Fix scope: Service-only.

#### Internal APIs are not protected by an internal security boundary

- Affected service(s): `user-service`, `friendship-service`, potentially all services if directly reachable
- Why it is a problem: Some internal endpoints are `permitAll` at the service level. Gateway routing may hide them externally, but a microservice boundary should not rely on network hope alone. If service ports are reachable from an unexpected network path, internal APIs can be called without service authentication.
- Evidence:
  - `chatappBE/user-service/src/main/java/com/example/user/configuration/SecurityConfig.java` permits `/api/v1/users/internal/**`.
  - `chatappBE/user-service/src/main/java/com/example/user/controller/UserProfileController.java` exposes `existsByAccountId(...)` under an internal path.
  - `chatappBE/friendship-service/src/main/java/com/example/friendship/configuration/SecurityConfig.java` permits `/api/v1/internal/**`.
  - `chatappBE/friendship-service/src/main/java/com/example/friendship/controller/InternalFriendController.java`
- Recommended fix: Require an internal service credential, mTLS, gateway-only network enforcement, or signed service token for internal endpoints. As a service-first fix, add a simple internal authentication filter/property-gated token check to the affected services and keep public JWT auth separate.
- Fix scope: Service-only.

#### Chat service has critical DTO/event contract duplication

- Affected service(s): `chat-service`
- Why it is a problem: The chat application context fails because the same event type is registered to two payload classes. This is a runtime correctness issue and a symptom that service-local DTOs and shared event contract DTOs are overlapping without a clear boundary.
- Evidence:
  - Test failure from `chatappBE/chat-service/build/test-results/test/TEST-com.example.chat.ChatappApplicationTests.xml`: `Conflicting event type registration: eventType=chat.message.pinned is already mapped to com.example.common.integration.chat.MessagePinPayload, cannot re-register with com.example.chat.modules.room.dto.RoomMessagePinEventPayload`.
  - `chatappBE/chat-service/src/main/java/com/example/chat/config/ChatRedisEventConfig.java` registers `RoomMessagePinEventPayload`.
  - `chatappBE/chat-service/src/main/java/com/example/chat/modules/room/dto/RoomMessagePinEventPayload.java`
  - `chatappBE/chat-service/src/main/java/com/example/chat/realtime/subscriber/ChatMessagePinnedRedisSubscriber.java`
  - `chatappBE/chat-service/src/main/java/com/example/chat/realtime/subscriber/ChatMessageUnpinnedRedisSubscriber.java`
- Recommended fix: In `chat-service`, use one payload class for the external Redis/Kafka event type and map to any internal DTO separately. Prefer the already shared contract for the external event type if it exists. This is a service contract cleanup, not a broad common-module redesign.
- Fix scope: Service-only.

#### Auth service is synchronously coupled to user-service profile readiness

- Affected service(s): `auth-service`, `user-service`
- Why it is a problem: Registration and login token issuance can block on user-service readiness. Auth is no longer independently available if user-service is slow or down. The implementation polls with sleeps, which is brittle under load and hard to scale.
- Evidence:
  - `chatappBE/auth-service/src/main/java/com/example/auth/service/impl/AuthSessionService.java`
  - `chatappBE/auth-service/src/main/java/com/example/auth/service/impl/UserProfileReadinessService.java`
  - `chatappBE/auth-service/src/main/java/com/example/auth/client/UserServiceClient.java`
  - `chatappBE/auth-service/src/main/java/com/example/auth/service/impl/LocalAuthService.java`
- Recommended fix: Let auth own identity and token issuance without waiting for profile creation. Treat profile readiness as an asynchronous user-service concern. If clients need profile completion state, expose it through user-service or token claims that do not require synchronous polling.
- Fix scope: Service-only.

#### Gateway does not route to realtime-edge and has weak readiness semantics

- Affected service(s): `gateway-service`, `realtime-edge-service`
- Why it is a problem: The runtime entrypoint does not match the service architecture. Also, readiness treats downstream 4xx responses as healthy, which can hide routing or authentication misconfiguration.
- Evidence:
  - `chatappBE/gateway-service/src/main/resources/application.yaml` has no route to `realtime-edge-service` on port `8090`.
  - `chatappBE/gateway-service/src/main/java/com/example/gateway/health/DownstreamReadinessIndicator.java` considers any status `>= 200 && < 500` as OK.
- Recommended fix: Add an explicit edge websocket route only after fixing edge authorization and command routing. Tighten readiness to use health endpoints or a narrow allowed status list. Do not mark arbitrary 401/404 downstream responses as ready.
- Fix scope: Service-only.

### Medium

#### Service package structures are inconsistent across services

- Affected service(s): all services, most visible in `chat-service`, `presence-service`, `notification-service`, `friendship-service`
- Why it is a problem: Similar concepts are placed under different package names in different services. That makes the codebase harder to navigate and increases the chance that new flows are added in the wrong layer.
- Evidence:
  - `chat-service` uses `modules.message.application`, `modules.message.infrastructure`, `modules.room.service`, and top-level `realtime`.
  - `presence-service` uses `service`, `redis`, `websocket`, `controller`, and `lifecycle` packages.
  - `notification-service` uses `service`, `websocket`, `kafka`, `controller`, and entity/repository packages.
  - `friendship-service` uses `service.impl`, `kafka`, `websocket`, `controller`, and `domain`.
  - `realtime-edge-service` uses clearer adapter packages: `adapter.in`, `adapter.out`, `delivery`, `subscription`, `session`.
- Recommended fix: Do not rewrite everything. For new or touched code, converge on service-local conventions: `adapter.in.*`, `adapter.out.*`, `application.*`, `domain.*`, `config.*`, and `contract/dto.*` where useful. Start with realtime-heavy services.
- Fix scope: Service-only.

#### Several service classes are too broad and mix orchestration with domain policy

- Affected service(s): mostly `chat-service`, also `user-service`, `notification-service`
- Why it is a problem: Large services that own validation, persistence, cache invalidation, event publication, external clients, and response mapping become hard to test and easy to break.
- Evidence:
  - `chatappBE/chat-service/src/main/java/com/example/chat/modules/room/service/impl/RoomService.java` handles room lifecycle, membership, invite join, moderator logic, read state, avatar upload, cache, realtime fanout, and system messages.
  - `chatappBE/chat-service/src/main/java/com/example/chat/modules/message/application/service/MessageCommandService.java` orchestrates sending with many collaborators and validation responsibilities.
  - `chatappBE/user-service/src/main/java/com/example/user/service/UserProfileService.java` mixes profile rules, avatar metadata, cache, mapping, and repository operations.
  - `chatappBE/notification-service/src/main/java/com/example/notification/service/impl/NotificationCommandService.java` persists notifications and pushes realtime events in the same transactional path.
- Recommended fix: Use incremental extraction around use cases already visible in the code: room membership authorization, room metadata queries, avatar assignment, notification push after commit, and profile avatar updates. Avoid a broad rewrite.
- Fix scope: Service-only.

#### Configuration is not production-ready

- Affected service(s): all services, especially `chat-service`, `upload-service`, `realtime-edge-service`, `gateway-service`
- Why it is a problem: Main application configs include development defaults, schema mutation, SQL logging, and credentials. Production behavior should not depend on mutable schema updates or hard-coded secrets.
- Evidence:
  - `chatappBE/chat-service/src/main/resources/application.yaml` and `chatappBE/upload-service/src/main/resources/application.yaml` include Cloudinary API defaults.
  - Several services use `spring.jpa.hibernate.ddl-auto: update` and `show-sql: true` in main configs.
  - `chatappBE/auth-service/src/main/java/com/example/auth/configuration/DatabaseSchemaFixer.java`
  - `chatappBE/user-service/src/main/java/com/example/user/configuration/DatabaseSchemaFixer.java`
  - `chatappBE/notification-service/src/main/java/com/example/notification/configuration/DatabaseSchemaFixer.java`
  - `chatappBE/realtime-edge-service/src/main/resources/application.yaml` uses `spring.redis.host`/`port`, while the other Spring Boot 3 services use `spring.data.redis.*`.
  - `chatappBE/realtime-edge-service/build.gradle` imports Spring Cloud `2023.0.0` while the rest of the stack is aligned around newer Spring Cloud versions.
- Recommended fix: Move secrets to environment-only config, disable SQL logging in production, replace schema fixer classes with migrations, and align the edge service Redis/Spring Cloud configuration with the rest of the backend.
- Fix scope: Service-only.

#### Tests are being excluded instead of repaired around service contracts

- Affected service(s): `chat-service`, `friendship-service`, `notification-service`
- Why it is a problem: Excluding stale tests can be reasonable during migration, but the exclusions now hide exactly the areas with the most architectural risk: realtime contracts, event consumers, websocket handlers, and notification delivery.
- Evidence:
  - `chatappBE/chat-service/build.gradle` excludes several realtime contract, adapter, and handler tests.
  - `chatappBE/friendship-service/build.gradle` excludes legacy realtime and command tests.
  - `chatappBE/notification-service/build.gradle` excludes notification application, Kafka, contract, and context tests.
  - `.\\gradlew.bat test` currently fails at `:chat-service:test` due duplicate event payload registration.
- Recommended fix: Keep exclusions only temporarily and track them as debt. First restore context-load tests, then restore focused contract tests for service event payloads and websocket authorization.
- Fix scope: Service-only.

### Low

#### Naming is uneven for clients, handlers, publishers, subscribers, and command routers

- Affected service(s): all services
- Why it is a problem: Naming differences are not the root cause, but they slow maintenance and make ownership less obvious.
- Evidence:
  - `UserServiceClient`, `FriendshipServiceClient`, `RestChatCommandRouter`, `PresenceCommandRouter`, `ChatMessageEventPublisherAdapter`, `NotificationWebSocketPublisher`, `PresenceRedisPublisher`, `FriendshipEventProducer`.
- Recommended fix: When touching files, align names with role and direction: `*Client` for outbound REST, `*Producer` for Kafka, `*RedisPublisher`/`*RedisSubscriber` for Redis, `*WebSocketHandler` for inbound socket handling, and `*DeliveryService` for edge fanout.
- Fix scope: Service-only.

#### Some legacy package remnants and deleted refactor artifacts confuse ownership

- Affected service(s): `chat-service`, `friendship-service`
- Why it is a problem: Empty/deleted packages and partially migrated class names make it harder to see the intended structure.
- Evidence:
  - The chat room layer still centers on `RoomService` while earlier split-oriented files such as lifecycle/member/avatar services appear to have been attempted and removed in the working tree.
  - `friendship-service` contains websocket dependencies and a local websocket handler even though friendship realtime appears to be moving to `realtime-edge-service`.
- Recommended fix: Remove unused package remnants and dependencies only after selecting the authoritative realtime architecture. Keep this cleanup after security and event-flow fixes.
- Fix scope: Service-only.

## 3. Service-by-Service Review

### gateway-service

#### Purpose Check

`gateway-service` owns external HTTP routing, websocket routing, gateway-level filters, rate limiting, circuit breaking, and readiness visibility for downstream services. That ownership is correct. It should not own domain rules; it should enforce coarse perimeter behavior and route to the correct service.

#### What Is Good

- The service is small and focused.
- Route definitions are centralized in `src/main/resources/application.yaml`.
- It uses Spring Cloud Gateway, Redis rate limiting, circuit breakers, and a downstream readiness indicator.
- `JwtAuthenticationFilter` keeps authentication logic out of controllers and passes `X-User-Id` downstream for convenience.

#### Problems

- Critical: The gateway routes websocket traffic to legacy domain-service websocket endpoints instead of `realtime-edge-service`.
- High: `DownstreamReadinessIndicator` treats all `2xx` through `4xx` responses as ready.
- Medium: The gateway comment/behavior around JWT propagation can imply downstream services do not need to revalidate tokens. Direct service access or spoofed headers would make that dangerous.
- Low: Route ownership for room mute endpoints is ambiguous because notification has room-mute REST endpoints under a path that overlaps chat room routing.

#### Structural Issues

- Package layout is coherent for a gateway: `config`, `filter`, and `health`.
- Runtime routing structure is the main issue, not Java package organization.
- Gateway should avoid encoding domain-specific exceptions beyond route ownership and public/private route policy.

#### Integration Issues

- WebSocket: `/ws/chat/**`, `/ws/presence/**`, `/ws/friendship/**`, and `/ws/notifications/**` currently route to domain services. This conflicts with the existence of `realtime-edge-service`.
- REST: `/api/v1/rooms/**` routes to chat, which likely makes notification room-mute endpoints unreachable if they are also under room-style paths.
- Security: `X-User-Id` is useful for downstream convenience, but services must still authenticate or verify trusted gateway origin.

#### Suspicious or Misplaced Responsibilities

- None of the domain logic appears misplaced into gateway.
- The suspicious part is route ownership: gateway has become the place where the old and new realtime models are frozen together.

#### Recommended Service-Level Fixes

- After edge authorization is fixed, route websocket paths to `realtime-edge-service`.
- Remove or stop exposing legacy websocket routes through gateway once the edge is authoritative.
- Tighten readiness to call `/actuator/health/readiness` or treat only expected success statuses as healthy.
- Keep downstream JWT/resource-server validation in domain services until an explicit trusted-service network boundary exists.

### auth-service

#### Purpose Check

`auth-service` owns accounts, credentials, OAuth identity linkage, JWT/JWKS, refresh tokens, and token issuance. That ownership is correct. It should not own profile creation beyond publishing an account-created fact.

#### What Is Good

- Identity ownership is clear.
- Local auth and OAuth auth are separated into `LocalAuthService` and `OAuthAuthService`.
- Refresh-token rotation and JWKS support are appropriate auth-service responsibilities.
- Publishing `account.created` is the right integration direction for creating a user profile asynchronously.

#### Problems

- High: Auth is synchronously coupled to user-service readiness during registration/session issuance.
- High: Account-created events are published during transactional account flows instead of after commit/outbox.
- Medium: OAuth session behavior and JWT resource-server behavior are mixed. `SessionCreationPolicy.IF_REQUIRED` may be necessary for OAuth but weakens the mental model for a mostly stateless API.
- Medium: `DatabaseSchemaFixer` mutates schema at startup.
- Low: Custom JWT filter/resource-server patterns differ from other services, which adds maintenance overhead.

#### Structural Issues

- `service.impl` is serviceable but less expressive than use-case-oriented package names.
- `AuthSessionService` is an orchestration hotspot because it handles token issuance and profile readiness.
- `UserProfileReadinessService` embeds distributed workflow polling inside auth.
- Config is broad but understandable: JWT, security, OAuth, Kafka, and database concerns are under configuration/config packages.

#### Integration Issues

- REST/internal client: `UserServiceClient` makes auth depend synchronously on user-service.
- Kafka: `AccountCreatedEventProducer` is the right direction, but publish timing should be after commit.
- Security: Auth exposes public login/register/OAuth/JWKS, which is correct. Internal dependency on user readiness is the unhealthy part.

#### Suspicious or Misplaced Responsibilities

- Waiting for user profile readiness belongs outside the critical auth path. User-service should own profile creation and expose profile completion to clients.
- Auth should publish identity facts, not verify downstream profile creation before token issuance.

#### Recommended Service-Level Fixes

- Remove synchronous profile-readiness polling from token issuance.
- Publish `account.created` after successful account transaction commit.
- Add an explicit registration status/profile-completion response if UX needs to know whether the profile exists yet.
- Move startup schema repair to migrations and disable it in production profiles.

### user-service

#### Purpose Check

`user-service` owns user profile data, profile lookup, search, avatar metadata, and the projection of auth account creation into a user profile. That ownership is mostly correct.

#### What Is Good

- It consumes account-created events rather than auth writing profile data directly.
- Profile APIs and lookup APIs are concentrated in one service.
- Cache failures appear to be handled defensively rather than crashing the main profile path.
- Upload handling has mostly moved out of user-service, which is the right boundary direction.

#### Problems

- High: Internal profile-existence endpoint is permitted without internal service authentication.
- Medium: `UserProfileService` is broad and mixes validation, persistence, caching, avatar metadata, and mapping.
- Medium: `AccountCreatedConsumer.listen(...)` has weak null/shape guarding around envelope payloads.
- Medium: Startup schema mutation through `DatabaseSchemaFixer`.
- Medium: Bulk APIs should enforce request size limits and validation more explicitly.
- Low: Upload/avatar policy knowledge can drift from `upload-service` if not kept minimal.

#### Structural Issues

- Package names are conventional but relatively flat: controller, service, repository, dto, configuration.
- The service layer is the main structural issue. `UserProfileService` should be split by use case only where touched: profile query/update, avatar metadata update, cache coordination.
- DTOs are close to controllers. That is fine, but event payload DTOs should stay separated from public REST response DTOs.

#### Integration Issues

- Kafka: `AccountCreatedConsumer` is the right integration shape for auth-to-user profile creation.
- Redis/cache: caching is appropriate for profile lookups, but cache keys and failure behavior should remain hidden behind a profile cache component.
- Internal REST: `/api/v1/users/internal/**` should require internal authentication.
- Upload boundary: user should store validated avatar references, not sign or verify uploads itself.

#### Suspicious or Misplaced Responsibilities

- The service should not grow into media policy ownership. It can validate "this avatar reference is acceptable for a profile", but Cloudinary signing/confirmation belongs to `upload-service`.
- Public or semi-public bulk lookup endpoints can become a user-data scraping surface if not scoped and rate limited.

#### Recommended Service-Level Fixes

- Add internal authentication to user internal endpoints.
- Split avatar update/reference assignment from general profile update logic when next touched.
- Add payload null/format guards in `AccountCreatedConsumer`.
- Add request size limits for bulk profile lookup.
- Replace startup schema fixer with migrations.

### friendship-service

#### Purpose Check

`friendship-service` owns friend requests, friend relationships, blocking, and social graph queries. That ownership is correct.

#### What Is Good

- The domain is clearly bounded.
- Command and query services are separated: `FriendCommandService` and `FriendQueryService`.
- Relationship normalization through low/high account ids is a good model for pairwise friendship/blocking.
- Friendship events are published for downstream consumers.

#### Problems

- High: `FriendshipEventProducer` synchronously calls `user-service` to enrich events with sender display names.
- High: Friendship events are published inside transactional command flows.
- High: Internal blocked-between endpoint is publicly permitted if the service port is reachable.
- Medium: Local websocket handler is essentially a legacy/no-op path while edge also handles friendship events.
- Medium: Realtime dependencies and tests are in a migrated/half-migrated state.
- Low: Some package names (`service.impl`, `kafka`, `websocket`) are less consistent with newer adapter-oriented service structure.

#### Structural Issues

- Command/query separation is good and should stay.
- Kafka production is too close to synchronous enrichment. Event creation should use domain data already owned by friendship or a cached/projected display name, not live REST enrichment.
- The websocket package no longer appears to be the future authoritative realtime path.

#### Integration Issues

- REST/internal client: `UserClient` usage from the event producer is suspicious. Friendship can query user-service when serving a read model, but event production should avoid a blocking dependency.
- Kafka: friendship events are useful durable facts, but publish them after commit.
- WebSocket: `FriendshipWebSocketHandler` registers sessions, while `realtime-edge-service` consumes friendship Kafka and delivers notifications. These are competing paths.
- Internal API: `InternalFriendController` should not be unauthenticated.

#### Suspicious or Misplaced Responsibilities

- User display-name enrichment is user-service data leaking into friendship event publication. Prefer account ids in the durable event, with display enrichment done at notification/read-model time.
- Local friendship websocket ownership is redundant if edge owns realtime.

#### Recommended Service-Level Fixes

- Remove synchronous user-service calls from `FriendshipEventProducer`; include IDs and relationship metadata in events.
- Publish friendship events after transaction commit.
- Add internal auth to `/api/v1/internal/**`.
- Keep command/query split, but move outbound integrations into explicit adapter packages when touched.
- Retire local websocket entrypoint after gateway is moved to realtime edge.

### chat-service

#### Purpose Check

`chat-service` owns rooms, membership, messages, reactions, read state, room pins, system messages, and chat-domain events. This is the largest service and is expected to be more complex than the others. The core ownership is correct, but chat currently also retains media upload logic and local websocket ownership that should move out or become internal-only.

#### What Is Good

- The message module has a stronger internal structure than most services: domain/application/infrastructure packages, send/reaction pipelines, and clear repository boundaries.
- Room and message responsibilities are at least module-separated.
- Redis sequence allocation for messages is a reasonable chat-service concern.
- Some flows already use after-commit publication, especially in `SystemMessageService` and `RoomPinService`.
- External user/friendship interactions are mostly behind clients rather than scattered through controllers.

#### Problems

- Critical: Room membership authorization is missing from message queries, reactions, room member metadata APIs, and websocket room subscription.
- Critical: `RoomController.uploadAvatarLegacy(...)`, `CloudinaryService`, and `CloudinaryConfig` duplicate `upload-service` responsibility.
- High: `RoomService` is a god service for room lifecycle, membership, moderation, cache, avatar upload, read state, realtime, and system messages.
- High: Realtime publication is inconsistent between Kafka, Redis, and local fanout.
- High: `ChatRedisEventConfig` registers a service-local pin payload for an event type already mapped to a common integration payload, causing context-load test failure.
- High: Message event publication uses `CompletableFuture.runAsync(...)` without transactional reliability or a dedicated executor.
- Medium: `MessageCommandService` still mixes orchestration, validation, membership/block checks, entity construction, and event pipeline execution.
- Medium: Several tests for realtime contracts/handlers are excluded.
- Low: Package organization is partly modern (`modules.message.application`) and partly older (`modules.room.service.impl`).

#### Structural Issues

- `modules.message` is the best internal pattern in the service. It separates application pipeline steps from infrastructure adapters.
- `modules.room` needs similar use-case separation, but this should be incremental. Start with:
  - room membership authorization/query service
  - room lifecycle command service
  - room avatar reference service
  - room metadata/read-model query service
- Top-level `realtime` mixes websocket handler/session registry, Redis subscriber, and fanout adapter. If edge becomes authoritative, chat should keep event publication/subscription adapters but stop exposing public websocket handling.
- DTO boundaries are blurry around pin events. `RoomMessagePinEventPayload` conflicts with shared `MessagePinPayload`.

#### Integration Issues

- REST/internal clients: Chat depends on user-service for profile enrichment and friendship-service for blocking checks. That direction is acceptable, but these calls should be limited to application services and protected by clear fallbacks.
- Kafka: Durable message/friendship/notification-facing facts should be Kafka events, published after commit or outbox.
- Redis: Redis should be used for transient fanout and chat-local sequencing/cache. It should not be the only propagation path for durable chat facts.
- WebSocket: Current `/ws/chat` handler performs local session registration and fanout. This conflicts with `realtime-edge-service`.
- Event contracts: Chat has duplicate local/shared payload registration for `chat.message.pinned`.

#### Suspicious or Misplaced Responsibilities

- Direct Cloudinary upload belongs to `upload-service`.
- Websocket connection ownership should move to edge if the edge service is retained.
- Room membership checks belong in chat and should be exposed minimally for edge/presence to call or project.
- Profile display enrichment can stay in chat read models, but durable chat events should avoid embedding volatile user profile data unless explicitly needed.

#### Recommended Service-Level Fixes

- Add membership checks to `MessageQueryService`, reaction pipeline validation, room member/member-count/code endpoints, and chat websocket room joins.
- Remove the websocket `JOIN` ability to subscribe without authorization.
- Fix `chat.message.pinned` event registration by using one external payload type.
- Remove or disable `uploadAvatarLegacy`, `CloudinaryService`, and chat-local Cloudinary config after upload-service confirmation is safe.
- Move event publication from `CompletableFuture.runAsync(...)` to after-commit with a service-local publisher abstraction.
- Start splitting `RoomService` only around high-risk use cases. Do not rewrite the entire room module in one pass.

### presence-service

#### Purpose Check

`presence-service` owns volatile user online state, device/session presence, room presence, typing indicators, and presence event fanout. That ownership is correct if it stays focused on transient state and does not become the source of chat membership rules.

#### What Is Good

- Presence is separated from chat, which is correct because online state is volatile and scales differently from persisted messages.
- Redis-backed state and TTL behavior are appropriate for presence.
- The service has a clearer separation between lifecycle handling, Redis state, websocket handling, and controllers than earlier versions often have.
- It uses Redis pub/sub for transient multi-instance fanout, which is appropriate for presence/typing.

#### Problems

- Critical: Room presence and typing flows do not verify chat room membership.
- High: Local direct websocket handling duplicates edge websocket handling.
- High: Local session-based offline decisions are fragile in multi-instance deployment.
- Medium: `PresenceEdgeCommandController` trusts edge-originated room actions without verifying room membership.
- Medium: Redis key expiration handling assumes Redis keyspace notifications are enabled, but that requirement is not clearly reflected in config.
- Medium: TTL/auto-away thresholds are not obviously aligned.
- Low: `GET /global` can expose broad online state if not intentionally scoped.

#### Structural Issues

- Packages are understandable but not as layered as edge or chat message modules.
- `websocket`, `redis`, `lifecycle`, `service`, and `controller` are clear enough for now.
- Membership authorization should be a separate adapter/service, not embedded directly in websocket handlers.
- Presence event DTOs should stay separate from chat domain DTOs.

#### Integration Issues

- Redis: Correct tool for ephemeral presence state and fanout, but keyspace notification dependency must be operationalized.
- WebSocket: Direct websocket endpoint should be retired if realtime-edge becomes authoritative.
- REST/internal: Edge command controller should be protected as internal and should validate that the user can act on the room.
- Chat dependency: Presence should not own chat membership. It should call a minimal chat membership API or consume a membership projection.

#### Suspicious or Misplaced Responsibilities

- Presence should not decide who belongs to a room by itself.
- Presence should not expose room presence to any authenticated user.
- If edge owns websocket sessions, presence should not need to track client websocket sessions directly except through edge commands/events.

#### Recommended Service-Level Fixes

- Add a room authorization adapter to presence and use it for room presence and typing reads/writes.
- Protect `PresenceEdgeCommandController` as an internal endpoint.
- Make edge the only websocket ingress before removing direct presence websocket routes.
- Document and enforce Redis keyspace notification requirements in service config/runbooks.
- Review TTL constants so heartbeat expiry and auto-away semantics match.

### notification-service

#### Purpose Check

`notification-service` owns persisted notifications, unread state, notification preferences/mutes, and notification delivery fanout. That ownership is mostly correct. It should not own room membership, but it must respect room membership when storing room-specific preferences.

#### What Is Good

- Notification persistence and realtime push are centralized.
- Room mute settings belong reasonably in notification because they are notification preferences, not chat membership facts.
- Kafka consumers allow other services to request notifications without writing notification tables.
- Redis pub/sub is used for multi-instance notification websocket fanout.

#### Problems

- High: Local websocket endpoint duplicates edge delivery.
- High: Notification push is performed inside the notification creation transaction.
- High: Room mute endpoints appear under room-like API paths that may conflict with chat gateway routing and do not verify room membership.
- Medium: In-memory dedupe is not sufficient for multi-instance event consumption.
- Medium: `AccountCreatedEventConsumer` bypasses the newer command/application service shape and needs stronger null/dedupe handling.
- Medium: `NotificationDomainService` publishes `notification.requested` after already pushing/persisting in some flows, which makes event ownership unclear.
- Low: Several notification tests are excluded in Gradle.

#### Structural Issues

- The service is relatively flat: controller, service, kafka, websocket, repository, entity.
- `NotificationCommandService` should own persistence and state transitions, while delivery should happen after commit.
- Kafka consumers should map external events to application commands rather than reaching around application boundaries.
- Websocket delivery code should become edge-facing or internal fanout only after the edge migration.

#### Integration Issues

- Kafka: Notification should consume durable facts and create notifications idempotently.
- Redis: Redis pub/sub is suitable for pushing already-created notification events to connected websocket instances.
- WebSocket: Direct `/ws/notifications` conflicts with edge.
- REST: Room mute APIs need clear routing and membership validation. If gateway routes `/api/v1/rooms/**` to chat, notification mute paths under similar ownership are likely unreachable or confusing.

#### Suspicious or Misplaced Responsibilities

- Room mute settings belong in notification, but authorization depends on chat membership. Notification should not infer that itself.
- The service should not publish and consume its own notification-requested facts in a way that hides who is authoritative for creation.

#### Recommended Service-Level Fixes

- Move push delivery to after-commit.
- Add idempotency/dedupe using persistent keys for consumed events.
- Protect room mute APIs with membership validation through chat or a projected membership view.
- Move room mute route paths away from chat-owned `/api/v1/rooms/**` if gateway conflicts exist, for example under `/api/v1/notification-settings/rooms/{roomId}`.
- Retire local websocket ingress after edge cutover.

### upload-service

#### Purpose Check

`upload-service` owns upload preparation, upload policy enforcement, and upload confirmation for media assets. That is the correct microservice boundary. It should not own chat room avatar assignment or user profile avatar assignment; it should return validated asset references for those services to persist.

#### What Is Good

- The service is focused and small.
- Policy-specific upload behavior is centralized in one service rather than duplicated across chat and user controllers.
- No database dependency keeps the service simple.
- The prepare/confirm split is a good direction for direct-to-cloud uploads.

#### Problems

- Critical: Confirm upload trusts client-supplied Cloudinary metadata and secure URL.
- High: Prepared uploads are not strongly correlated with confirmation, owner, policy, or expected folder.
- High: Main config includes Cloudinary credential defaults.
- Medium: `PrepareUploadRequest` does not carry enough information to validate size before signing, and the controller currently passes `0L` for size.
- Medium: Other services still retain local media policy/upload paths, especially chat.
- Low: The policy registry is good, but policy drift remains possible if chat/user duplicate validation rules.

#### Structural Issues

- Package layout is simple and adequate.
- `UploadSigningService` currently owns both signing and confirmation. That is fine for now, but confirmation verification may deserve a separate collaborator once it calls Cloudinary server-side.
- DTOs are service-local and appropriate.

#### Integration Issues

- Cloudinary: Confirmation should verify the actual Cloudinary asset by `publicId` and expected upload context, not trust the client payload.
- Service consumers: Chat/user should call upload-service or accept only upload-service validated references.
- Security: Upload ownership must bind `currentUserId`, policy, folder, max size, resource type, and confirmed asset.

#### Suspicious or Misplaced Responsibilities

- None inside upload. The issue is that other services have not fully stopped doing upload work.

#### Recommended Service-Level Fixes

- Issue a prepare token or persist a short-lived prepare record that binds user, policy, folder, and constraints.
- On confirm, verify Cloudinary metadata server-side and compare it to the prepare record/token.
- Remove Cloudinary defaults from committed config.
- Make chat/user persist only upload-service validated asset references.

### realtime-edge-service

#### Purpose Check

`realtime-edge-service` is intended to own websocket connections, subscriptions, edge session state, and fanout from domain events to connected clients. That role is justified for this system because chat, presence, friendship, and notification all need realtime delivery, and duplicating websocket handling in every domain service creates scaling and consistency problems.

#### What Is Good

- The intended boundary is strong: edge owns websocket sessions, domain services own domain state.
- Package structure is one of the clearer services: `adapter.in`, `adapter.out`, `delivery`, `subscription`, `session`.
- It has a session registry abstraction and a Redis-backed option for shared session state.
- It has per-domain delivery services and command-router abstractions.
- It recognizes legacy websocket paths, which can help migration if implemented safely.

#### Problems

- Critical: Gateway does not route websocket traffic here, so the service is not authoritative.
- Critical: `/ws/chat` `JOIN` routes to chat room join mutation instead of subscription authorization.
- Critical: `ChannelSubscriptionManager` authorizes room/presence/typing channels for all authenticated sessions.
- High: Generic Kafka consumers are disabled or placeholder, so durable event ingestion is incomplete.
- High: `EventDeliveryService.deliverToSession(...)` is placeholder-like and not a real generic delivery path.
- High: Redis config uses `spring.redis.*`, unlike the rest of the Spring Boot 3 services.
- High: Build imports Spring Cloud `2023.0.0`, which is inconsistent with the rest of the backend dependency alignment.
- Medium: Default session registry is in-memory, so multi-instance behavior is not active by default.
- Medium: Token handling appears query-param oriented for websocket handshakes. This is common for browsers but must be treated carefully in logs and gateway config.
- Medium: Edge depends on several downstream services and can fail partially if command routers are unavailable.

#### Structural Issues

- The package structure is good and should be the target style for future adapter work.
- Authorization is placed too generically in `ChannelSubscriptionManager`. It needs domain-aware authorization hooks for room/presence/typing.
- Command routing should not perform domain mutations for websocket subscriptions.
- Placeholder generic event delivery should either be completed or removed until a real common delivery abstraction exists.

#### Integration Issues

- Gateway: Not integrated.
- Chat: Wrong command route for join.
- Presence: Edge command routes need internal auth and room authorization.
- Friendship: Active Kafka friendship consumer can deliver through edge, but clients are still routed to friendship-service websocket by gateway.
- Notification: Edge has notification delivery, while notification-service also has local websocket delivery.
- Redis/Kafka: Edge partially consumes Redis and Kafka, but generic event flow is not complete.

#### Suspicious or Misplaced Responsibilities

- Edge should not decide domain authorization by itself without consulting domain services or projections.
- Edge should not mutate chat membership on websocket subscribe.
- Edge should not become a domain orchestration service. It should authenticate, authorize subscription through minimal domain checks, manage sockets, and deliver events.

#### Recommended Service-Level Fixes

- Fix `/ws/chat` join to become subscription-only.
- Add domain authorization adapters for room/presence/typing subscriptions.
- Complete or disable placeholder generic Kafka/event delivery paths.
- Align Redis and Spring Cloud configuration with the rest of the backend.
- Make Redis-backed session registry the production default if edge runs multiple instances.
- Move gateway websocket routes to edge only after the above fixes.

## 4. Cross-Service Consistency Review

The services are not consistently organized. `realtime-edge-service` has the cleanest adapter-oriented structure. `chat-service` has a modern message module but an older room module. `presence-service`, `notification-service`, and `friendship-service` use flatter controller/service/kafka/websocket layouts. This is workable, but new code should stop adding more variations.

Inconsistent folder structures:

- `chat-service`: `modules.message.application`, `modules.message.infrastructure`, `modules.room.service.impl`, top-level `realtime`.
- `realtime-edge-service`: `adapter.in`, `adapter.out`, `delivery`, `session`, `subscription`.
- `presence-service`: `controller`, `service`, `redis`, `websocket`, `lifecycle`.
- `notification-service`: `controller`, `service`, `kafka`, `websocket`, `entity`, `repository`.
- `friendship-service`: `controller`, `service.impl`, `kafka`, `websocket`, `domain`.

Inconsistent naming:

- Outbound REST appears as `UserServiceClient`, `FriendshipServiceClient`, `UserClient`, `RestChatCommandRouter`, and `PresenceCommandRouter`.
- Event output appears as `*Producer`, `*Publisher`, `*EventPublisherAdapter`, and `*WebSocketPublisher`.
- Redis subscribers and websocket publishers are named differently even when they perform similar adapter roles.

Inconsistent event handling:

- Chat publishes some facts through Kafka plus Redis, some through Redis only, and some through direct local fanout.
- Notification consumes Kafka, persists, and pushes Redis/websocket, but also has event production paths whose authority is unclear.
- Friendship publishes durable Kafka facts, but enriches them synchronously with user-service data.
- Presence uses Redis for transient events, which is right, but the flow classification naming suggests broader semantics than the implementation actually provides.
- Edge consumes some Kafka topics and Redis streams, but generic consumers are disabled or placeholders.

Inconsistent websocket/realtime organization:

- Domain services still own websocket handlers.
- Edge also owns websocket handlers.
- Gateway points to domain services.
- Edge has alias support for old paths, but those aliases are not the active gateway routes.

Inconsistent DTO/request/response/event design:

- Chat pin events have both common integration payloads and service-local payloads registered to the same event type.
- Notification Redis payloads are raw `RealtimeWsEvent` JSON, while chat/presence use event envelopes.
- REST DTOs, websocket DTOs, and event payloads are not always visibly separated by package.

Recommended consistency target:

- Controllers/websocket/Kafka listeners: `adapter.in.*`
- REST clients/Kafka producers/Redis publishers: `adapter.out.*`
- Use cases: `application.*`
- Persistent domain model and policies: `domain.*`
- External event contracts: `contract.event.*` or existing common integration contracts
- REST/websocket DTOs: transport-specific DTO packages, not reused as durable event contracts unless intentionally stable

This does not require a full rewrite. Apply it to high-risk touched areas first: chat room authorization, edge subscriptions, presence room actions, notification mute settings, and upload confirmation.

## 5. Realtime / Kafka / Redis / WebSocket Review at Service Level

### Actual Current Flow

Gateway websocket flow currently goes to domain services:

- `/ws/chat/**` goes to `chat-service`.
- `/ws/presence/**` goes to `presence-service`.
- `/ws/friendship/**` goes to `friendship-service`.
- `/ws/notifications/**` goes to `notification-service`.

Domain service local websocket behavior:

- `chat-service` accepts chat websocket sessions, lets clients join rooms in a local registry, and sends chat events through local fanout and Redis subscriber paths.
- `presence-service` accepts websocket sessions, tracks presence/typing, writes to Redis, and receives Redis pub/sub events.
- `notification-service` accepts notification websocket sessions and pushes notification events through local/Redis paths.
- `friendship-service` has a websocket handler but the active delivery path appears weak or legacy compared with edge.

Realtime edge behavior:

- `realtime-edge-service` exposes `/realtime` and legacy `/ws/...` aliases.
- It can consume friendship Kafka events and deliver them through edge delivery services.
- It has Redis listeners for chat/presence/notification-style events.
- Some generic Kafka consumption is disabled with `autoStartup = "false"`.
- It is not reached by gateway websocket routes.

### What Is Clean

- The conceptual split of durable Kafka facts, Redis ephemeral fanout, and websocket edge delivery is the right direction.
- Presence using Redis TTL/pubsub for volatile state is appropriate.
- Notification owning persisted notification state is appropriate.
- Chat owning room/message facts is appropriate.
- Edge owning session registry and delivery would be appropriate if gateway and services were cut over consistently.

### What Is Duplicated, Ambiguous, or Fragile

- Websocket session ownership is duplicated between domain services and edge.
- Chat room join semantics differ between local chat websocket and edge legacy websocket path.
- Chat event publication uses three different delivery patterns: Kafka, Redis, and direct local fanout.
- Notification has both local websocket delivery and edge delivery support.
- Friendship Kafka-to-edge delivery is likely ineffective for real users while gateway points clients to friendship-service websocket.
- Edge generic delivery code is incomplete enough that it should not be treated as authoritative.

### Kafka vs Redis vs WebSocket Role Separation

Intended clean separation should be:

- Kafka: durable cross-service facts, such as account created, friendship changed, message created if other services need it, notification requested/created when it must survive restarts.
- Redis pub/sub: transient multi-instance fanout for already-authorized realtime delivery, presence, typing, and socket instance coordination.
- Redis storage/TTL: presence state, session registry if edge is horizontally scaled, chat sequence/cache if needed.
- WebSocket: client connection transport only, ideally owned by `realtime-edge-service`.

Current role separation is not clean:

- Chat sometimes treats Redis as the only event bus for durable-looking room/member events.
- Notification pushes realtime before transaction commit.
- Edge subscribes to both Kafka and Redis but only partially implements the durable side.
- Domain services still send directly to websockets.

### Current Service Ownership of Realtime Concerns

The right end-state is likely:

- `realtime-edge-service`: websocket authentication, session registry, subscription registry, fanout to clients.
- `chat-service`: room/message state, membership authorization, durable chat facts, internal authorization checks for edge/presence.
- `presence-service`: presence state and typing state, no direct public websocket if edge is active.
- `notification-service`: notification persistence/preferences, no direct public websocket if edge is active.
- `friendship-service`: friendship/block domain and durable events, no direct public websocket if edge is active.

The current state does not match that end-state. The edge service is justified, but incomplete and not integrated. It is not redundant conceptually; it is redundant operationally until gateway and domain services stop owning the same websocket surface.

## 6. Dependency Direction Review

Services depending on too many others:

- `realtime-edge-service` depends on chat, presence, friendship, notification, Redis, and Kafka. This is acceptable for an edge if it is only a transport/subscription service, but dangerous if it starts owning domain orchestration.
- `chat-service` depends on user-service and friendship-service. This is mostly reasonable: chat needs profile display data and block checks. The dependency should remain behind clients and avoid being required for every hot-path operation if cached/projected data is possible.
- `auth-service` depends on user-service readiness. This is the most suspicious dependency because identity/token issuance should not synchronously depend on profile projection.
- `friendship-service` depends on user-service inside event production. This is suspicious because durable relationship events should not block on user profile enrichment.

Services exposing too much:

- `chat-service` exposes room-scoped reads and websocket subscription without enough membership checks.
- `presence-service` exposes room/global presence too broadly.
- `user-service` and `friendship-service` expose internal endpoints without internal authentication if service ports are reachable.
- `notification-service` exposes room mute settings without clear route ownership and membership validation.

Suspicious internal client relationships:

- `auth-service -> user-service` for profile readiness polling.
- `friendship-service -> user-service` from `FriendshipEventProducer`.
- `realtime-edge-service -> chat-service` using the public room join endpoint for websocket subscribe.
- `presence-service/realtime-edge-service -> chat-service` missing a minimal authorization dependency even though they handle room-scoped state.

Dependencies to reduce or invert:

- Replace auth-to-user readiness polling with asynchronous profile creation and client-visible profile status.
- Replace friendship event enrichment with ID-only durable events or enrichment in notification/read models.
- Replace edge room join mutation with a chat membership authorization check.
- Add a minimal chat membership authorization API/projection for presence and edge. This is not a common-module issue; it is a service contract issue.

## 7. Minimal Fix Strategy

### Phase 1 - Safe service-only fixes

- What to do:
  - Fix `chat.message.pinned` duplicate payload registration in `chat-service`.
  - Add membership checks to chat message queries, reactions, room member metadata endpoints, and local chat websocket joins.
  - Stop realtime-edge `/ws/chat` from calling the chat room join mutation.
  - Add authorization checks for edge room/presence/typing subscriptions.
  - Add internal auth protection to `user-service` and `friendship-service` internal endpoints.
  - Remove hard-coded Cloudinary credential defaults from configs.
- Why:
  - These are correctness and security issues with high blast radius.
  - They can be fixed without changing shared common modules.
- Risk level: Medium. Authorization changes may expose clients relying on previously permissive behavior.
- Expected impact: Immediate improvement in data isolation, test stability, and runtime safety.

### Phase 2 - Structural service refactors

- What to do:
  - Extract focused room membership authorization/query logic from `RoomService`.
  - Move chat event publishing away from `CompletableFuture.runAsync(...)` to after-commit service-local publishers.
  - Move notification push after commit.
  - Remove synchronous user-service enrichment from friendship event production.
  - Remove auth profile-readiness polling from token issuance.
  - Harden upload confirmation with server-side verification or prepare-token correlation.
- Why:
  - These changes reduce cross-service coupling and transaction/event correctness risk while staying inside service boundaries.
- Risk level: Medium to high, depending on client expectations around registration and upload confirmation.
- Expected impact: Better reliability, clearer ownership, easier testing, fewer distributed consistency bugs.

### Phase 3 - Cross-service consistency fixes

- What to do:
  - Decide that `realtime-edge-service` is the websocket owner.
  - Route gateway websocket traffic to edge.
  - Disable or remove public websocket endpoints from chat, presence, friendship, and notification after migration.
  - Standardize touched adapter names and package placement.
  - Restore excluded realtime/event contract tests as the flows are migrated.
  - Move notification room mute routes to an unambiguous notification-owned path if gateway conflicts exist.
- Why:
  - This removes the two-realtime-architectures problem without a large rewrite.
- Risk level: High. Websocket migration affects clients and operational routing.
- Expected impact: Much clearer realtime ownership and better horizontal scaling.

### Phase 4 - Only unavoidable common changes

- What to do:
  - Only change `common-*` if existing common event contracts cannot represent the corrected service-level contract.
  - If common changes are needed, keep them additive and version-compatible where possible.
  - Do not use this phase for package cleanup or broad common redesign.
- Why:
  - The current problems are mostly service usage, service boundaries, and runtime integration problems.
- Risk level: Medium if common contracts change; low if changes are additive.
- Expected impact: Removes remaining contract friction after service-level boundaries are stable.

## 8. Top Priority Fix Order

1. Fix chat room data authorization: message reads, reactions, room member/member-count/code APIs, and local websocket room joins.
2. Fix realtime-edge `/ws/chat` so websocket join cannot mutate chat room membership.
3. Add domain-aware authorization to `realtime-edge-service` subscriptions for room, presence, and typing channels.
4. Fix `chat.message.pinned` duplicate event payload registration so `chat-service` context tests load.
5. Protect `user-service` and `friendship-service` internal endpoints with internal service authentication.
6. Harden `upload-service` confirmation and remove or disable direct Cloudinary upload from `chat-service`.
7. Move event publication after commit in auth, friendship, notification, and chat message send flows.
8. Remove auth-to-user synchronous profile readiness polling from token issuance.
9. Remove friendship-to-user synchronous enrichment from `FriendshipEventProducer`.
10. Decide and execute websocket ownership migration: route gateway to `realtime-edge-service`, then retire public domain websocket endpoints.
11. Align `realtime-edge-service` runtime config: Redis properties, dependency BOM, production session registry, and enabled/disabled consumers.
12. Restore excluded realtime/event tests and add focused authorization tests for chat, presence, notification mute settings, and edge subscriptions.
13. Replace startup schema fixer classes and `ddl-auto: update` with migrations in production profiles.
14. Incrementally split `RoomService` and `UserProfileService` around the high-change use cases.

## 9. Final Verdict

The current service split is acceptable in concept. The system does not need a full rewrite and does not need a broad `common-*` redesign to become stable.

The biggest architectural problem is duplicated realtime ownership. `realtime-edge-service` is the right kind of service for this app, but it is not yet authoritative, and its current legacy chat join behavior is unsafe.

The biggest structural/code problem is that service boundaries are not consistently enforced at application boundaries. Chat room membership checks are missing from several room-scoped flows, event publication is inconsistent, and large service classes such as `RoomService` carry too many responsibilities.

This can be stabilized mostly by fixing services only. The most important work is to secure room-scoped flows, clean up realtime routing, and make each service publish/consume events according to a clear role.

What should not be touched yet:

- Do not redesign `common-*` modules as the first move.
- Do not rewrite all service package structures at once.
- Do not remove domain-service websocket endpoints until realtime-edge authorization and gateway routing are correct.
- Do not split `RoomService` broadly before the authorization and event-flow bugs are fixed.
- Do not introduce a new architecture pattern until the current service boundaries are made coherent.
