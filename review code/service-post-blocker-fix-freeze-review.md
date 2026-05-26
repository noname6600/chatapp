# Service Layer Post-Blocker-Fix Freeze Review

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
  - deployment/infrastructure, except minimal `gateway-service` routing/security/CORS configuration needed to understand service boundaries
  - database schema outside service-owned code/resources directly referenced by these modules
  - UI/client behavior
- Boundary limits applied:
  - This review judged current service startup, compile/test health, runtime wiring, route ownership, service boundaries, adapter thinness, and realtime migration readiness only.
  - Common-layer redesign remains out of scope.
  - Package/folder naming issues were not treated as blockers unless they caused a runtime, compile, integration, or boundary failure.

## 2. Build/Test Validation
- Commands run:
  - `.\gradlew.bat :auth-service:compileJava :user-service:compileJava :chat-service:compileJava :presence-service:compileJava :notification-service:compileJava :friendship-service:compileJava :upload-service:compileJava :gateway-service:compileJava`
  - `.\gradlew.bat :auth-service:test :user-service:test :chat-service:test :presence-service:test :notification-service:test :friendship-service:test :upload-service:test :gateway-service:test`
  - `.\gradlew.bat --rerun-tasks :auth-service:compileJava :user-service:compileJava :chat-service:compileJava :presence-service:compileJava :notification-service:compileJava :friendship-service:compileJava :upload-service:compileJava :gateway-service:compileJava`
  - `.\gradlew.bat --rerun-tasks :auth-service:test :user-service:test :chat-service:test :presence-service:test :notification-service:test :friendship-service:test :upload-service:test :gateway-service:test`
- Compile status:
  - Fresh compile passed for all reviewed service modules.
  - Forced compile result: `BUILD SUCCESSFUL`, 23 tasks executed.
  - Compiled service modules: `auth-service`, `user-service`, `chat-service`, `presence-service`, `notification-service`, `friendship-service`, `upload-service`, `gateway-service`.
- Test status:
  - Fresh service test run passed for all reviewed service modules.
  - Forced test result: `BUILD SUCCESSFUL`, 61 tasks executed.
  - Tested service modules and counts:
    - `auth-service`: 13 suites, 47 tests, 0 failures, 0 errors, 0 skipped
    - `user-service`: 6 suites, 15 tests, 0 failures, 0 errors, 0 skipped
    - `chat-service`: 31 suites, 93 tests, 0 failures, 0 errors, 0 skipped
    - `presence-service`: 2 suites, 4 tests, 0 failures, 0 errors, 0 skipped
    - `notification-service`: 6 suites, 19 tests, 0 failures, 0 errors, 0 skipped
    - `friendship-service`: 4 suites, 6 tests, 0 failures, 0 errors, 0 skipped
    - `upload-service`: 3 suites, 5 tests, 0 failures, 0 errors, 0 skipped
    - `gateway-service`: 5 suites, 17 tests, 0 failures, 0 errors, 0 skipped
- Failures/errors/warnings:
  - No compile failures.
  - No test failures.
  - Remaining warnings only:
    - Gradle incubating problems-report notice.
    - unchecked/unsafe operation notes in common/core and some service tests.
    - deprecated API notes in `auth-service` Resend/local auth tests.
    - deprecated `@MockBean` warnings in `chat-service` and `upload-service` tests.
    - OpenJDK class sharing warning during tests.

## 3. What Improved
- Service startup health is materially better:
  - `chat-service`, `presence-service`, `notification-service`, and `friendship-service` application context tests pass with the current websocket auto-configuration exclusions.
  - `auth-service`, `user-service`, and `upload-service` context tests also pass.
  - `gateway-service` integration tests cover random-port startup, readiness, CORS, auth error propagation, and security behavior.
- Cache-manager wiring is no longer a compile/startup blocker:
  - `user-service/src/main/java/com/example/user/configuration/RedisCacheConfig.java` defines a `TimeRedisCacheManager` bean.
  - `chat-service/src/main/java/com/example/chat/config/RedisCacheConfig.java` defines a `TimeRedisCacheManager` bean.
  - Both modules compile and pass context/tests.
- Chat outbound publisher wiring is partially restored:
  - `chat-service/src/main/java/com/example/chat/modules/message/infrastructure/redis/ChatMessageEventPublisherAdapter.java` is a Spring component implementing `IMessageEventPublisher` and `IReactionEventPublisher`.
  - Message/reaction application code now has an injectable publisher bean, so the previous missing-bean style failure is gone.
- Notification outbound realtime wiring is present:
  - `notification-service/src/main/java/com/example/notification/realtime/infrastructure/NotificationRedisRealtimeAdapter.java` implements `NotificationRealtimePort`.
  - `notification-service/src/main/java/com/example/notification/service/impl/NotificationPushService.java` delegates notification fanout through that port.
- Gateway CORS properties are registered:
  - `gateway-service/src/main/java/com/example/gateway/GatewayApplication.java` enables `CorsProperties`.
  - `gateway-service/src/main/java/com/example/gateway/config/GatewayConfig.java` also enables `CorsProperties` and registers `CorsWebFilter`.
- No deployable service-to-service Gradle project dependency was found among the reviewed services:
  - Service modules depend on `common:*` projects and external libraries, not on each other as Gradle projects.
  - Runtime service-to-service calls are via Feign/HTTP or event publication rather than direct service-module compile dependencies.

## 4. Freeze Blockers
### 1. Notification event adapters are still not wired end to end for non-account events
- Exact service/module/file/package/class:
  - `notification-service/src/main/java/com/example/notification/application/NotificationKafkaEventApplicationService.java`, package `com.example.notification.application`, class `NotificationKafkaEventApplicationService`
  - `notification-service/src/main/java/com/example/notification/kafka/AccountCreatedEventConsumer.java`, package `com.example.notification.kafka`, class `AccountCreatedEventConsumer`
  - `chat-service/src/main/java/com/example/chat/modules/message/infrastructure/redis/ChatMessageEventPublisherAdapter.java`, package `com.example.chat.modules.message.infrastructure.redis`, class `ChatMessageEventPublisherAdapter`
- Evidence:
  - `NotificationKafkaEventApplicationService` defines handlers for account creation, chat message sent, reaction updated, and friend request events.
  - The only runtime listener found in `notification-service` is `AccountCreatedEventConsumer`, and it only listens to `account.created`.
  - The chat message/reaction publisher currently delegates to `ChatRedisPublisher`; no notification Redis subscriber or Kafka listener is present for those chat payloads in `notification-service`.
- Why it is a blocker:
  - This is a runtime integration break, not naming or style debt.
  - The service contains application handlers for chat/reaction/friendship notification behavior, but the handlers are not reachable from the current runtime adapters except for account-created welcome notification.
- Impact:
  - Chat message notifications, reaction notifications, and friendship request notifications will not be created through the service layer as it stands.
  - Integration/realtime-edge migration would start from an unproved and partially disconnected event path.
- Narrow recommended next fix:
  - Add only the missing runtime adapters and focused wiring tests:
    - Wire notification inbound adapters for chat message sent, reaction updated, and friendship request events to the existing `NotificationKafkaEventApplicationService` methods.
    - Align the chat publisher side to the same selected transport those notification adapters consume, or add notification Redis subscribers if Redis is the intended bridge.
    - Do not reopen broader notification, common-event, or package-structure refactors.

### 2. Chat realtime adapter silently drops room membership events emitted by application services
- Exact service/module/file/package/class:
  - `chat-service/src/main/java/com/example/chat/realtime/infrastructure/ChatRealtimeAdapter.java`, package `com.example.chat.realtime.infrastructure`, class `ChatRealtimeAdapter`
  - `chat-service/src/main/java/com/example/chat/modules/room/service/impl/RoomMembershipApplicationService.java`, package `com.example.chat.modules.room.service.impl`, class `RoomMembershipApplicationService`
  - `chat-service/src/main/java/com/example/chat/modules/room/service/impl/RoomModerationApplicationService.java`, package `com.example.chat.modules.room.service.impl`, class `RoomModerationApplicationService`
- Evidence:
  - `RoomMembershipApplicationService` emits `MEMBER_JOINED`, `MEMBER_LEFT`, and `MEMBER_REMOVED` through `ChatRealtimePort`.
  - `RoomModerationApplicationService` emits `MEMBER_REMOVED` through `ChatRealtimePort`.
  - `ChatRealtimeAdapter.publishRoomEvent(...)` only forwards pin/unpin payloads and logs that non-pin room fanout is skipped.
  - `ChatRealtimeAdapter.publishUserEvent(...)` ignores direct user fanout.
- Why it is a blocker:
  - This is a runtime correctness bug in the service layer: application services emit realtime events that the active adapter knowingly drops.
  - It is not merely a temporary websocket route issue; the loss happens at the service adapter boundary before any future realtime-edge handoff.
- Impact:
  - Room join/leave/remove realtime behavior is absent even though the application services signal it.
  - Future realtime-edge migration would inherit ambiguous semantics because the service port currently suggests events are published while the adapter suppresses them.
- Narrow recommended next fix:
  - Make `ChatRealtimeAdapter` publish all supported `ChatRealtimePort` room events through the current event publisher, including membership events, or explicitly replace those emits with the durable event path that realtime-edge will consume.
  - Add a focused test proving `MEMBER_JOINED`, `MEMBER_LEFT`, and `MEMBER_REMOVED` are delivered to the outbound publisher.
  - Do not reintroduce service-local websocket handlers or reopen broad websocket ownership refactors.

## 5. Post-Refactor Cleanup
### 1. Package declarations and physical file paths are inconsistent in several service modules
- Exact service/module/file/package/class:
  - `auth-service/src/main/java/com/example/auth/controller/AuthController.java` declares `package com.example.auth.auth.adapter.in.rest`
  - `user-service/src/main/java/com/example/user/controller/UserProfileController.java` declares `package com.example.user.profile.adapter.in.rest`
  - `presence-service/src/main/java/com/example/presence/controller/PresenceController.java` declares `package com.example.presence.presence.adapter.in.rest`
  - `notification-service/src/main/java/com/example/notification/controller/NotificationController.java` declares `package com.example.notification.notification.adapter.in.rest`
  - `friendship-service/src/main/java/com/example/friendship/controller/FriendController.java` declares `package com.example.friendship.friendship.adapter.in.rest`
  - `upload-service/src/main/java/com/example/upload/controller/UploadController.java` declares `package com.example.upload.file.adapter.in.rest`
- Why it is cleanup only:
  - The modules compile and the application context tests pass.
  - Component scanning currently reaches these packages.
  - This is package/file layout debt, not a current runtime or boundary blocker.
- Recommended follow-up:
  - Align file paths and package names after the freeze-critical event wiring is repaired.

### 2. Websocket auto-configuration exclusion strings are awkward and should be normalized
- Exact service/module/file/package/class:
  - `chat-service/src/main/java/com/example/chat/ChatServiceApplication.java`
  - `presence-service/src/main/java/com/example/presence/PresenceServiceApplication.java`
  - `notification-service/src/main/java/com/example/notification/NotificationServiceApplication.java`
  - `friendship-service/src/main/java/com/example/friendship/FriendshipServiceApplication.java`
- Why it is cleanup only:
  - The relevant service context tests pass, so this is not currently a startup blocker.
  - The `excludeName = "\uFEFFcom.example.common.websocket.config.RealtimeWebSocketAutoConfiguration"` spelling is easy to misread and brittle for maintainers.
- Recommended follow-up:
  - Normalize the exclusion string or replace it with a clearer local opt-out mechanism during the next service cleanup pass.

### 3. Gateway websocket routes are transitional and should be removed or replaced during realtime-edge cutover
- Exact service/module/file/package/class:
  - `gateway-service/src/main/resources/application.yaml`, routes `chat-service-ws`, `presence-service-ws`, `friendship-service-ws`, `notification-service-ws`
  - `gateway-service/src/main/java/com/example/gateway/config/SecurityConfig.java`, public `/ws/**` allowance
- Why it is cleanup only:
  - The routes are explicitly documented as temporary.
  - Gateway compile and integration tests pass.
  - The review rule says temporary transitional websocket routes that are acknowledged and not breaking startup are cleanup, not freeze blockers.
- Recommended follow-up:
  - Remove or retarget these routes when realtime-edge owns websocket ingress.

### 4. Friendship realtime publisher interface is currently unused
- Exact service/module/file/package/class:
  - `friendship-service/src/main/java/com/example/friendship/realtime/publisher/FriendshipRealtimeEventPublisher.java`, package `com.example.friendship.realtime.publisher`, interface `FriendshipRealtimeEventPublisher`
- Why it is cleanup only:
  - Friendship command flow currently publishes events through `FriendshipEventProducer`.
  - No compile/startup failure results from the unused interface.
- Recommended follow-up:
  - Remove it or bind it to the final realtime-edge/event publication path after the blocking notification/event adapter wiring is corrected.

### 5. Some comments still describe Kafka while the active implementation is Redis or transport-neutral
- Exact service/module/file/package/class:
  - `chat-service/src/main/java/com/example/chat/modules/message/application/pipeline/send/steps/PublishMessageEventStep.java`, class `PublishMessageEventStep`
  - `chat-service/src/main/java/com/example/chat/modules/message/infrastructure/redis/ChatMessageEventPublisherAdapter.java`, class `ChatMessageEventPublisherAdapter`
- Why it is cleanup only:
  - The comment mismatch itself does not break compile or startup.
  - The functional transport mismatch is already covered as a freeze blocker above.
- Recommended follow-up:
  - After the blocker fix chooses and wires the real event transport, update comments to match the actual runtime path.

## 6. Service Freeze Readiness
- Verdict for the service layer as a whole: NO
- Brief reason:
  - Compile and service tests are green, and the earlier startup/cache/CORS missing-bean blockers are no longer present.
  - The freeze decision must be based only on Freeze Blockers, and two runtime wiring/correctness blockers remain: notification event adapters are not wired end to end for non-account events, and chat realtime membership events are emitted but silently dropped by the active adapter.

## 7. Final Recommendation
- NO: only the listed blockers should be fixed next.
- Do not open new broad refactor phases before those blockers are resolved.
- Once those narrow event/realtime wiring fixes are verified, the service layer can be re-checked for freeze and then moved toward integration/realtime-edge migration rather than more structural churn.
- Common-layer redesign remains explicitly out of scope.
