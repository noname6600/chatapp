# Service Layer Post-Phase-9 Freeze Review

## 1. Scope Reviewed
- Reviewed service/app modules:
  - `chatappBE/auth-service`
  - `chatappBE/user-service`
  - `chatappBE/chat-service`
  - `chatappBE/presence-service`
  - `chatappBE/notification-service`
  - `chatappBE/friendship-service`
  - `chatappBE/upload-service`
  - `chatappBE/gateway-service`
- Exact exclusions:
  - `chatappBE/common/**`
  - frontend
  - deployment/infrastructure except `gateway-service` routing/security/readiness config needed to understand service boundaries
  - database schema outside entities/repositories/config directly owned by the reviewed service code
  - UI/client behavior
- Boundary limits applied:
  - Service Java code, service resources, service `build.gradle` files, and gateway route/config ownership were reviewed.
  - `common/**` was not architecture-reviewed. It was only referenced to interpret service build/test failures against the current shared foundation.

## 2. Build/Test Validation
- Commands run:
  - `Get-ChildItem -Path chatappBE -Directory | Select-Object -ExpandProperty Name`
  - `rg --files chatappBE -g '!chatappBE/common/**' -g '*.java'`
  - `.\gradlew.bat --no-daemon :auth-service:compileJava :user-service:compileJava :chat-service:compileJava :presence-service:compileJava :notification-service:compileJava :friendship-service:compileJava :upload-service:compileJava :gateway-service:compileJava`
  - `.\gradlew.bat --no-daemon :auth-service:test :user-service:test :chat-service:test :presence-service:test :notification-service:test :friendship-service:test :upload-service:test :gateway-service:test`
  - `.\gradlew.bat --no-daemon --continue :auth-service:test :user-service:test :chat-service:test :presence-service:test :notification-service:test :friendship-service:test :upload-service:test :gateway-service:test`
  - Static boundary checks with `rg` for cross-service imports, service-to-service Gradle project dependencies, REST route mappings, websocket classes, Kafka listeners/handlers, and realtime port implementations.
- Compile status:
  - All eight reviewed service modules compiled successfully.
  - Gradle also compiled required `common` dependencies as build prerequisites; those modules were not included in the architecture review scope.
- Test status:
  - `auth-service`: 47 tests, 0 failures.
  - `user-service`: 15 tests, 1 failure.
  - `chat-service`: 93 tests, 1 failure.
  - `presence-service`: 4 tests, 1 failure.
  - `notification-service`: 19 tests, 1 failure.
  - `friendship-service`: 6 tests, 1 failure.
  - `upload-service`: 5 tests, 0 failures.
  - `gateway-service`: 17 tests, 9 failures.
- Failures/errors/warnings:
  - `user-service` failed `com.example.user.ChatappApplicationTests.contextLoads`: `UserProfileService` could not get a bean of type `com.example.common.redis.api.ITimeRedisCacheManager`.
  - `chat-service`, `presence-service`, `notification-service`, and `friendship-service` failed their `ChatappApplicationTests.contextLoads` tests while Spring read `common-websocket` auto-configuration metadata: the classpath resource name contains a leading `U+FEFF` before `com.example.common.websocket.config.RealtimeWebSocketAutoConfiguration`.
  - `gateway-service` failed `AuthErrorPayloadPropagationIntegrationTest`, `GatewayCorsIntegrationTest`, and `GatewayReadinessIntegrationTest`: `GatewayConfig` could not get a bean of type `com.example.common.web.cors.CorsProperties`.
  - JVM class-data-sharing warnings and Hibernate teardown SQL logs were observed; these are not freeze blockers.
- Modules that could not be classified confidently:
  - No module was skipped. Full Spring context behavior for `chat-service`, `presence-service`, `notification-service`, and `friendship-service` remains masked by the websocket auto-configuration metadata failure, but static service-scope inspection found additional concrete wiring blockers in `chat-service` and `notification-service`.

## 3. What Improved
- Deployable service boundaries are materially cleaner: no reviewed service has a Gradle `project(':...-service')` dependency on another deployable service, and static import checks found no direct Java imports from one deployable service package into another.
- Route namespace ownership is mostly coherent: auth, users, rooms/messages, presence, friendship, notifications, upload, and gateway fallback/readiness live behind distinct route namespaces. Gateway legacy aliases rewrite toward the owned `/api/v1/**` namespaces instead of creating a second owner.
- REST controllers are generally thin. They extract principals, validate simple request shape, map path/body values, and delegate to service/application classes.
- Service-local websocket ownership has been reduced substantially. The reviewed service modules no longer contain service-local `WebSocketHandler`, websocket session registry, websocket broadcaster, or websocket config classes. Remaining websocket exposure is mostly temporary gateway routing and service-local realtime port abstractions.
- Realtime publication intent is more explicit: chat, presence, notification, and friendship now expose service-local realtime publisher/port concepts rather than embedding websocket fanout directly in controllers.
- Chat room behavior has been decomposed into focused application/service classes such as `RoomLifecycleApplicationService`, `RoomMembershipApplicationService`, `RoomModerationApplicationService`, `RoomMessageStateApplicationService`, and `RoomReadStateApplicationService`.
- Upload is isolated as its own service surface with prepare/confirm commands and policy-backed signing behavior, instead of being mixed into profile or chat controllers.

## 4. Freeze Blockers
- `chatappBE/chat-service/build.gradle`, `chatappBE/presence-service/build.gradle`, `chatappBE/notification-service/build.gradle`, `chatappBE/friendship-service/build.gradle`; application context tests for `com.example.chat.ChatappApplicationTests`, `com.example.presence.ChatappApplicationTests`, `com.example.notification.ChatappApplicationTests`, `com.example.friendship.ChatappApplicationTests`.
  - Why it is a blocker: these services declare the websocket shared foundation and currently fail application-context startup because Spring tries to read an auto-configuration class name with a leading `U+FEFF`.
  - Impact: four deployable service contexts cannot be proven startable, so integration work would be testing against a known broken runtime baseline.
  - Narrow recommended next fix: correct the auto-configuration imports resource/name or temporarily remove the `common-websocket` dependency from services that no longer need local websocket startup. This is a metadata/startup fix, not a common-layer redesign.

- `chatappBE/user-service/src/main/java/com/example/user/service/impl/UserProfileService.java` (`com.example.user.profile.application.UserProfileService`), `chatappBE/user-service/src/main/java/com/example/user/configuration/RedisCacheConfig.java` (`com.example.user.config.RedisCacheConfig`), `chatappBE/chat-service/src/main/java/com/example/chat/modules/room/cache/redis/RedisRoomListCacheAdapter.java`, `chatappBE/chat-service/src/main/java/com/example/chat/config/RedisCacheConfig.java`.
  - Why it is a blocker: `UserProfileService` and `RedisRoomListCacheAdapter` inject `com.example.common.redis.api.ITimeRedisCacheManager`, while the local service cache configs expose `com.example.common.redis.cache.core.TimeRedisCacheManager`. `user-service` already fails context startup with `NoSuchBeanDefinitionException`; `chat-service` has the same service-local type alignment risk, masked by the earlier websocket metadata failure.
  - Impact: user profile reads/writes and chat room-list cache wiring are not safely startable. This is runtime wiring breakage, not naming polish.
  - Narrow recommended next fix: align the service imports and bean return types to one cache-manager API/class, or expose the local cache-manager bean under the exact interface injected by the service code.

- `chatappBE/chat-service/src/main/java/com/example/chat/modules/message/application/service/IMessageEventPublisher.java`, `chatappBE/chat-service/src/main/java/com/example/chat/modules/message/application/service/IReactionEventPublisher.java`, `chatappBE/chat-service/src/main/java/com/example/chat/modules/message/infrastructure/redis/ChatRedisPublisher.java`, and injectors including `PublishMessageEventStep`, `PublishMessageEditedEventStep`, `PublishMessageDeletedEventStep`, `PublishReactionEventStep`, `MessageCommandService`, and `SystemMessageService`.
  - Why it is a blocker: static inspection found no service bean implementing `IMessageEventPublisher` or `IReactionEventPublisher`. `ChatRedisPublisher` publishes chat events but does not implement either required application port.
  - Impact: once the earlier websocket metadata failure is removed, `chat-service` is likely to fail context startup; even if manually wired, message/reaction event publication is structurally disconnected.
  - Narrow recommended next fix: add exactly one thin outbound adapter bean, or two small beans, implementing those publisher interfaces and delegating to the intended Redis/Kafka/stream publisher without moving business orchestration into the adapter.

- `chatappBE/notification-service/src/main/java/com/example/notification/service/impl/NotificationPushService.java` (`com.example.notification.notification.application.NotificationPushService`), `chatappBE/notification-service/src/main/java/com/example/notification/realtime/port/NotificationRealtimePort.java`, `chatappBE/notification-service/src/main/java/com/example/notification/application/NotificationKafkaEventApplicationService.java`.
  - Why it is a blocker: `NotificationPushService` injects `NotificationRealtimePort`, but no reviewed service class implements that port. Separately, `NotificationKafkaEventApplicationService` contains account/chat/reaction/friend-request handling methods, but no `@KafkaListener`, `KafkaEventHandler`, Redis subscriber, or other transport adapter in `notification-service` references it.
  - Impact: notification-service cannot be considered startable or behaviorally integrated. Event-created notifications and unread-count realtime publication are disconnected from transport.
  - Narrow recommended next fix: add a thin realtime adapter implementing `NotificationRealtimePort`, and add thin inbound event adapter(s) that subscribe/handle the intended event transport and delegate to `NotificationKafkaEventApplicationService`.

- `chatappBE/gateway-service/src/main/java/com/example/gateway/config/GatewayConfig.java` and `chatappBE/gateway-service/src/main/java/com/example/gateway/GatewayApplication.java`.
  - Why it is a blocker: `GatewayConfig` requires `CorsProperties`, but `GatewayApplication` only enables `GatewayReadinessProperties`; gateway tests fail with `NoSuchBeanDefinitionException` for `com.example.common.web.cors.CorsProperties`.
  - Impact: the gateway application context is not startable, so gateway routing, CORS, auth-error propagation, and readiness behavior cannot be frozen.
  - Narrow recommended next fix: register/enable `CorsProperties` in the gateway application/configuration, or otherwise make the existing common-web CORS properties bean visible to the gateway context.

## 5. Post-Refactor Cleanup
- `auth-service/src/main/java/com/example/auth/controller/AuthController.java`, `user-service/src/main/java/com/example/user/controller/UserProfileController.java`, `presence-service/src/main/java/com/example/presence/controller/PresenceController.java`, `notification-service/src/main/java/com/example/notification/controller/*.java`, `friendship-service/src/main/java/com/example/friendship/controller/*.java`, `upload-service/src/main/java/com/example/upload/controller/UploadController.java`, `gateway-service/src/main/java/com/example/gateway/controller/FallbackController.java`.
  - Why it is cleanup only: several files now declare adapter/application-style packages while still living in older physical folders. Java compiles this, component scanning still reaches them, and it does not create a current service boundary violation by itself.
  - Recommended follow-up: after blockers are fixed, align physical folders with declared packages or choose one package convention per service.

- `chatappBE/gateway-service/src/main/resources/application.yaml` and `chatappBE/gateway-service/src/main/java/com/example/gateway/config/SecurityConfig.java`.
  - Why it is cleanup only: gateway still carries temporary `/ws/**` pass-through routes to service-local websocket adapters. This is explicitly labeled transitional and does not by itself contradict the next realtime-edge migration.
  - Recommended follow-up: remove or redirect these routes during realtime-edge cutover, not as another service-layer refactor phase.

- `chatappBE/chat-service/src/main/java/com/example/chat/modules/message/application/pipeline/send/steps/PublishMessageEventStep.java`.
  - Why it is cleanup only: comments still describe Kafka publishing while the current concrete publisher code is Redis-oriented. The real blocker is the missing publisher bean; the comment mismatch is documentation debt.
  - Recommended follow-up: update comments after the actual event transport wiring is fixed.

- `chatappBE/friendship-service/src/main/java/com/example/friendship/application/FriendshipKafkaEventApplicationService.java`.
  - Why it is cleanup only: the class appears unused after local realtime consumer removal. Since friendship command flow currently publishes its own events through `FriendshipEventProducer`, this unused planning class is low-risk dead code unless a future adapter is meant to call it.
  - Recommended follow-up: either wire it through a thin adapter if it remains part of the realtime migration plan, or delete it after realtime-edge ownership is settled.

- `chatappBE/friendship-service/src/main/java/com/example/friendship/kafka/FriendshipEventProducer.java`.
  - Why it is cleanup only: the Kafka publisher enriches friend-request events by calling `UserClient` and choosing display-name fallback inside the adapter. This is not currently a compile-time service dependency or route ownership contradiction, but it is adapter-thickness debt.
  - Recommended follow-up: move sender display-name enrichment into an application service or make the event carry IDs only, depending on the notification contract chosen during integration.

## 6. Service Freeze Readiness
- Verdict for the service layer as a whole: NO.
- Reason: the decision is based only on Freeze Blockers. The reviewed service layer compiles, but multiple deployable service contexts are not currently startable or have missing runtime adapter beans/critical event wiring. Cleanup items did not affect this verdict.

## 7. Final Recommendation
- Do not freeze the service layer yet.
- Fix only the listed Freeze Blockers next. Do not open another broad service refactor phase for naming, folder layout, comments, or transitional websocket polish.
- After those blockers are fixed and the same compile/test validation passes, the service structure should be treated as frozen enough to move to integration and realtime-edge migration work.
- Common-layer redesign remains out of scope.
