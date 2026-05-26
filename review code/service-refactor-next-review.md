# 1. Executive Summary

Date: May 13, 2026

Scope reviewed:
- `gateway-service`
- `auth-service`
- `user-service`
- `notification-service`
- `presence-service`
- `chat-service`
- `friendship-service`
- `upload-service`
- `realtime-edge-service`

Rule applied: service-only review, with `common/*` treated as frozen except for read-only contract checks and proven blockers.

Current overall state: main service code is materially cleaner than the previous service-only architecture review. A fresh `compileJava` sweep across all nine scoped services passed. Package declarations now match physical paths across the scoped service main trees. The old `realtime-edge-service` `com.example.realtimeedge` skeleton is gone, the prior common-websocket import BOM blocker is gone, the realtime-edge port config is fixed, and the worst duplicate room/friendship realtime artifacts were removed.

However, the backend is not clean enough to move mainly into validation yet. There are still service-local blockers and validation blockers that should be fixed before relying on runtime execution:
- `presence-service` direct websocket rollback disconnect still appears wrong: `PresenceWebSocketHandler` calls lifecycle cleanup before unregistering the closing session, but `PresenceConnectionLifecycleAdapter` checks `sessionRegistry.isUserOnline(userId)`, which still counts the closing session. This can still skip `presenceService.offline(...)` for last-session disconnects. Evidence: `presence-service/src/main/java/com/example/presence/websocket/handler/PresenceWebSocketHandler.java:195`, `:197`; `presence-service/src/main/java/com/example/presence/websocket/adapter/PresenceConnectionLifecycleAdapter.java:80`, `:84`; `presence-service/src/main/java/com/example/presence/websocket/session/PresenceSessionRegistry.java:116`.
- `realtime-edge-service` notification command forwarding defaults to the chat-service port and has no configured `services.notification.url` entry. Evidence: `realtime-edge-service/src/main/java/com/example/realtime/adapter/out/notification/RestNotificationCommandRouter.java:35`; `realtime-edge-service/src/main/resources/application.yaml:58`.
- `gateway-service:compileTestJava` fails because `GatewayCorsIntegrationTest` still instantiates `new GatewayConfig()` after production `GatewayConfig` gained a required `CorsProperties` constructor dependency. Evidence: `gateway-service/src/test/java/com/example/gateway/config/GatewayCorsIntegrationTest.java:24`, `:42`, `:59`, `:75`; production constructor field at `gateway-service/src/main/java/com/example/gateway/config/GatewayConfig.java:20`.
- `chat-service:compileTestJava` still fails on stale realtime contract tests that reference removed/nonexistent common APIs. Evidence: `chat-service/src/test/java/com/example/chat/realtime/contract/RealtimeContractBaselineTest.java:6`; `RealtimeContractValidatorTest.java:3-5`; `RealtimeMessagingAlignmentTest.java:4-9`.

What improved after the latest cleanup:
- Service `compileJava` passes for all scoped services.
- `KafkaEventProducer` missing-bean risk was handled service-locally with `KafkaProducerAdapterConfig` in auth, chat, friendship, and notification; common stayed frozen.
- The common-websocket import file now starts with `com`, not a BOM byte sequence.
- `realtime-edge-service/src/main/resources/application.yaml` now uses top-level `server.port: 8090`.
- `notification-service` friend request consumer now listens to `KafkaTopics.TOPIC_FRIENDSHIP_REQUEST_EVENTS`.
- `user-service` no longer depends on `upload-service` and now uses local `AvatarAssetMetadata`.
- `user-service` account-created Kafka ingress now delegates to `UserKafkaAccountCreatedApplicationService`.
- The unused chat room application-service set, unused chat/presence/friendship realtime publisher interfaces, inactive friendship outbound websocket publishers, duplicate notification realtime adapter, and stale realtime-edge skeleton were removed.
- Gateway websocket route posture is documented as legacy service-local ingress until edge cutover.

Whether another refactor pass is needed: yes, but it should be a narrow service-only pass, not a broad rewrite. Fix the remaining blockers and prune clearly dead service-local transitional code. Do not reopen common.

# 2. Frozen Common Status

No proven remaining common blocker was found in this pass.

Evidence:
- The scoped `compileJava` sweep passed after the service-local Kafka adapter fixes.
- The common-websocket auto-configuration imports file now reads `com.example.common.websocket.config.RealtimeWebSocketAutoConfiguration` and its first bytes are `63 6F 6D`, not `EF BB BF`.
- The remaining failures found are service-owned: presence disconnect lifecycle, realtime-edge notification routing config, gateway stale test construction, and chat stale service tests.

Common should remain untouched. The current next pass should not redesign common Kafka, common Redis, common websocket, common event contracts, or common security. The service-local `KafkaProducerAdapterConfig` bridge classes should stay for now under the frozen-common rule.

# 3. Service-by-Service Re-Review

## gateway-service

Current responsibility: API gateway for backend HTTP routes, auth filtering, CORS, fallback behavior, and transitional service-local websocket routing.

Structure state now: clear. Main packages are compact: `config`, `controller`, `filter`, and `health`. Package and path layout is acceptable.

Layer separation: acceptable for a gateway. HTTP routes, JWT filtering, and CORS are isolated. Websocket routes remain transitional and service-local.

Rollback-safe path state: legacy websocket routes remain in `application.yaml`:
- `chat-service-ws` at `gateway-service/src/main/resources/application.yaml:85` with `/ws/chat/**` at `:90`
- `presence-service-ws` at `:108` with `/ws/presence/**` at `:111`
- `friendship-service-ws` at `:129` with `/ws/friendship/**` at `:132`
- `notification-service-ws` at `:150` with `/ws/notifications/**` at `:153`

This is acceptable as rollback compatibility. The chat websocket route includes an explicit comment that service-local ingress stays until realtime-edge ingress is cut over.

Debt/transitional code still present:
- `GatewayCorsIntegrationTest` is stale and fails test compilation by constructing `GatewayConfig` with no arguments at `gateway-service/src/test/java/com/example/gateway/config/GatewayCorsIntegrationTest.java:24`, `:42`, `:59`, `:75`.
- Gateway does not currently route `/realtime` or the legacy `/ws/*` aliases to `realtime-edge-service`. This is cleanup only unless gateway is required to own edge ingress now.

Confidence:
- Compile confidence: High. Main compile passed.
- Runtime confidence: Medium/High. Main gateway structure is small; websocket edge cutover is intentionally not done.
- Structural cleanliness: High.
- Legacy/debt burden: Low/Medium.

Decision: leave main code mostly as-is. Refactor now only to fix the stale gateway CORS test if test compilation is part of the validation gate. Revisit websocket route cutover later.

## auth-service

Current responsibility: registration, login, OAuth exchange, refresh/logout, password flows, verification, JWT/JWKS, and account-created event publication.

Structure state now: clearer than before. Packages are conventional: `controller`, `service`, `repository`, `entity`, `jwt`, `kafka`, `scheduler`, `integration.resend`, and `configuration`. Package/path layout is acceptable.

Layer separation: acceptable. `AccountCreatedEventProducer` remains an infrastructure publisher using `KafkaEventProducer`, while `KafkaProducerAdapterConfig` bridges it to the frozen common `KafkaEventPublisher` bean at `auth-service/src/main/java/com/example/auth/configuration/KafkaProducerAdapterConfig.java:20`.

Rollback-safe path state: no major rollback websocket path in this service.

Debt/transitional code still present:
- `DatabaseSchemaFixer` still runs startup DDL for `accounts.email_verified` at `auth-service/src/main/java/com/example/auth/configuration/DatabaseSchemaFixer.java:16`, `:22`. This is migration debt, not a safe structural removal without schema-state proof.
- The Kafka adapter bridge is service-local compatibility code. Keep it for now under frozen-common.

What improved:
- `SecurityConfig` now permits only explicit public auth endpoints at `auth-service/src/main/java/com/example/auth/configuration/SecurityConfig.java:43-60`, instead of permitting all `/api/v1/auth/**`.
- `AuthController` now uses `requirePrincipal(...)` for authenticated operations such as logout-all, password change, verification status, and verification send at `auth-service/src/main/java/com/example/auth/controller/AuthController.java:81`, `:90`, `:104`, `:113`, with the helper at `:141`.

Confidence:
- Compile confidence: High.
- Runtime confidence: High/Medium. No current service-local startup blocker found; startup DDL remains operational debt.
- Structural cleanliness: Medium/High.
- Legacy/debt burden: Medium.

Decision: good enough to leave alone for now. Refactor later: remove `DatabaseSchemaFixer` only after normal migration state is verified.

## user-service

Current responsibility: user profile lifecycle, profile lookup/search, avatar metadata acceptance, and profile creation from account-created events.

Structure state now: clearer than before. Packages are acceptable: `application`, `controller`, `service`, `repository`, `dto`, `kafka`, `configuration`, `utils`. Package/path layout is acceptable.

Layer separation: improved. `AccountCreatedConsumer` is now a thin ingress adapter delegating to `UserKafkaAccountCreatedApplicationService` at `user-service/src/main/java/com/example/user/kafka/AccountCreatedConsumer.java:18`, `:20`; orchestration lives at `user-service/src/main/java/com/example/user/application/UserKafkaAccountCreatedApplicationService.java:17`.

Rollback-safe path state: no websocket rollback path.

Debt/transitional code still present:
- Direct Cloudinary upload infrastructure remains even though the live profile endpoint now accepts avatar metadata. Evidence:
  - dependency at `user-service/build.gradle:42`
  - `CloudinaryConfig` at `user-service/src/main/java/com/example/user/configuration/CloudinaryConfig.java:10`
  - `CloudinaryService` at `user-service/src/main/java/com/example/user/service/impl/CloudinaryService.java:17`
  - `CloudinaryUploadResult` at `user-service/src/main/java/com/example/user/dto/CloudinaryUploadResult.java:8`
  - the active controller endpoint calls `applyAvatarMetadata` at `user-service/src/main/java/com/example/user/controller/UserProfileController.java:113-119`
  - active service code converts to local `AvatarAssetMetadata` at `user-service/src/main/java/com/example/user/service/impl/UserProfileService.java:190`, `:232`, `:244`
- `DatabaseSchemaFixer` still runs startup uniqueness DDL at `user-service/src/main/java/com/example/user/configuration/DatabaseSchemaFixer.java:16`, `:36`.
- `AccountCreatedConsumer` still has an unused local `accountId` variable at `user-service/src/main/java/com/example/user/kafka/AccountCreatedConsumer.java:22`; minor cleanup only.

What improved:
- No direct `upload-service` Gradle dependency remains.
- Upload metadata boundary is now local via `AvatarAssetMetadata`.
- Account-created creation logic has a single active application-service path.

Confidence:
- Compile confidence: High.
- Runtime confidence: Medium/High.
- Structural cleanliness: Medium/High.
- Legacy/debt burden: Medium.

Decision: refactor now if doing a dead-code sweep: remove the unused direct Cloudinary upload stack from `user-service`, but keep the `cloudinary.cloud-name` property if `AvatarGenerator` still needs it. Defer `DatabaseSchemaFixer` until migration proof exists.

## notification-service

Current responsibility: notification persistence, unread counts, room notification settings, realtime fanout, and event consumption from account/chat/reaction/friendship streams.

Structure state now: still mixed. Packages are understandable (`application`, `kafka`, `service`, `websocket`, `realtime`, `controller`), but application and Kafka layers overlap.

Layer separation: not clean enough. `FriendRequestEventConsumer` now delegates to `NotificationFriendRequestEventApplicationService` and listens to the current aggregate topic at `notification-service/src/main/java/com/example/notification/kafka/FriendRequestEventConsumer.java:17`, `:20`, `:36`. But message and reaction consumers still duplicate the logic that also exists in application services:
- `MessageCreatedEventConsumer` contains direct notification policy/repository/command orchestration at `notification-service/src/main/java/com/example/notification/kafka/MessageCreatedEventConsumer.java:23-128`.
- The same business handling exists in `NotificationMessageEventApplicationService` at `notification-service/src/main/java/com/example/notification/application/NotificationMessageEventApplicationService.java:21`.
- `ReactionEventConsumer` contains direct reaction notification logic at `notification-service/src/main/java/com/example/notification/kafka/ReactionEventConsumer.java:17-81`.
- The same reaction handling exists in `NotificationReactionEventApplicationService` at `notification-service/src/main/java/com/example/notification/application/NotificationReactionEventApplicationService.java:15`.
- `NotificationKafkaEventApplicationService` exists at `notification-service/src/main/java/com/example/notification/application/NotificationKafkaEventApplicationService.java:21`, but is not used by main code; `rg` finds it only in tests and its own class.

Rollback-safe path state: notification service-local websocket/Redis fanout remains active. The duplicate deprecated `NotificationRedisRealtimeAdapter` is gone, which is an improvement. `NotificationWebSocketPublisher` is now the active `NotificationRealtimePort` implementation at `notification-service/src/main/java/com/example/notification/websocket/NotificationWebSocketPublisher.java:49`.

Debt/transitional code still present:
- `NotificationRealtimeEventPublisher` is an unused interface at `notification-service/src/main/java/com/example/notification/realtime/publisher/NotificationRealtimeEventPublisher.java:12`; no main/test reference uses it.
- `NotificationWebSocketPublisher` still contains stale text saying `NotificationRedisRealtimeAdapter` should be removed at `notification-service/src/main/java/com/example/notification/websocket/NotificationWebSocketPublisher.java:39-41`, even though it already has been.
- Flow classification TODOs remain in `NotificationWebSocketPublisher` at `:74`, `:81`.
- `DatabaseSchemaFixer` runs startup DDL and drops a constraint at `notification-service/src/main/java/com/example/notification/configuration/DatabaseSchemaFixer.java:16`, `:23`, `:29`.
- Multiple notification tests are excluded in `notification-service/build.gradle:59-64`, and `ChatappApplicationTests` is excluded at `:77`.

What improved:
- The wrong friendship topic listener is fixed.
- The duplicate realtime adapter was removed.
- Friend-request consumer is thin now.

Confidence:
- Compile confidence: High.
- Runtime confidence: Medium. Main compile is good and the friend-request topic is fixed, but duplicate logic and excluded tests reduce confidence.
- Structural cleanliness: Medium/Low.
- Legacy/debt burden: High.

Decision: refactor now after blocker cleanup. This is still the messiest service structurally because duplicate application/Kafka paths are present as Spring beans and tests are excluded around exactly that boundary.

## presence-service

Current responsibility: presence state, room presence, typing events, Redis publication/subscription, direct websocket rollback ingress, and realtime-edge HTTP command ingress.

Structure state now: mostly clear. Packages separate `controller`, `service`, `realtime.port`, `redis`, `state`, and `websocket`. The edge command controller is an acceptable boundary.

Layer separation: better than before, but direct websocket lifecycle and edge HTTP lifecycle still coexist. That is acceptable as rollback compatibility only if both paths are correct.

Rollback-safe path state: direct websocket remains at `presence-service/src/main/java/com/example/presence/configuration/WebSocketConfig.java:14`, using `PresenceWebSocketHandler`. Edge ingress is separated at `presence-service/src/main/java/com/example/presence/controller/PresenceEdgeCommandController.java:39`, with `/api/v1/presence/ws/**` endpoints.

Debt/transitional code still present:
- The direct websocket disconnect path still appears buggy. `PresenceWebSocketHandler.afterConnectionClosed` captures rooms at `:190`, removes the session from all rooms at `:192`, calls lifecycle cleanup at `:195`, and unregisters at `:197`. The adapter then calls `sessionRegistry.isUserOnline(userId)` at `PresenceConnectionLifecycleAdapter.java:80`, which still counts the closing session because unregister has not happened yet. That means `presenceService.offline(userId)` at `:84` can still be skipped for the last direct websocket session.
- The local `rooms` variable captured at `PresenceWebSocketHandler.java:190` is not used, and the adapter's later `getRoomsOfUser(userId)` at `PresenceConnectionLifecycleAdapter.java:92` happens after the closing session was removed from rooms.
- No direct websocket disconnect test appears to cover this; presence tests cover `PresenceService.offline(...)` and edge controller disconnect, not `PresenceWebSocketHandler.afterConnectionClosed`.

What improved:
- The unused `PresenceRealtimeEventPublisher` is gone.
- The edge command controller is clean and tested.
- The earlier null-user ordering issue was partially addressed, but the last-session logic still needs correction.

Confidence:
- Compile confidence: High.
- Runtime confidence: Medium. Edge path is credible; direct rollback path still has a concrete disconnect bug.
- Structural cleanliness: Medium.
- Legacy/debt burden: Medium.

Decision: refactor now. This is a blocker cleanup because rollback-compatible direct websocket ingress is still active and should not leave users online after disconnect.

## chat-service

Current responsibility: chat room lifecycle, membership/moderation/read state, room avatar metadata/legacy upload, messages, reactions, Redis/Kafka event publication, Redis subscribers, and direct websocket rollback ingress.

Structure state now: improved but still heavy. The `modules/message` structure is relatively clear. The `modules/room` side is still centered on one large active `RoomService` of 590 lines at `chat-service/src/main/java/com/example/chat/modules/room/service/impl/RoomService.java`. Package/path layout is acceptable.

Layer separation: better after cleanup. The previously unused split room application-service classes are gone. `RoomController` uses `IRoomService`, `IRoomQueryService`, `IPrivateRoomService`, and `IRoomPinService` rather than the removed split app-service set. `ChatRealtimeAdapter` now sends non-pin room/user events via broadcasters and pin/unpin via `ChatRedisPublisher`, so the earlier "silently drops membership events" issue is no longer present.

Rollback-safe path state:
- Direct websocket handler remains under `chat-service/src/main/java/com/example/chat/realtime/websocket/handler/ChatWebSocketHandler.java`.
- Deprecated direct room avatar file upload remains at `chat-service/src/main/java/com/example/chat/modules/room/controller/RoomController.java:287`, `:290`, and calls `IRoomService.uploadAvatar`, deprecated at `chat-service/src/main/java/com/example/chat/modules/room/service/IRoomService.java:35`. This is rollback-compatible while upload metadata flow is validated.

Debt/transitional code still present:
- `chat-service:compileTestJava` fails on stale realtime contract tests:
  - `RealtimeContractBaselineTest.java:6`
  - `RealtimeContractValidatorTest.java:3-5`
  - `RealtimeMessagingAlignmentTest.java:4-9`
- `RoomService` is still too broad. It owns create/join/leave/rename/member moderation/read state/last-message/avatar work in one class. Evidence: methods at `RoomService.java:60`, `:100`, `:107`, `:168`, `:208`, `:227`, `:258`, `:283`, `:316`, `:322`, `:344`, `:358`, `:370`, `:414`, `:427`, `:439`, `:497`.
- `ChatRealtimeAdapter` still documents incomplete durable-first Kafka semantics and has TODOs at `chat-service/src/main/java/com/example/chat/realtime/infrastructure/ChatRealtimeAdapter.java:94`, `:101`, `:116`, `:123`.
- `ChatWebSocketHandler.afterConnectionClosed` unregisters before calling the lifecycle adapter at `chat-service/src/main/java/com/example/chat/realtime/websocket/handler/ChatWebSocketHandler.java:182`, `:185`, so `ChatConnectionLifecycleAdapter` logs "Closed connection has no userId" at `chat-service/src/main/java/com/example/chat/realtime/adapter/ChatConnectionLifecycleAdapter.java:65`, `:67`. This is suspicious but currently lower severity because the adapter only logs on close.

What improved:
- The unused room application-service architecture was removed.
- The unused `ChatRealtimeEventPublisher` is gone.
- `ChatRealtimeAdapter` is now functional for direct fanout rather than dropping non-pin room/user events.

Confidence:
- Compile confidence: High for main code; Low/Medium for test-source because `compileTestJava` fails.
- Runtime confidence: Medium.
- Structural cleanliness: Medium/Low.
- Legacy/debt burden: High.

Decision: refactor now for stale service tests if validation is next. Do not split `RoomService` as part of the immediate blocker pass; that is a later targeted refactor.

## friendship-service

Current responsibility: friend commands, friendship query, user profile enrichment, event publication, edge command ingress, and direct websocket rollback session ingress.

Structure state now: clearer. Packages are acceptable: `controller`, `service`, `kafka`, `client`, `repository`, `websocket`, `realtime.port`, and `configuration`. Package/path layout is acceptable.

Layer separation: improved. `FriendCommandService` owns commands; `FriendshipEventProducer` publishes current aggregate topics. Edge command ingress is separated at `friendship-service/src/main/java/com/example/friendship/controller/FriendshipRealtimeCommandController.java:27`.

Rollback-safe path state:
- `FriendshipWebSocketConfig` still registers `/ws/friendship` at `friendship-service/src/main/java/com/example/friendship/configuration/FriendshipWebSocketConfig.java:16`.
- `FriendshipWebSocketHandler` only registers/unregisters sessions at `friendship-service/src/main/java/com/example/friendship/websocket/FriendshipWebSocketHandler.java:15`, `:28`. The inactive outbound websocket publisher path was removed, so this is now clearly session/rollback surface rather than active delivery.

Debt/transitional code still present:
- `FriendshipRealtimePort` is unused in main code; `rg` finds it only at `friendship-service/src/main/java/com/example/friendship/realtime/port/FriendshipRealtimePort.java:6` and in excluded `FriendshipRealtimeConsumerTest`.
- `FriendshipEventProducer` performs `UserClient` display-name enrichment inside the Kafka producer at `friendship-service/src/main/java/com/example/friendship/kafka/FriendshipEventProducer.java:27`, `:87`, `:105`. This is not a blocker, but it mixes external lookup with event publication.
- Legacy tests are excluded in `friendship-service/build.gradle:73-74`.

What improved:
- `FriendshipKafkaEventApplicationService`, `FriendshipRealtimeEventPublisher`, `FriendshipWebSocketPublisher`, and `WebSocketFriendshipBroadcaster` were removed.
- Active topic publication is now clearer: request lifecycle events go to `KafkaTopics.TOPIC_FRIENDSHIP_REQUEST_EVENTS` at `FriendshipEventProducer.java:61`, status events to `KafkaTopics.TOPIC_FRIENDSHIP_EVENTS` at `:71`.

Confidence:
- Compile confidence: High.
- Runtime confidence: Medium/High.
- Structural cleanliness: Medium.
- Legacy/debt burden: Medium.

Decision: mostly leave for now. Safe cleanup can remove the unused `FriendshipRealtimePort` and the stale excluded test if no pending migration task uses them. Move producer-side enrichment later if the producer is touched for other reasons.

## upload-service

Current responsibility: upload preparation/confirmation, Cloudinary signing, upload purpose policy validation, and upload asset response/contract models.

Structure state now: clean. Packages are compact: `application`, `config`, `contract`, `controller`, `domain`, `dto`, `service`. Package/path layout is acceptable.

Layer separation: good. `UploadController` maps requests into `PrepareUploadCommand` and `ConfirmUploadCommand`; `UploadSigningService` owns signing and confirmation; `UploadPolicyRegistry` owns per-purpose policies.

Rollback-safe path state: no websocket/Kafka rollback path.

Debt/transitional code still present:
- No significant service-local dead code found.
- Test-source warning only: `UploadControllerPurposeDeserializationTest` uses deprecated `@MockBean`; compile still passes.

Confidence:
- Compile confidence: High.
- Runtime confidence: High.
- Structural cleanliness: High.
- Legacy/debt burden: Low.

Decision: leave as-is.

## realtime-edge-service

Current responsibility: unified websocket ingress, websocket session registry, channel subscriptions, command forwarding to domain services, Redis/Kafka event intake, local websocket delivery, and cross-instance handoff.

Structure state now: much clearer than before. The active tree is `com.example.realtime`; the old `com.example.realtimeedge` skeleton tree is gone. Packages are separated into `adapter.in`, `adapter.out`, `connection`, `delivery`, `dispatch`, `routing`, `protocol`, `subscription`, and `config`.

Layer separation: mostly acceptable. Websocket ingress is in `adapter.in.websocket`, HTTP command routing is in `adapter.out.*`, delivery services are domain-specific, and session registry has in-memory/Redis implementations behind a facade.

Rollback-safe path state:
- `WebSocketConfig` exposes `/realtime` and legacy aliases `/ws/notifications`, `/ws/friendship`, `/ws/presence`, and `/ws/chat` at `realtime-edge-service/src/main/java/com/example/realtime/config/WebSocketConfig.java:26`, `:31`, `:36`, `:41`, `:46`.
- `RealtimeWebSocketHandler` explicitly preserves legacy endpoint behavior at `realtime-edge-service/src/main/java/com/example/realtime/adapter/in/websocket/RealtimeWebSocketHandler.java:64`.

Debt/transitional code still present:
- `RestNotificationCommandRouter` defaults `services.notification.url` to `http://localhost:8083`, which is chat-service, not notification-service. Evidence: `realtime-edge-service/src/main/java/com/example/realtime/adapter/out/notification/RestNotificationCommandRouter.java:35`. `application.yaml` defines `services.chat`, `services.presence`, and `services.friendship` under `services:` at `realtime-edge-service/src/main/resources/application.yaml:58`, but no `services.notification`.
- Disabled generic Kafka placeholder path remains: `KafkaEventConsumer` has listeners with `autoStartup = "false"` at `realtime-edge-service/src/main/java/com/example/realtime/adapter/in/kafka/KafkaEventConsumer.java:33`, `:55`, and it delegates to placeholder `EventDeliveryService`.
- `EventDeliveryService.deliverToSession` still only logs and does not send to websocket at `realtime-edge-service/src/main/java/com/example/realtime/delivery/EventDeliveryService.java:75-90`. It has no active caller except disabled `KafkaEventConsumer`.
- Empty `realtime-edge-service/src/main/resources/application.yml` exists beside active `application.yaml`.
- `realtime-edge-service/build.gradle:17` still imports Spring Cloud BOM `2023.0.0` while nearby services use `2025.0.1`.
- `JwtHandshakeInterceptor` only accepts a `token` query parameter at `realtime-edge-service/src/main/java/com/example/realtime/adapter/in/websocket/JwtHandshakeInterceptor.java:45`. This may be fine for websocket clients but should be verified against clients/gateway forwarding behavior.
- `CommandDispatcher` comment says phase B only wires notification commands, but friendship is now wired too at `realtime-edge-service/src/main/java/com/example/realtime/routing/CommandDispatcher.java:20`, `:28`.

What improved:
- Correct `server.port: 8090` at `realtime-edge-service/src/main/resources/application.yaml:30-31`.
- Old skeleton package was removed.
- Domain-specific delivery services now send real websocket messages, unlike the disabled generic placeholder path.
- Redis-backed session registry exists behind a mode switch.

Confidence:
- Compile confidence: High.
- Runtime confidence: Medium. Main code compiles and tests compile, but notification command forwarding has a wrong default/missing property and full-stack runtime still needs validation.
- Structural cleanliness: Medium.
- Legacy/debt burden: Medium/High.

Decision: refactor now for notification command URL/config. Then remove or quarantine the disabled generic Kafka/EventDelivery placeholder trio if no active plan depends on it.

# 4. Top Refactor Targets

## 1. Blocker cleanup mini-pass: presence direct disconnect, realtime-edge notification routing, gateway/chat test-source blockers

Files/packages to touch:
- `presence-service/src/main/java/com/example/presence/websocket/handler/PresenceWebSocketHandler.java`
- `presence-service/src/main/java/com/example/presence/websocket/adapter/PresenceConnectionLifecycleAdapter.java`
- `presence-service/src/main/java/com/example/presence/websocket/session/PresenceSessionRegistry.java`
- add focused presence websocket disconnect test under `presence-service/src/test/java`
- `realtime-edge-service/src/main/java/com/example/realtime/adapter/out/notification/RestNotificationCommandRouter.java`
- `realtime-edge-service/src/main/resources/application.yaml`
- `gateway-service/src/test/java/com/example/gateway/config/GatewayCorsIntegrationTest.java`
- `chat-service/src/test/java/com/example/chat/realtime/contract/*`

Why next:
- These are the remaining concrete blockers to credible validation.
- Presence direct websocket rollback path is still active and can keep users online after disconnect.
- Realtime-edge notification commands can route to the wrong service by default.
- Gateway and chat test-source compilation failures block a normal validation loop.

Type:
- blocker cleanup
- runtime confidence cleanup
- validation blocker cleanup

Expected benefit:
- Makes service validation trustworthy again without common changes.
- Gives a clean baseline for runtime execution.

Expected risk:
- Low/Medium. Presence lifecycle must be fixed carefully to avoid breaking multi-session semantics.
- Gateway/chat test fixes are low risk if they only update/delete stale service tests.

## 2. notification-service Kafka/application consolidation and dead interface cleanup

Files/packages to touch:
- `notification-service/src/main/java/com/example/notification/kafka/MessageCreatedEventConsumer.java`
- `notification-service/src/main/java/com/example/notification/kafka/ReactionEventConsumer.java`
- `notification-service/src/main/java/com/example/notification/kafka/FriendRequestEventConsumer.java`
- `notification-service/src/main/java/com/example/notification/application/NotificationKafkaEventApplicationService.java`
- `notification-service/src/main/java/com/example/notification/application/NotificationMessageEventApplicationService.java`
- `notification-service/src/main/java/com/example/notification/application/NotificationReactionEventApplicationService.java`
- `notification-service/src/main/java/com/example/notification/application/NotificationFriendRequestEventApplicationService.java`
- `notification-service/src/main/java/com/example/notification/realtime/publisher/NotificationRealtimeEventPublisher.java`
- `notification-service/src/main/java/com/example/notification/websocket/NotificationWebSocketPublisher.java`
- `notification-service/build.gradle`
- excluded tests under `notification-service/src/test/java/com/example/notification/**`

Why next:
- Notification is still the messiest service structurally.
- Message/reaction business logic exists twice: once in Kafka consumers and once in application services.
- `NotificationKafkaEventApplicationService` is not active main path, while excluded tests assume consumer delegation.
- An unused realtime publisher interface remains.

Type:
- structural cleanup
- dead code removal
- consistency cleanup

Expected benefit:
- Thin Kafka ingress becomes clear and testable.
- Excluded tests can be updated/re-enabled or intentionally deleted.
- Reduces duplicate notification behavior drift.

Expected risk:
- Medium. Message/reaction notification behavior is user-facing; compare current consumer behavior against application-service behavior before deleting either side.

## 3. Dead transitional service-only sweep: user Cloudinary upload, friendship unused realtime port, realtime-edge disabled generic placeholder

Files/packages to touch:
- `user-service/build.gradle`
- `user-service/src/main/java/com/example/user/configuration/CloudinaryConfig.java`
- `user-service/src/main/java/com/example/user/service/impl/CloudinaryService.java`
- `user-service/src/main/java/com/example/user/dto/CloudinaryUploadResult.java`
- `user-service/src/main/resources/application.yaml`
- `friendship-service/src/main/java/com/example/friendship/realtime/port/FriendshipRealtimePort.java`
- `friendship-service/src/test/java/com/example/friendship/kafka/FriendshipRealtimeConsumerTest.java`
- `friendship-service/build.gradle`
- `realtime-edge-service/src/main/java/com/example/realtime/adapter/in/kafka/KafkaEventConsumer.java`
- `realtime-edge-service/src/main/java/com/example/realtime/delivery/EventDeliveryService.java`
- `realtime-edge-service/src/main/java/com/example/realtime/delivery/EventRouter.java`
- `realtime-edge-service/src/main/resources/application.yml`

Why next:
- These are dead or inactive service-owned artifacts that obscure the active paths.
- `user-service` has no active direct Cloudinary upload route; keeping SDK/config/service code makes the profile service look like it still owns uploads.
- `FriendshipRealtimePort` has no main-code implementation/caller.
- The disabled generic edge Kafka path is not the active delivery model and its delivery service does not actually send websocket messages.

Type:
- dead code removal
- consistency cleanup
- optional later cleanup for the realtime-edge generic path if a near-term task still depends on it

Expected benefit:
- Smaller runtime context.
- Clearer service responsibilities.
- Less confusion during validation.

Expected risk:
- Low for user/friendship dead code if reference checks remain clean.
- Low/Medium for realtime-edge generic placeholder removal because it may be intended as future work; confirm no pending task expects it before deletion.

# 5. Dead/Transitional Code Decisions

## Keep for rollback

- Gateway legacy websocket routes: `/ws/chat/**`, `/ws/presence/**`, `/ws/friendship/**`, `/ws/notifications/**` in `gateway-service/src/main/resources/application.yaml:85-153`.
- Presence direct websocket handler/config/session registry, but fix disconnect first: `presence-service/src/main/java/com/example/presence/websocket/**`.
- Friendship direct websocket handler/config/session registry: `friendship-service/src/main/java/com/example/friendship/websocket/FriendshipWebSocketHandler.java:15`; `friendship-service/src/main/java/com/example/friendship/configuration/FriendshipWebSocketConfig.java:16`.
- Realtime-edge legacy aliases `/ws/notifications`, `/ws/friendship`, `/ws/presence`, `/ws/chat` at `realtime-edge-service/src/main/java/com/example/realtime/config/WebSocketConfig.java:31`, `:36`, `:41`, `:46`.
- Chat deprecated direct room avatar upload route at `chat-service/src/main/java/com/example/chat/modules/room/controller/RoomController.java:287`, `:290`, and its Cloudinary support, until the upload-service metadata flow is validated end to end.
- Service-local `KafkaProducerAdapterConfig` bridge classes in auth, chat, friendship, and notification. They are compatibility code required by frozen common, not dead code right now.

## Safe to remove now

- `user-service` direct Cloudinary upload stack:
  - `user-service/src/main/java/com/example/user/service/impl/CloudinaryService.java:17`
  - `user-service/src/main/java/com/example/user/configuration/CloudinaryConfig.java:10`
  - `user-service/src/main/java/com/example/user/dto/CloudinaryUploadResult.java:8`
  - `user-service/build.gradle:42`
  - remove `cloudinary.api-key` and `cloudinary.api-secret` config if no remaining bean uses them; keep `cloudinary.cloud-name` if `AvatarGenerator` still uses it.
- `notification-service/src/main/java/com/example/notification/realtime/publisher/NotificationRealtimeEventPublisher.java:12`, unused by main/test code.
- `friendship-service/src/main/java/com/example/friendship/realtime/port/FriendshipRealtimePort.java:6`, unused by main code and referenced only by excluded stale test.
- `realtime-edge-service/src/main/resources/application.yml`, empty duplicate beside active `application.yaml`.
- Stale comments after behavior changed:
  - `notification-service/src/main/java/com/example/notification/websocket/NotificationWebSocketPublisher.java:39-41`
  - `realtime-edge-service/src/main/java/com/example/realtime/routing/CommandDispatcher.java:20`

## Refactor later

- `auth-service/src/main/java/com/example/auth/configuration/DatabaseSchemaFixer.java:16`
- `user-service/src/main/java/com/example/user/configuration/DatabaseSchemaFixer.java:16`
- `notification-service/src/main/java/com/example/notification/configuration/DatabaseSchemaFixer.java:16`
- `chat-service/src/main/java/com/example/chat/modules/room/service/impl/RoomService.java`, still too broad but not an immediate broad-rewrite target.
- `chat-service/src/main/java/com/example/chat/realtime/infrastructure/ChatRealtimeAdapter.java:94`, `:101`, `:116`, `:123` durable-first TODOs.
- `friendship-service/src/main/java/com/example/friendship/kafka/FriendshipEventProducer.java:87-105` user display-name enrichment inside the producer.
- Gateway websocket route cutover to realtime-edge, once ingress ownership is ready.
- Spring Cloud BOM alignment in `realtime-edge-service/build.gradle:17`.

## Needs verification before removal

- Startup schema fixers need migration-state proof before removal.
- Chat legacy room avatar direct upload should remain until room avatar metadata/upload-service flow is validated.
- Gateway service-local websocket routes should remain until logs/client inventory prove edge ingress owns those paths.
- Realtime-edge query-param-only websocket JWT handling at `realtime-edge-service/src/main/java/com/example/realtime/adapter/in/websocket/JwtHandshakeInterceptor.java:45` should be checked against current clients.
- Realtime-edge disabled generic `KafkaEventConsumer`/`EventDeliveryService`/`EventRouter` trio is inactive and removable from a runtime perspective, but verify no imminent migration task expects that placeholder before deleting it.
- Notification message/reaction application services and consumers should be compared before consolidation because both contain business behavior.

# 6. Final Verdict

The backend is not quite clean enough to move mainly into validation/runtime execution.

It is much cleaner than before the latest cleanup, and the package/folder structure is now broadly acceptable. Main service compile is green. Active paths are easier to identify. The old dead room/friendship/realtime-edge skeleton code is mostly gone.

But one more narrow service-only refactor pass should happen first:
1. Fix blocker/validation cleanup in `presence-service`, `realtime-edge-service`, `gateway-service` tests, and `chat-service` stale tests.
2. Consolidate `notification-service` Kafka/application handling and remove its unused realtime publisher interface.
3. Remove clear service-local dead code in `user-service`, `friendship-service`, and inactive realtime-edge placeholders, without changing common.

After that pass, the backend should be ready to focus mainly on validation/runtime execution rather than more structural churn.
