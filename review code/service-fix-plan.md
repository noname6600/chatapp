# Service Fix Plan

## 1. What this document is
- This is not a new broad architecture review.
- This is an execution-oriented service refactor plan derived from review code/full-service-review.md and aligned with the latest narrow-pass outcomes already completed.
- Focus is service-first and common-last.
- Scope is limited to:
  - gateway-service
  - auth-service
  - user-service
  - friendship-service
  - chat-service
  - presence-service
  - notification-service
  - upload-service
  - realtime-edge-service
- Common modules are out of scope unless a service-level correctness bug is impossible to fix without common changes.

## 2. Confirmed Service-Critical Issues

### Critical

1) Duplicate websocket ownership and non-authoritative edge ingress
- Affected service(s): gateway-service, realtime-edge-service, chat-service, presence-service, friendship-service, notification-service
- Exact files/packages:
  - chatappBE/gateway-service/src/main/resources/application.yaml
  - chatappBE/realtime-edge-service/src/main/java/com/example/realtime/config/WebSocketConfig.java
  - chatappBE/chat-service/src/main/java/com/example/chat/realtime/websocket/handler/ChatWebSocketHandler.java
  - chatappBE/presence-service/src/main/java/com/example/presence/websocket/handler/PresenceWebSocketHandler.java
  - chatappBE/friendship-service/src/main/java/com/example/friendship/websocket/FriendshipWebSocketHandler.java
  - chatappBE/notification-service/src/main/java/com/example/notification/websocket/NotificationWebSocketHandler.java
- Why dangerous:
  - Two active websocket ownership models produce inconsistent authorization and delivery behavior.
- Type:
  - runtime architecture
  - correctness
- Best fix approach:
  - Service-side pre-cutover hardening first: lock down edge authorization and room checks; keep legacy websocket endpoints temporarily.
  - Delay gateway websocket cutover until service-side guards are complete.
- Fix scope:
  - service-only

2) Missing room membership authorization in room-scoped data and subscription flows
- Affected service(s): chat-service, presence-service, realtime-edge-service
- Exact files/packages:
  - chatappBE/chat-service/src/main/java/com/example/chat/realtime/websocket/session/ChatSessionRegistry.java
  - chatappBE/chat-service/src/main/java/com/example/chat/realtime/websocket/handler/ChatWebSocketHandler.java
  - chatappBE/chat-service/src/main/java/com/example/chat/modules/message/application/query/MessageQueryService.java
  - chatappBE/chat-service/src/main/java/com/example/chat/modules/message/application/pipeline/reaction/steps/ValidateReactionStep.java
  - chatappBE/chat-service/src/main/java/com/example/chat/modules/message/application/pipeline/reaction/steps/PersistReactionStep.java
  - chatappBE/chat-service/src/main/java/com/example/chat/modules/room/controller/RoomController.java
  - chatappBE/presence-service/src/main/java/com/example/presence/controller/PresenceController.java
  - chatappBE/presence-service/src/main/java/com/example/presence/controller/PresenceEdgeCommandController.java
  - chatappBE/realtime-edge-service/src/main/java/com/example/realtime/subscription/ChannelSubscriptionManager.java
- Why dangerous:
  - Unauthorized users can subscribe/read room presence/chat state.
- Type:
  - security
  - correctness
- Best fix approach:
  - Add service-local membership guard interface in chat-service and call it from room-scoped reads/mutations.
  - Add presence-side authorization adapter using chat internal membership check.
  - Enforce edge-side subscription authorization via downstream membership check before ROOM/PRESENCE/TYPING subscriptions.
- Fix scope:
  - service-first, common optional

3) Edge chat JOIN path can trigger domain membership mutation
- Affected service(s): realtime-edge-service, chat-service
- Exact files/packages:
  - chatappBE/realtime-edge-service/src/main/java/com/example/realtime/adapter/in/websocket/RealtimeWebSocketHandler.java
  - chatappBE/realtime-edge-service/src/main/java/com/example/realtime/adapter/out/chat/RestChatCommandRouter.java
  - chatappBE/chat-service/src/main/java/com/example/chat/modules/room/controller/RoomController.java
  - chatappBE/chat-service/src/main/java/com/example/chat/modules/room/service/impl/RoomService.java
- Why dangerous:
  - Websocket subscribe semantics and room-membership mutation are conflated.
- Type:
  - security
  - correctness
  - runtime architecture
- Best fix approach:
  - Replace edge JOIN routing to public room join endpoint with subscription-only flow + explicit membership verification endpoint.
- Fix scope:
  - service-only

4) Upload confirmation trusts client metadata and chat still has direct Cloudinary upload path
- Affected service(s): upload-service, chat-service
- Exact files/packages:
  - chatappBE/upload-service/src/main/java/com/example/upload/service/UploadSigningService.java
  - chatappBE/upload-service/src/main/java/com/example/upload/dto/ConfirmUploadRequest.java
  - chatappBE/chat-service/src/main/java/com/example/chat/modules/room/controller/RoomController.java
  - chatappBE/chat-service/src/main/java/com/example/chat/modules/room/service/CloudinaryService.java
  - chatappBE/chat-service/src/main/java/com/example/chat/config/CloudinaryConfig.java
- Why dangerous:
  - Allows forged upload confirmation metadata and keeps dual upload ownership active.
- Type:
  - security
  - correctness
  - runtime architecture
- Best fix approach:
  - Upload-service must verify asset server-side or via prepare-token correlation.
  - Decommission chat direct upload endpoint after upload-service path is validated in chat room avatar flow.
- Fix scope:
  - service-only

### High

5) Publish-in-transaction and fire-and-forget async event publication
- Affected service(s): auth-service, friendship-service, notification-service, chat-service
- Exact files/packages:
  - chatappBE/auth-service/src/main/java/com/example/auth/service/impl/LocalAuthService.java
  - chatappBE/auth-service/src/main/java/com/example/auth/service/impl/OAuthAuthService.java
  - chatappBE/friendship-service/src/main/java/com/example/friendship/service/impl/FriendCommandService.java
  - chatappBE/notification-service/src/main/java/com/example/notification/service/impl/NotificationCommandService.java
  - chatappBE/chat-service/src/main/java/com/example/chat/modules/message/application/pipeline/send/steps/PublishMessageEventStep.java
- Why dangerous:
  - Can emit rolled-back facts or lose events under threadpool/failure conditions.
- Type:
  - correctness
  - runtime architecture
- Best fix approach:
  - Move to service-local after-commit publication abstraction first.
  - Keep outbox as a later phase where needed.
- Fix scope:
  - service-only

6) Internal APIs publicly permitted in user/friendship
- Affected service(s): user-service, friendship-service
- Exact files/packages:
  - chatappBE/user-service/src/main/java/com/example/user/configuration/SecurityConfig.java
  - chatappBE/user-service/src/main/java/com/example/user/controller/UserProfileController.java
  - chatappBE/friendship-service/src/main/java/com/example/friendship/configuration/SecurityConfig.java
  - chatappBE/friendship-service/src/main/java/com/example/friendship/controller/InternalFriendController.java
- Why dangerous:
  - Internal trust boundary can be bypassed if service is reachable.
- Type:
  - security
- Best fix approach:
  - Add service-local internal auth filter/token header validation for internal paths.
- Fix scope:
  - service-only

7) Chat event contract collision for chat.message.pinned/unpinned
- Affected service(s): chat-service
- Exact files/packages:
  - chatappBE/chat-service/src/main/java/com/example/chat/config/ChatRedisEventConfig.java
  - chatappBE/chat-service/src/main/java/com/example/chat/modules/room/dto/RoomMessagePinEventPayload.java
  - chatappBE/chat-service/src/main/java/com/example/chat/realtime/subscriber/ChatMessagePinnedRedisSubscriber.java
  - chatappBE/chat-service/src/main/java/com/example/chat/realtime/subscriber/ChatMessageUnpinnedRedisSubscriber.java
- Why dangerous:
  - Conflicting event type registrations break context/test reliability.
- Type:
  - correctness
  - maintainability
- Best fix approach:
  - Use one external payload contract type for external event registration; map internally if needed.
- Fix scope:
  - service-only

8) Auth token issuance path coupled to user profile readiness polling
- Affected service(s): auth-service, user-service
- Exact files/packages:
  - chatappBE/auth-service/src/main/java/com/example/auth/service/impl/AuthSessionService.java
  - chatappBE/auth-service/src/main/java/com/example/auth/service/impl/UserProfileReadinessService.java
  - chatappBE/auth-service/src/main/java/com/example/auth/client/UserServiceClient.java
- Why dangerous:
  - Login/registration availability degrades when user-service is slow/down.
- Type:
  - correctness
  - runtime architecture
- Best fix approach:
  - Decouple token issuance from profile readiness and expose profile status separately.
- Fix scope:
  - service-only

### Medium

9) Gateway readiness semantics too permissive
- Affected service(s): gateway-service
- Exact files/packages:
  - chatappBE/gateway-service/src/main/java/com/example/gateway/health/DownstreamReadinessIndicator.java
- Why dangerous:
  - Treating 4xx as healthy can mask misrouting/auth failure.
- Type:
  - correctness
  - runtime architecture
- Best fix approach:
  - Restrict readiness checks to health endpoints or explicit accepted statuses.
- Fix scope:
  - service-only

10) Durable vs transient event role confusion (Kafka vs Redis vs local websocket)
- Affected service(s): chat-service, presence-service, notification-service, realtime-edge-service
- Exact files/packages:
  - chatappBE/chat-service/src/main/java/com/example/chat/modules/message/infrastructure/adapter/ChatMessageEventPublisherAdapter.java
  - chatappBE/chat-service/src/main/java/com/example/chat/realtime/infrastructure/ChatRealtimeAdapter.java
  - chatappBE/presence-service/src/main/java/com/example/presence/redis/PresenceRedisPublisher.java
  - chatappBE/notification-service/src/main/java/com/example/notification/websocket/NotificationWebSocketPublisher.java
  - chatappBE/realtime-edge-service/src/main/java/com/example/realtime/adapter/in/kafka/KafkaEventConsumer.java
- Why dangerous:
  - Semantically same events routed through multiple channels create duplication/drift.
- Type:
  - runtime architecture
  - correctness
- Best fix approach:
  - Define per-service source-of-truth stream and reduce mixed duplicate publication paths incrementally.
- Fix scope:
  - service-first, common optional

11) Excluded tests hide risky service contract areas
- Affected service(s): chat-service, friendship-service, notification-service
- Exact files/packages:
  - chatappBE/chat-service/build.gradle
  - chatappBE/friendship-service/build.gradle
  - chatappBE/notification-service/build.gradle
- Why dangerous:
  - Realtime and event contract regressions can slip through.
- Type:
  - maintainability
  - correctness
- Best fix approach:
  - Keep quarantine explicit, then restore in small chunks after each targeted refactor.
- Fix scope:
  - service-only

### Low

12) Inconsistent package/adapter naming across services
- Affected service(s): all scoped services
- Exact files/packages:
  - service-local adapter/client/publisher/subscriber classes across services
- Why dangerous:
  - Slows onboarding and refactor safety.
- Type:
  - maintainability
- Best fix approach:
  - Apply naming convergence only when touching files for real fixes.
- Fix scope:
  - service-only

13) God-service maintainability hotspots
- Affected service(s): chat-service, user-service, notification-service
- Exact files/packages:
  - chatappBE/chat-service/src/main/java/com/example/chat/modules/room/service/impl/RoomService.java
  - chatappBE/user-service/src/main/java/com/example/user/service/impl/UserProfileService.java
  - chatappBE/notification-service/src/main/java/com/example/notification/service/impl/NotificationCommandService.java
- Why dangerous:
  - Change risk and testability degrade.
- Type:
  - maintainability
- Best fix approach:
  - Incremental extraction by use case after security/correctness fixes.
- Fix scope:
  - service-only

## 3. Service-by-Service Action List

### gateway-service

#### Fix now
- Keep current state from latest narrow pass (compileTest fix done).
- Tighten DownstreamReadinessIndicator health criteria.
- Confirm and document that websocket gateway cutover is deferred until edge authorization fixes complete.

#### Fix next
- Add explicit websocket routing plan to realtime-edge-service (feature-gated, not active yet).
- Resolve route ambiguity for notification room-mute ownership paths if still under chat-owned prefix.

#### Leave for later
- Broader route organization cleanup and naming polish.

#### Do not touch yet
- Do not switch gateway websocket ingress to edge before edge room/subscription authorization is hardened.

### auth-service

#### Fix now
- Remove user-profile readiness polling from token issuance path.
- Keep account-created event publication but move to after-commit mechanism.

#### Fix next
- Consolidate auth session orchestration around stateless token issuance path.
- Add focused resiliency tests for user-service degraded conditions.

#### Leave for later
- Package naming convergence and non-critical config polish.

#### Do not touch yet
- Do not do broad security-stack rewrite.

### user-service

#### Fix now
- Internal endpoint protection for /api/v1/users/internal/** with service-token gate.
- Add stricter null/shape guard in AccountCreatedConsumer.

#### Fix next
- Small extraction: profile read/update vs avatar metadata assignment.
- Add request-size validation for bulk user lookup endpoints.

#### Leave for later
- Schema-fixer retirement to migration-only path.

#### Do not touch yet
- Do not reintroduce direct upload ownership.

### friendship-service

#### Fix now
- Internal endpoint protection for /api/v1/internal/** with service-token gate.
- Move event publishing to after-commit.

#### Fix next
- Remove synchronous display-name enrichment from FriendshipEventProducer (ID-first events).
- Add fallback/read-model enrichment in consuming service instead.

#### Leave for later
- Legacy websocket package removal after edge cutover.

#### Do not touch yet
- Do not remove rollback websocket handler before gateway cutover and edge parity.

### chat-service

#### Fix now
- Add room membership authorization checks to:
  - MessageQueryService
  - reaction validation/persist flow
  - room member/member-count/room-code endpoints in RoomController
  - local websocket room join in ChatWebSocketHandler
- Resolve chat.message.pinned payload registration collision (single external contract registration).
- Keep quarantined stale tests explicit; do not unquarantine until core fixes are complete.

#### Fix next
- Replace PublishMessageEventStep fire-and-forget async path with after-commit publication.
- Split RoomService around membership authorization and metadata query boundaries only.

#### Leave for later
- Full room module decomposition.

#### Do not touch yet
- Do not remove legacy websocket endpoint until edge cutover.
- Do not remove legacy avatar upload endpoint until upload-service confirmation hardening + chat integration is proven.

### presence-service

#### Fix now
- Keep latest narrow-pass disconnect fix baseline.
- Add room membership authorization checks for room presence and typing paths.
- Protect PresenceEdgeCommandController as internal-only service ingress.

#### Fix next
- Validate Redis keyspace notification operational requirement and enforce via startup check/log policy.
- Align TTL/auto-away thresholds and add focused tests.

#### Leave for later
- Package refactor into adapter layers.

#### Do not touch yet
- Do not remove rollback websocket endpoint before edge cutover.

### notification-service

#### Fix now
- Keep latest narrow-pass Kafka consumer delegation consolidation baseline.
- Move push side effects out of transaction body to after-commit delivery trigger.
- Add room membership authorization checks for room-mute APIs via chat authorization adapter.

#### Fix next
- Add durable dedupe key strategy for consumed events.
- Reduce duplicate logic between consumers and application service paths.

#### Leave for later
- Full notification application service structure cleanup.

#### Do not touch yet
- Do not remove rollback websocket path before edge cutover.

### upload-service

#### Fix now
- Replace client-trusted confirm with server-verified confirm (Cloudinary fetch/verify) or prepare-token correlation.
- Bind confirm to prepared upload ownership, policy, folder, and constraints.

#### Fix next
- Add stronger policy-specific size/type validation at prepare and confirm stages.

#### Leave for later
- Internal package reshaping.

#### Do not touch yet
- Do not shift ownership of media verification back into chat/user services.

### realtime-edge-service

#### Fix now
- Keep latest narrow-pass notification command URL fix baseline.
- Fix /ws/chat JOIN behavior to subscription-only with membership authorization check.
- Harden ChannelSubscriptionManager for ROOM/PRESENCE/TYPING authorization.

#### Fix next
- Decide fate of disabled generic KafkaEventConsumer/EventDeliveryService placeholder path:
  - either complete and enable safely, or
  - remove/quarantine to avoid false architecture signals.
- Align Redis config style and dependency BOM with backend baseline.

#### Leave for later
- Cross-instance delivery optimizations after security/correctness parity.

#### Do not touch yet
- Do not perform gateway cutover until edge subscription authorization and join semantics are fixed.

## 4. Exact Top Priority Refactors

1) Chat room-scoped authorization hardening
- service: chat-service
- target files/classes:
  - MessageQueryService
  - ValidateReactionStep
  - PersistReactionStep
  - RoomController
  - ChatWebSocketHandler
  - ChatSessionRegistry (if needed for safe join guard)
- exact problem:
  - Missing membership checks on room-scoped operations.
- exact intended outcome:
  - Unauthorized room reads/subscriptions/reactions are rejected.
- risk level:
  - Medium
- dependency:
  - None

2) Edge websocket JOIN semantic correction
- service: realtime-edge-service (+chat-service internal check endpoint if needed)
- target files/classes:
  - RealtimeWebSocketHandler
  - RestChatCommandRouter
  - ChannelSubscriptionManager
  - chat internal auth endpoint for membership (new or reused)
- exact problem:
  - JOIN can mutate room membership via chat public join endpoint.
- exact intended outcome:
  - JOIN only subscribes after membership authorization.
- risk level:
  - Medium/High
- dependency:
  - Refactor 1 provides shared membership guard semantics.

3) Presence room/typing authorization hardening
- service: presence-service
- target files/classes:
  - PresenceController
  - PresenceEdgeCommandController
  - PresenceWebSocketHandler
  - service-local authorization adapter (new)
- exact problem:
  - Room presence/typing actions lack membership checks.
- exact intended outcome:
  - Presence room actions require verified room membership.
- risk level:
  - Medium
- dependency:
  - Chat membership authorization API/adapter contract.

4) Internal endpoint protection
- service: user-service, friendship-service
- target files/classes:
  - user SecurityConfig + internal controller path protection
  - friendship SecurityConfig + InternalFriendController protection
- exact problem:
  - Internal APIs are permitAll at service layer.
- exact intended outcome:
  - Internal APIs require service credential.
- risk level:
  - Low/Medium
- dependency:
  - None

5) Upload confirm hardening and chat direct upload retirement prep
- service: upload-service, chat-service
- target files/classes:
  - UploadSigningService
  - ConfirmUploadRequest handling path
  - RoomController uploadAvatarLegacy
  - CloudinaryService/CloudinaryConfig in chat
- exact problem:
  - Client-trusted confirm and dual upload ownership.
- exact intended outcome:
  - Upload-service confirms ownership/constraints server-side; chat direct upload path can be safely deprecated/removed.
- risk level:
  - Medium
- dependency:
  - None

6) After-commit publication standardization (service-local)
- service: auth-service, friendship-service, notification-service, chat-service
- target files/classes:
  - LocalAuthService, OAuthAuthService
  - FriendCommandService
  - NotificationCommandService
  - PublishMessageEventStep
- exact problem:
  - In-transaction publication and unreliable async publication patterns.
- exact intended outcome:
  - Stable after-commit publication with predictable failure behavior.
- risk level:
  - Medium/High
- dependency:
  - None (can be phased by service)

## 5. Safe First Refactor Batch

Batch objective:
- service-local only
- no common redesign
- no broad package rewrite
- no gateway cutover yet
- no websocket endpoint removal yet
- correctness/security first

Batch contents:
1. user-service internal API auth gate.
2. friendship-service internal API auth gate.
3. chat-service room membership checks on room-scoped read/reaction/subscription paths.
4. realtime-edge-service JOIN semantic fix (subscription-only) and ROOM/PRESENCE/TYPING subscription authorization guard.
5. presence-service room presence/typing authorization checks via service-local adapter.
6. upload-service confirm hardening (token correlation/server verification).
7. Fix chat pin-event payload registration collision in chat-service context.

Why this batch first:
- Highest security and correctness risk reduction with minimal architecture risk.
- No gateway or common-module redesign required.

## 6. Realtime Migration Preparation

### gateway-service
- what is still duplicated:
  - Gateway routes websocket traffic to domain services while edge also exposes websocket endpoints.
- what authorization is missing:
  - No edge-authoritative policy yet; still route-level legacy mode.
- what endpoint/handler is unsafe:
  - Legacy websocket routes are not unsafe by themselves, but they preserve dual ownership.
- what must change before cutover:
  - Cutover only after edge JOIN/subscription authorization is fixed and domain websocket parity is validated.

### chat-service
- what is still duplicated:
  - Local websocket ownership overlaps with edge websocket aliases.
- what authorization is missing:
  - Membership checks in room-scoped read/reaction/subscription flows.
- what endpoint/handler is unsafe:
  - Local websocket JOIN without membership verification.
- what must change before cutover:
  - Enforce membership checks and expose minimal internal membership authorization endpoint for edge/presence.

### presence-service
- what is still duplicated:
  - Local websocket and edge command ingress both active.
- what authorization is missing:
  - Room membership validation for room/typing operations.
- what endpoint/handler is unsafe:
  - PresenceEdgeCommandController and room presence APIs without strict room auth.
- what must change before cutover:
  - Internal auth + room membership checks wired and tested.

### friendship-service
- what is still duplicated:
  - Local websocket handler retained while edge consumes friendship events.
- what authorization is missing:
  - Internal controller path trust boundary.
- what endpoint/handler is unsafe:
  - Internal endpoints currently too permissive.
- what must change before cutover:
  - Internal API auth gate and event publication correctness.

### notification-service
- what is still duplicated:
  - Local websocket delivery path and edge-capable path coexist.
- what authorization is missing:
  - Room mute operations need room authorization.
- what endpoint/handler is unsafe:
  - Room-scoped preference endpoints if unguarded by membership.
- what must change before cutover:
  - After-commit push and room auth checks.

### realtime-edge-service
- what is still duplicated:
  - Edge aliases mirror domain websocket paths while gateway still points to domain services.
- what authorization is missing:
  - Domain-aware channel authorization for ROOM/PRESENCE/TYPING.
- what endpoint/handler is unsafe:
  - Legacy /ws/chat JOIN path currently conflates subscribe with room mutation unless fixed.
- what must change before cutover:
  - Join semantics fixed, subscription auth hardened, command routes validated, then gateway cutover.

## 7. Common Changes That Are Truly Unavoidable
- At this stage, no common-module change is strictly required to execute the priority service fixes above.
- Common changes remain optional only if a later step proves a service cannot express authorization/event contracts with existing service-local adapters.

## 8. Final Recommended Execution Order
1. Security first
- chat room membership checks
- edge subscription authorization and JOIN semantic fix
- presence room authorization
- user/friendship internal API protection

2. Correctness second
- chat pin-event contract collision fix
- upload confirm hardening and chat direct upload retirement preparation
- notification room-mute authorization validation

3. Event flow third
- after-commit publication in auth/friendship/notification/chat
- reduce risky in-transaction or fire-and-forget publication paths

4. Realtime cutover preparation fourth
- finalize edge authorization parity
- keep domain websocket endpoints temporarily
- validate end-to-end behavior under dual mode
- then cut gateway websocket routing to edge

5. Cleanup later
- restore quarantined tests gradually
- incremental god-service decomposition
- config/BOM/style convergence where safe
- websocket endpoint retirement in domain services only after stable edge ownership
