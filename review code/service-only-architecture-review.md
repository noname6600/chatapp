# 1. Executive Summary

This is a service-only review of `gateway-service`, `auth-service`, `user-service`, `notification-service`, `presence-service`, `chat-service`, `friendship-service`, `upload-service`, and `realtime-edge-service` under the frozen-common rule.

The scoped service main code is compile-clean: `compileJava` succeeded for all nine services. That is a good baseline, but the backend is not fully runtime-clean yet. Test/context execution exposed two shared-contract/runtime failures and several service-level runtime risks that should be fixed before treating the current edge-migration state as healthy.

Overall verdict: the service layer is coherent enough to continue, but it is not clean enough to call finished. The issues are mostly cleanup and migration debt, with a small set of real blockers:

- Services compile, but several service contexts are likely to fail because services inject `KafkaEventProducer` while common Kafka auto-configuration exposes `KafkaEventPublisher`.
- Services depending on `common-websocket` can fail during Spring auto-configuration loading because the auto-configuration imports file starts with a UTF-8 BOM/hidden leading character.
- `presence-service` has a direct websocket disconnect bug that can leave users online until Redis/TTL cleanup.
- `realtime-edge-service` is configured with `spring.server.port`, so it is likely to start on the default Boot port instead of `8090`.
- `notification-service` has a stale/wrong friend-request Kafka listener topic relative to the current friendship producer topic.

The recent realtime-edge migration made ownership direction better in some places, especially by adding `realtime-edge-service` and HTTP command endpoints, but it also left visible transitional layers: legacy service-local websocket endpoints, duplicate realtime adapters, unused application services, excluded tests, and an old unscanned edge skeleton package.

# 2. Frozen Common Rule

Common was treated as read-only reference only. I inspected common where it was needed to understand service runtime coupling and shared event topics.

True common blockers found:

1. `common/common-websocket/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` starts with bytes `EF BB BF`. The presence context test failed trying to load the realtime websocket auto-configuration class with that hidden leading character included in the class name. This is a proven runtime failure for services that depend on `common-websocket`.
2. `common/common-kafka/src/main/java/com/example/common/kafka/config/KafkaAutoConfiguration.java:28` registers `KafkaEventPublisher`, but multiple services inject `KafkaEventProducer` directly. `auth-service:test` proved this as an application-context failure. Because services can likely migrate to the registered publisher or provide service-local adapter beans, the first fix should remain service-first. Change common only if service-level migration is proven impossible.

No broader common redesign is recommended. The frozen rule should remain in force except for the two proven runtime blockers above.

# 3. Service-by-Service Review

## gateway-service

Purpose: API gateway and route/security boundary for backend HTTP and websocket traffic.

Structure quality: High. The service is small and understandable: `config`, `controller`, `filter`, and `health`. `JwtAuthFilterGatewayFilterFactory` forwards `X-User-Id` from JWT subject at `gateway-service/src/main/java/com/example/gateway/filter/JwtAuthFilterGatewayFilterFactory.java:40`.

Architecture quality: Medium/High. HTTP routing and gateway filtering are in the right service. Websocket routes still point at the old service-local websocket endpoints: `/ws/chat/**`, `/ws/presence/**`, `/ws/friendship/**`, and `/ws/notifications/**` in `gateway-service/src/main/resources/application.yaml:85`, `:106`, `:127`, and `:148`. There is no gateway route for `realtime-edge-service` or `/realtime`. That is acceptable only if clients connect directly to the edge service or another routing layer owns edge ingress. Inside this service, the edge migration is not fully wired.

Websocket/security boundary: `SecurityConfig` permits `/ws/**` at `gateway-service/src/main/java/com/example/gateway/config/SecurityConfig.java:37`, so websocket auth is delegated to downstream services/edge. That is coherent if deliberate, but it makes downstream handshake validation mandatory.

Deprecated/unused/transitional code: No major service dead code found. The old websocket gateway routes are rollback-compatible, not proven dead. `GatewayCorsIntegrationTest` is stale: it constructs `new GatewayConfig()` at `gateway-service/src/test/java/com/example/gateway/config/GatewayCorsIntegrationTest.java:24`, `:42`, `:59`, and `:75`, but the production constructor now requires `CorsProperties`.

Likely runtime confidence: The main gateway should run. Edge migration behavior needs verification because gateway does not currently route websocket clients to `realtime-edge-service`.

Confidence:

- Compile confidence: High.
- Runtime confidence: Medium/High.
- Structural cleanliness: High.
- Legacy/debt burden: Low/Medium.

Clean inside service only:

- Decide whether gateway should route `/realtime` and legacy `/ws/*` aliases to `realtime-edge-service`; if not, document the direct-edge ingress assumption.
- Keep the old service websocket routes as rollback-compatible until edge ingress is validated.
- Update `GatewayCorsIntegrationTest` to match the current `GatewayConfig(CorsProperties)` constructor.

## auth-service

Purpose: account registration/login/logout, password changes, email verification, token issuance, and account-created event publication.

Structure quality: Medium. The service uses familiar packages: `controller`, `service`, `repository`, `entity`, `jwt`, `kafka`, `scheduler`, and config. It is understandable but still has older transitional pieces.

Architecture quality: Medium. Core auth behavior is in service classes, while Kafka publication is isolated in `AccountCreatedEventProducer`. The main boundary issue is security: `SecurityConfig` permits all `/api/v1/auth/**` at `auth-service/src/main/java/com/example/auth/configuration/SecurityConfig.java:44`, while `AuthController` contains endpoints that require an authenticated principal. `logout-all`, email verification status, and email verification send call `principal.getAccountId()` without a null guard at `auth-service/src/main/java/com/example/auth/controller/AuthController.java:81`, `:105`, and `:113`. `changePassword` has a null check at `:90`, so this inconsistency is visible.

Common coupling: `AccountCreatedEventProducer` injects `KafkaEventProducer` at `auth-service/src/main/java/com/example/auth/kafka/AccountCreatedEventProducer.java:22`. The auth context test failed because no such bean exists from common auto-configuration. This is a real runtime blocker unless auth switches to a registered common publisher or provides a service-local adapter.

Deprecated/unused/transitional code: `DatabaseSchemaFixer` runs raw startup DDL at `auth-service/src/main/java/com/example/auth/configuration/DatabaseSchemaFixer.java:23`. That is service-local schema debt, not a compile blocker, but it should not remain as permanent application startup behavior.

Likely runtime confidence: Low/Medium until the Kafka producer bean mismatch is fixed. Also, permitted protected auth endpoints can return 500/null-principal behavior instead of clear 401/403 behavior.

Confidence:

- Compile confidence: High.
- Runtime confidence: Low/Medium.
- Structural cleanliness: Medium.
- Legacy/debt burden: Medium.

Clean inside service only:

- Replace direct `KafkaEventProducer` injection with the currently registered publisher contract, or add a service-local adapter bean.
- Split public auth endpoints from authenticated auth endpoints in security config, or add explicit guards and method security.
- Remove `DatabaseSchemaFixer` after the database migration is represented by the normal schema/migration path.

## user-service

Purpose: user profile lifecycle, profile lookup/update, avatar metadata confirmation, and account-created profile creation.

Structure quality: Medium. The service has a partial layered layout with `application`, `configuration`, `controller`, `dto`, `entity`, `kafka`, `repository`, `service`, and `utils`. The layout is mostly readable, but the boundary between Kafka consumer and application service is currently duplicated.

Architecture quality: Medium. The biggest boundary violation is a compile-time dependency on another service: `user-service/build.gradle:31` depends on `project(':upload-service')`, and `UserProfileService` imports `com.example.upload.contract.UploadAssetMetadata` at `user-service/src/main/java/com/example/user/service/impl/UserProfileService.java:3`. There is already a local `AvatarAssetMetadata` DTO at `user-service/src/main/java/com/example/user/dto/AvatarAssetMetadata.java:14`, which suggests the intended boundary was to avoid importing upload-service.

Kafka/application boundary: `AccountCreatedConsumer` contains profile creation logic directly and listens to `account.created` at `user-service/src/main/java/com/example/user/kafka/AccountCreatedConsumer.java:27`. `UserKafkaAccountCreatedApplicationService` duplicates the same idempotent creation flow at `user-service/src/main/java/com/example/user/application/UserKafkaAccountCreatedApplicationService.java:41`, but main code does not appear to use it; only tests reference it.

Deprecated/unused/transitional code: `UserKafkaAccountCreatedApplicationService` is likely transitional/dead unless the consumer is meant to delegate to it. `DatabaseSchemaFixer` creates a unique index at startup at `user-service/src/main/java/com/example/user/configuration/DatabaseSchemaFixer.java:37`; this is service-local schema debt.

Likely runtime confidence: Medium. Main compile is clean, but the service-to-service dependency is a maintainability and boundary problem. I did not see a proven startup failure for user-service in this pass.

Confidence:

- Compile confidence: High.
- Runtime confidence: Medium.
- Structural cleanliness: Medium.
- Legacy/debt burden: Medium/High.

Clean inside service only:

- Remove the `upload-service` project dependency and use a user-service-local avatar metadata DTO/contract.
- Make `AccountCreatedConsumer` delegate to `UserKafkaAccountCreatedApplicationService`, or delete the unused application service and tests if direct consumer logic is the intended design.
- Move startup schema fixing out of runtime application code when the migration path is stable.

## notification-service

Purpose: notification creation, unread counts, notification websocket/Redis fanout, and Kafka event consumption from account/chat/reaction/friendship events.

Structure quality: Medium. Packages are understandable (`application`, `configuration`, `controller`, `kafka`, `realtime`, `repository`, `service`, `websocket`), but there are overlapping realtime and Kafka paths.

Architecture quality: Medium/Low. The application-service layer exists, but Kafka consumers are inconsistent. `NotificationKafkaEventApplicationService` exists at `notification-service/src/main/java/com/example/notification/application/NotificationKafkaEventApplicationService.java:21`, but main consumers are not consistently delegating to it. `MessageCreatedEventConsumer` and `ReactionEventConsumer` still contain consumer-local business handling at `notification-service/src/main/java/com/example/notification/kafka/MessageCreatedEventConsumer.java:32` and `notification-service/src/main/java/com/example/notification/kafka/ReactionEventConsumer.java:23`. This keeps event consumption and notification creation more mixed than necessary.

Runtime/topic risk: `FriendRequestEventConsumer` listens to a single literal topic string `"friend.request.sent,friend.request.accepted,friend.request.declined,friend.request.cancelled"` at `notification-service/src/main/java/com/example/notification/kafka/FriendRequestEventConsumer.java:19`. Current friendship publication sends friend request events to `KafkaTopics.TOPIC_FRIENDSHIP_REQUEST_EVENTS`, which is `friendship.request.events` at `common/common-kafka/src/main/java/com/example/common/kafka/topic/KafkaTopics.java:25`, and the producer uses it at `friendship-service/src/main/java/com/example/friendship/kafka/FriendshipEventProducer.java:61`. This is likely to prevent friend-request notifications from being consumed.

Realtime boundary: `NotificationWebSocketPublisher` is the active `@Primary` realtime port at `notification-service/src/main/java/com/example/notification/websocket/NotificationWebSocketPublisher.java:47`. `NotificationRedisRealtimeAdapter` is explicitly deprecated for removal at `notification-service/src/main/java/com/example/notification/realtime/infrastructure/NotificationRedisRealtimeAdapter.java:45`, but it is still a `@Component` and implements the same port at `:47`. This is cleanup debt; the primary bean avoids immediate ambiguity, but the duplicate adapter should not survive long-term.

Common coupling: `NotificationEventProducer` injects `KafkaEventProducer` at `notification-service/src/main/java/com/example/notification/kafka/NotificationEventProducer.java:20`, so this service is exposed to the same missing-bean runtime blocker as auth.

Tests/debt signal: The build excludes many notification Kafka/realtime tests at `notification-service/build.gradle:59` through `:64`, and excludes `ChatappApplicationTests` at `:77`. This reduces runtime confidence.

Likely runtime confidence: Low/Medium. Compile is clean, but context startup is likely blocked by the Kafka producer bean mismatch and/or common-websocket auto-configuration. Friend-request notifications are likely not wired to the current topic.

Confidence:

- Compile confidence: High.
- Runtime confidence: Low/Medium.
- Structural cleanliness: Medium.
- Legacy/debt burden: High.

Clean inside service only:

- Align Kafka producer injection with the available publisher contract or provide a service-local adapter.
- Fix `FriendRequestEventConsumer` to consume the current friendship request topic.
- Make Kafka consumers consistently delegate to application services, or remove the unused aggregate application service.
- Remove `NotificationRedisRealtimeAdapter` after confirming `NotificationWebSocketPublisher` is the only intended realtime port implementation.
- Re-enable excluded Kafka/realtime tests after the wiring is corrected.

## presence-service

Purpose: online/offline state, room presence, typing state, presence Redis/Kafka-facing adapters, and edge command handling for presence websocket behavior.

Structure quality: Medium. The service has reasonable packages: `configuration`, `controller`, `dto`, `realtime`, `redis`, `service`, `state`, and `websocket`. The newer edge command controller is a clean addition, but legacy websocket handling remains.

Architecture quality: Medium. `PresenceEdgeCommandController` is a good edge-migration adapter: edge-owned websocket ingress can call service-owned domain commands over HTTP. The direct websocket path still mixes session lifecycle, room registry cleanup, and domain offline transitions.

Runtime bug: `PresenceWebSocketHandler.afterConnectionClosed` reads `userId` and `rooms`, removes the session from rooms, unregisters the session, then calls `lifecycleAdapter.onConnectionClosed(session)` at `presence-service/src/main/java/com/example/presence/websocket/handler/PresenceWebSocketHandler.java:188` through `:197`. The adapter then calls `sessionRegistry.getUserId(session)` at `presence-service/src/main/java/com/example/presence/websocket/adapter/PresenceConnectionLifecycleAdapter.java:73`; after unregister, this returns null and exits at `:74` through `:76`. Result: direct websocket disconnects do not call `presenceService.offline(userId)` at `:84`, so users can remain online until a later expiry path.

Deprecated/unused/transitional code: `PresenceRealtimeEventPublisher` appears unused in main code at `presence-service/src/main/java/com/example/presence/realtime/publisher/PresenceRealtimeEventPublisher.java:11`. The direct websocket endpoint is rollback-compatible while edge migration is being validated, but the disconnect ordering bug must be fixed if it remains enabled.

Common coupling: Presence depends on `common-websocket`; the presence context test failed on the common-websocket auto-configuration import hidden-character issue. That is a proven runtime blocker outside presence code, but it affects this service directly.

Likely runtime confidence: Low/Medium. Main compile is clean, but context startup failed due common-websocket and direct websocket disconnect behavior is wrong.

Confidence:

- Compile confidence: High.
- Runtime confidence: Low/Medium.
- Structural cleanliness: Medium.
- Legacy/debt burden: Medium.

Clean inside service only:

- Fix direct websocket disconnect by passing the captured `userId`/rooms into the lifecycle cleanup or calling lifecycle before unregistering.
- Keep `PresenceEdgeCommandController` as the current clean migration adapter.
- Remove unused `PresenceRealtimeEventPublisher` if no runtime path references it.
- Keep legacy websocket path only as explicitly marked rollback-compatible code until edge validation is complete.

## chat-service

Purpose: chat rooms, membership/moderation/read state, message creation/edit/delete/reactions, chat realtime fanout, and chat websocket command handling.

Structure quality: Medium/Low. The modular split under `modules/message`, `modules/room`, and `realtime` is a good direction. The problem is that old and new structures coexist: `RoomService` remains a large all-purpose service implementing `IRoomService` at `chat-service/src/main/java/com/example/chat/modules/room/service/impl/RoomService.java:46`, while split services such as `RoomLifecycleApplicationService`, `RoomMembershipApplicationService`, `RoomMessageStateApplicationService`, `RoomMetadataApplicationService`, `RoomModerationApplicationService`, and `RoomReadStateApplicationService` exist at their respective class declarations but are not wired as the active controller path.

Architecture quality: Medium/Low. The message module is cleaner than the room module. The room module still concentrates lifecycle, membership, moderation, read state, avatar, and metadata behavior into one active service. The split application services look like unfinished migration code rather than current architecture.

Realtime boundary: `ChatRealtimeAdapter` explicitly documents unfinished durable-first behavior and has TODOs for Kafka publication at `chat-service/src/main/java/com/example/chat/realtime/infrastructure/ChatRealtimeAdapter.java:94`, `:101`, `:116`, and `:123`. `ChatMessageEventPublisherAdapter` injects `KafkaEventProducer` at `chat-service/src/main/java/com/example/chat/modules/message/infrastructure/redis/ChatMessageEventPublisherAdapter.java:37`, so chat has the same missing-bean runtime risk as auth.

Websocket lifecycle issue: `ChatWebSocketHandler.afterConnectionClosed` unregisters the session at `chat-service/src/main/java/com/example/chat/realtime/websocket/handler/ChatWebSocketHandler.java:182`, then calls `lifecycleAdapter.onConnectionClosed(session)` at `:185`. The adapter reads the user from the registry at `chat-service/src/main/java/com/example/chat/realtime/adapter/ChatConnectionLifecycleAdapter.java:65` and returns if null at `:66` through `:68`. This is less severe than presence because the adapter currently only logs on close, but it shows the same transitional lifecycle ordering defect.

Rollback-compatible code: `RoomController` exposes deprecated direct room avatar file upload at `chat-service/src/main/java/com/example/chat/modules/room/controller/RoomController.java:287` with `@Deprecated` at `:290`. This is rollback-compatible if the new upload confirmation flow is still being validated; do not delete it blindly.

Deprecated/unused/transitional code: The unused split room application services are the largest chat cleanup issue. `ChatRealtimeEventPublisher` appears unused at `chat-service/src/main/java/com/example/chat/realtime/publisher/ChatRealtimeEventPublisher.java:12`.

Likely runtime confidence: Low/Medium. Compile is clean, but context startup is likely blocked by missing `KafkaEventProducer` and/or common-websocket auto-configuration. Structurally, chat is the messiest service in scope.

Confidence:

- Compile confidence: High.
- Runtime confidence: Low/Medium.
- Structural cleanliness: Medium/Low.
- Legacy/debt burden: High.

Clean inside service only:

- Align Kafka producer usage with the registered publisher contract or add a service-local adapter.
- Either wire the split room application services into the controller path or remove them; do not leave two room-service architectures alive.
- Fix websocket lifecycle ordering if direct websocket rollback remains enabled.
- Keep the deprecated room avatar upload route only until the upload-service confirm flow is validated.
- Resolve `ChatRealtimeAdapter` durable-first TODOs or mark the remaining direct-fanout paths as intentional rollback behavior.

## friendship-service

Purpose: friend request commands, friendship state, user lookup enrichment, friendship event publication, and legacy friendship websocket/session handling.

Structure quality: Medium. Packages are broadly sensible: `application`, `client`, `config`, `controller`, `dto`, `entity`, `kafka`, `realtime`, `repository`, `service`, and `websocket`. The issue is not package naming; it is leftover unused paths.

Architecture quality: Medium/Low. `FriendCommandService` owns domain mutations and event publication. `FriendshipEventProducer` publishes to the current aggregate Kafka topics, using `KafkaTopics.TOPIC_FRIENDSHIP_REQUEST_EVENTS` at `friendship-service/src/main/java/com/example/friendship/kafka/FriendshipEventProducer.java:61` and `KafkaTopics.TOPIC_FRIENDSHIP_EVENTS` at `:71`. That is directionally good. However, the producer also injects `UserClient` at `:27` and resolves sender display name at `:105`, so infrastructure publication is doing external user-service enrichment. That responsibility would be cleaner before event construction, but it is not a runtime blocker.

Common coupling: `FriendshipEventProducer` injects `KafkaEventProducer` at `friendship-service/src/main/java/com/example/friendship/kafka/FriendshipEventProducer.java:26`, so it is exposed to the same missing-bean runtime blocker as auth/chat/notification.

Websocket/realtime boundary: `FriendshipWebSocketConfig` registers `/ws/friendship` at `friendship-service/src/main/java/com/example/friendship/configuration/FriendshipWebSocketConfig.java:23`, but `FriendshipWebSocketHandler` only registers and unregisters sessions at `friendship-service/src/main/java/com/example/friendship/websocket/FriendshipWebSocketHandler.java:20` through `:31`. `FriendshipWebSocketPublisher` states it is "Not wired into active delivery path" at `friendship-service/src/main/java/com/example/friendship/websocket/FriendshipWebSocketPublisher.java:21`. This makes the service-local websocket path misleading unless it is intentionally kept only for rollback/session compatibility.

Deprecated/unused/transitional code: `FriendshipKafkaEventApplicationService` appears unused in main code at `friendship-service/src/main/java/com/example/friendship/application/FriendshipKafkaEventApplicationService.java:18`. `FriendshipRealtimeEventPublisher` appears unused at `friendship-service/src/main/java/com/example/friendship/realtime/publisher/FriendshipRealtimeEventPublisher.java:12`. Build excludes legacy tests at `friendship-service/build.gradle:73` and `:74`.

Likely runtime confidence: Low/Medium. Compile is clean, but app context is likely exposed to `KafkaEventProducer` missing-bean failure, and the old websocket endpoint is not a functional delivery path.

Confidence:

- Compile confidence: High.
- Runtime confidence: Low/Medium.
- Structural cleanliness: Medium.
- Legacy/debt burden: High.

Clean inside service only:

- Align Kafka producer injection with the available publisher contract or add a service-local adapter.
- Move user display-name enrichment out of the infrastructure producer if this code is touched.
- Remove or clearly quarantine the unused `FriendshipKafkaEventApplicationService`, `FriendshipRealtimeEventPublisher`, and non-delivering websocket publisher/handler after edge delivery is validated.
- Keep `/ws/friendship` only if it is explicitly a rollback-compatible endpoint; otherwise it should not advertise a path that cannot deliver events.

## upload-service

Purpose: Cloudinary upload signature preparation, upload confirmation, purpose-specific upload policy validation, and upload contract objects.

Structure quality: High. The service is compact and clean: `application`, `config`, `contract`, `controller`, `domain`, `dto`, and `service`. There is no Kafka/Redis/websocket coupling.

Architecture quality: High. Controller/request DTOs map to application commands and `UploadSigningService` owns Cloudinary signing and confirmation validation. `UploadPolicyRegistry` centralizes per-purpose policy setup at `upload-service/src/main/java/com/example/upload/service/UploadPolicyRegistry.java:20`, and `UploadPurpose` is a local domain enum at `upload-service/src/main/java/com/example/upload/domain/UploadPurpose.java:3`.

Runtime confidence: High/Medium. Compile is clean and there are no obvious context blockers in this service. One behavior needs Cloudinary validation: `UploadSigningService.prepare` sets `publicId = policy.getFolder() + "/" + UUID.randomUUID()` at `upload-service/src/main/java/com/example/upload/service/UploadSigningService.java:42`, then signs both `folder` and that folder-prefixed `public_id` at `:47` and `:48`. Depending on Cloudinary semantics, this can duplicate folder information or produce a different public id shape than expected. This is needs-verification, not a proven blocker.

Boundary note: The upload service itself is clean. The boundary violation is in `user-service`, which imports upload-service's `UploadAssetMetadata` directly.

Deprecated/unused/transitional code: No significant dead service code found.

Confidence:

- Compile confidence: High.
- Runtime confidence: High/Medium.
- Structural cleanliness: High.
- Legacy/debt burden: Low.

Clean inside service only:

- Validate the Cloudinary `folder` plus folder-prefixed `public_id` behavior with a real or mocked upload.
- Keep upload contracts stable; do not expand upload-service responsibility into profile or chat domain decisions.

## realtime-edge-service

Purpose: unified websocket ingress and realtime delivery for notification, friendship, presence, and chat migration paths.

Structure quality: Medium/Low. The active service is under `com.example.realtime`, and `RealtimeEdgeApplication` scans only `com.example.common` and `com.example.realtime` at `realtime-edge-service/src/main/java/com/example/realtime/RealtimeEdgeApplication.java:21`. However, a stale `com.example.realtimeedge` skeleton tree still exists with `RealtimeEdgeServiceApplication`, `CentralRealtimeWebSocketHandler`, command routers, session registry, and event handlers. Those files are unscanned by the active app and many explicitly say skeleton/placeholder, for example `realtime-edge-service/src/main/java/com/example/realtimeedge/RealtimeEdgeServiceApplication.java:24` and `realtime-edge-service/src/main/java/com/example/realtimeedge/websocket/handler/CentralRealtimeWebSocketHandler.java:35`.

Architecture quality: Medium. The active service has a sensible split across websocket ingress, routing, delivery, Redis handoff, and Kafka consumers. `WebSocketConfig` registers `/realtime` and legacy aliases `/ws/notifications`, `/ws/friendship`, `/ws/presence`, and `/ws/chat` at `realtime-edge-service/src/main/java/com/example/realtime/config/WebSocketConfig.java:26`, `:31`, `:36`, `:41`, and `:46`. These aliases are useful compatibility code.

Runtime/config blocker: `application.yaml` places port under `spring.server.port` at `realtime-edge-service/src/main/resources/application.yaml:5` and `:6`. Spring Boot expects top-level `server.port`, so the service is likely to start on default port `8080` instead of the intended `8090`. This is a service-local runtime configuration blocker if clients/gateway expect `8090`.

Ingress/auth boundary: `JwtHandshakeInterceptor` only reads `token` from the query string at `realtime-edge-service/src/main/java/com/example/realtime/adapter/in/websocket/JwtHandshakeInterceptor.java:45`. This can be acceptable for websocket clients, but it should be verified against existing clients because gateway JWT auth/header forwarding will not automatically satisfy this interceptor.

Delivery boundary: Active domain-specific delivery services do send websocket messages directly, for example `NotificationRealtimeDeliveryService` sends `TextMessage` at `realtime-edge-service/src/main/java/com/example/realtime/delivery/NotificationRealtimeDeliveryService.java:58`, and equivalent chat/presence/friendship delivery services do the same. `EventDeliveryService` is still placeholder-like at `realtime-edge-service/src/main/java/com/example/realtime/delivery/EventDeliveryService.java:75` through `:90`, but it is only used by the disabled generic `KafkaEventConsumer` path.

Kafka migration status: `KafkaEventConsumer` has chat and notification listeners with `autoStartup = "false"` at `realtime-edge-service/src/main/java/com/example/realtime/adapter/in/kafka/KafkaEventConsumer.java:33` and `:55`. The notification listener uses `topics = "notification.*"` rather than a topic pattern, but it is disabled. Friendship consumers are active and use `KafkaTopics.TOPIC_FRIENDSHIP_REQUEST_EVENTS` and `TOPIC_FRIENDSHIP_EVENTS` at `realtime-edge-service/src/main/java/com/example/realtime/adapter/in/kafka/FriendshipKafkaEventConsumer.java:41` and `:92`.

Build/dependency consistency: The edge service imports Spring Cloud BOM `2023.0.0` at `realtime-edge-service/build.gradle:17`, while neighboring services use the repo's newer Spring Cloud alignment. This is dependency risk, not a proven blocker from compile/test.

Likely runtime confidence: Medium. `realtime-edge-service:test` passed, but integration validation is not the same as full multi-service runtime. The wrong port property and stale unscanned skeleton package lower confidence.

Confidence:

- Compile confidence: High.
- Runtime confidence: Medium.
- Structural cleanliness: Medium/Low.
- Legacy/debt burden: High.

Clean inside service only:

- Move `server.port` to the correct top-level key.
- Remove or archive the unscanned `com.example.realtimeedge` skeleton package after confirming no task depends on it.
- Keep legacy websocket aliases as compatibility endpoints.
- Verify whether query-param-only websocket JWT handling matches current clients.
- Align Spring Cloud BOM with the rest of the backend if no local reason exists for `2023.0.0`.
- Either finish or remove the disabled generic `KafkaEventConsumer`/`EventDeliveryService` placeholder path.

# 4. Cross-Service Findings

Repeated structural problems:

- Several services are compile-clean but context-risky because they inject `KafkaEventProducer`: auth, chat, friendship, and notification. The registered common bean is `KafkaEventPublisher`, not `KafkaEventProducer`.
- Realtime migration left duplicate paths: service-local websocket handlers still exist while `realtime-edge-service` also exposes compatibility websocket paths.
- Application-service extraction is incomplete in several places. User, notification, friendship, and chat contain application services that are not the active path or duplicate consumer/service logic.
- Startup schema fixers remain in auth and user services. These are service-local hacks and should not be permanent runtime responsibilities.
- Several tests are excluded or stale, especially notification/friendship migration tests and gateway CORS tests. That weakens runtime confidence after migration.

Repeated transitional patterns:

- Legacy websocket endpoints are mostly rollback-compatible, not automatically dead. Keep them until edge ingress and rollback strategy are validated.
- Interfaces named `*RealtimeEventPublisher` appear unused across chat, presence, notification, and friendship. These look like transitional abstractions that should either get wired or removed.
- Deprecated adapters are still registered as beans, especially `NotificationRedisRealtimeAdapter`. Marking as deprecated is not enough if it still lives in the runtime context.

Consistency problems:

- Kafka topic usage is inconsistent for friendship request events: friendship producer and realtime-edge use aggregate `friendship.request.events`, while notification listens to event-name strings.
- Gateway still routes websocket traffic to old services, while realtime-edge exposes the replacement aliases. This may be intentional rollout behavior, but service configuration alone does not show a unified edge ingress.
- Realtime-edge uses a different Spring Cloud BOM than nearby services.

Edge migration effect:

The migration made the target direction clearer: realtime-edge owns websocket ingress, and services should own domain/application commands and event publication. But the current codebase is in a transitional middle state. It is cleaner conceptually than before, but messier in code because old service websocket paths, temporary adapters, unused app services, and duplicated event consumers still coexist.

# 5. Real Blockers

1. Kafka producer bean mismatch. `common-kafka` auto-configures `KafkaEventPublisher`, while auth/chat/friendship/notification inject `KafkaEventProducer`. Auth context test proved this fails at runtime.
2. `common-websocket` auto-configuration import has a BOM/hidden leading character. Presence context test proved Spring tries to load the wrong class name.
3. Presence direct websocket disconnect does not mark the user offline because the session is unregistered before `PresenceConnectionLifecycleAdapter` reads `userId`.
4. Realtime-edge port is under `spring.server.port`, so the service likely starts on the wrong port.
5. Notification friend-request consumer likely listens to the wrong topic and will miss current friendship request events.

Not blockers, but important cleanup debt:

- Gateway has no realtime-edge route; this is a blocker only if gateway is expected to own edge ingress now.
- Chat's duplicate room application services are maintainability debt, not a compile/runtime blocker by themselves.
- Friendship's service-local `/ws/friendship` endpoint is misleading, but can be rollback-compatible if intentionally retained.
- Upload Cloudinary `folder` plus folder-prefixed `public_id` behavior needs validation, not a proven blocker.

# 6. Cleanup Candidates

## must clean soon

- Align service Kafka producer usage with the actually registered publisher contract, or add service-local adapter beans.
- Fix the `common-websocket` auto-configuration import hidden-character issue. This is allowed under the frozen rule because it is a proven runtime failure.
- Fix presence direct websocket disconnect ordering.
- Correct `realtime-edge-service` port configuration to top-level `server.port`.
- Fix notification friend-request Kafka listener topic.
- Decide and document whether gateway or external routing owns realtime-edge websocket ingress.

## should clean later

- Remove user-service's compile-time dependency on `upload-service`; use a local avatar metadata DTO/contract.
- Wire or remove `UserKafkaAccountCreatedApplicationService`.
- Make notification Kafka consumers consistently delegate to application services, or remove unused aggregate application service.
- Remove deprecated `NotificationRedisRealtimeAdapter` after confirming the primary websocket publisher is the intended path.
- Wire or remove chat room split services; do not keep both `RoomService` and unused room application services as active beans indefinitely.
- Remove or quarantine unused realtime publisher interfaces across chat/presence/notification/friendship.
- Remove or archive `realtime-edge-service`'s unscanned `com.example.realtimeedge` skeleton tree.
- Re-enable excluded notification/friendship tests after migration wiring is corrected.
- Update stale gateway CORS tests.
- Move auth/user startup schema fixers out of runtime application code.

## leave as-is for now

- Legacy service-local websocket endpoints, if they are intentionally rollback-compatible during edge validation.
- Realtime-edge legacy aliases `/ws/notifications`, `/ws/friendship`, `/ws/presence`, and `/ws/chat`.
- Chat deprecated room avatar direct upload route, until the upload-service confirmation flow is validated end to end.
- Upload-service structure and policy registry, which are currently clean.

# 7. Final Verdict

Is the current service-layer code likely to run?

Partially. All scoped services compile, and `realtime-edge-service:test` passed. However, the full backend should not be considered runtime-clean until the Kafka producer bean mismatch, common-websocket auto-configuration import issue, presence disconnect bug, realtime-edge port config, and notification friendship topic are fixed.

Is the service structure acceptable?

Mostly acceptable, but not clean. Gateway and upload are clean. Auth/user/presence are workable with targeted cleanup. Notification/friendship/chat still carry substantial migration debt. Realtime-edge has a good active structure but also a stale skeleton package that should be removed.

Is the architecture still coherent?

Yes, at the intended direction level: services own domain logic, realtime-edge is moving toward websocket ownership, and common is mostly contract/support code. The coherence is weakened by leftover service-local websocket paths, duplicate adapters, unused application services, and inconsistent Kafka producer/topic usage.

Is the code clean enough to continue?

Yes, but not clean enough to freeze. Continue development only after addressing the real blockers. After that, do a service-local cleanup pass focused on dead/transitional code, not common refactors.

Should the next step be cleanup or validation/runtime execution?

First fix the real runtime blockers, then run validation/runtime execution. After those pass, do cleanup. Running more end-to-end validation before fixing the proven blockers will mostly rediscover the same failures.
