# Service Layer Freeze Confirmation Review

## 1. Scope Reviewed
- Reviewed service/application modules under `chatappBE/**`:
  - `chatappBE/auth-service`
  - `chatappBE/user-service`
  - `chatappBE/chat-service`
  - `chatappBE/presence-service`
  - `chatappBE/notification-service`
  - `chatappBE/friendship-service`
  - `chatappBE/upload-service`
  - `chatappBE/gateway-service`
- Exact exclusions applied:
  - `chatappBE/common/**`
  - frontend
  - deployment/infrastructure, except minimal gateway routing/config needed to understand service ownership
  - database schema outside service-owned code directly used by these services
  - UI/client behavior
- Boundary limits applied:
  - Reviewed service runtime wiring, Gradle service dependencies, service-owned controllers/consumers/adapters/application services, gateway route ownership, and service-level tests.
  - Common-layer internals were treated as dependency context only, not as reviewed design surface.

## 2. Build/Test Validation
- Commands run from `chatappBE`:
  - `.\gradlew.bat :auth-service:compileJava :user-service:compileJava :chat-service:compileJava :presence-service:compileJava :notification-service:compileJava :friendship-service:compileJava :upload-service:compileJava :gateway-service:compileJava --no-daemon`
  - `.\gradlew.bat :auth-service:test :user-service:test :chat-service:test :presence-service:test :notification-service:test :friendship-service:test :upload-service:test :gateway-service:test --no-daemon --rerun-tasks`
  - Focused proof: `.\gradlew.bat :notification-service:test --tests com.example.notification.kafka.NotificationKafkaDeserializationWiringTest --no-daemon --rerun-tasks`
- Compile status:
  - Passed. `BUILD SUCCESSFUL`, 23 compile tasks up-to-date.
- Test status:
  - Passed. `BUILD SUCCESSFUL`, 61 tasks executed for the full service test run.
  - Focused notification Kafka deserialization test also passed. `BUILD SUCCESSFUL`, 24 tasks executed.
- Failures/errors/warnings:
  - No compile failures.
  - No test failures.
  - Non-blocking warnings observed:
    - `auth-service` production/test code uses deprecated APIs in Resend/local auth test paths.
    - `chat-service` tests use deprecated `@MockBean` in `MessageCommandServiceForwardTransactionalRollbackTest`.
    - `upload-service` tests use deprecated `@MockBean` in `UploadControllerPurposeDeserializationTest`.
    - `notification-service` test code has unchecked-operation warnings in `NotificationCommandServiceTest`.
    - Gradle emitted an incubating problems report and JVM CDS warnings. None affected compile/test success.

## 3. What Improved
- `notification-service/src/main/resources/application.yaml` now uses `org.springframework.kafka.support.serializer.ErrorHandlingDeserializer` with `com.example.common.kafka.serialization.EventEnvelopeKafkaDeserializer` as the delegate for Kafka consumer values.
- `notification-service/src/test/java/com/example/notification/kafka/NotificationKafkaDeserializationWiringTest.java` provides a meaningful production-style wiring proof: it serializes an `EventEnvelope<ChatMessagePayload>` and deserializes it through the configured `ErrorHandlingDeserializer` delegate chain, verifying the resulting payload type.
- Notification Kafka transport adapters are thin:
  - `MessageCreatedEventConsumer`
  - `ReactionEventConsumer`
  - `FriendRequestEventConsumer`
  - `AccountCreatedEventConsumer`
  Each performs null guarding/event metadata extraction and delegates to `NotificationKafkaEventApplicationService`.
- Notification event orchestration is now in application services instead of Kafka listener methods:
  - `NotificationKafkaEventApplicationService`
  - `NotificationMessageEventApplicationService`
  - `NotificationReactionEventApplicationService`
  - `NotificationFriendRequestEventApplicationService`
- Service boundary checks found no Gradle compile-time dependency from one deployable service module to another deployable service module. Service modules depend on common modules and external libraries only.
- Gateway route ownership is coherent enough for the next phase:
  - auth: `/api/v1/auth/**`, `/api/auth/**`
  - user: `/api/v1/users/**`, `/api/users/**`
  - chat: `/api/v1/rooms/**`, `/api/v1/messages/**`, compatibility `/api/v1/chat/**`/`/api/chat/**` rewrites
  - presence: `/api/v1/presence/**`, `/api/presence/**`
  - friendship: `/api/v1/friendship/**`, `/api/friendship/**`
  - notification: `/api/v1/notifications/**`, `/api/notifications/**`
  - upload: `/api/v1/upload/**`, `/api/upload/**`
- Realtime output is generally behind service-local ports/adapters rather than direct controller-owned websocket orchestration:
  - `ChatRealtimePort` with `ChatRealtimeAdapter`
  - `PresenceRealtimePort` with `PresenceRedisPublisher`
  - `NotificationRealtimePort` with `NotificationRedisRealtimeAdapter`

## 4. Freeze Blockers
- None.

## 5. Post-Refactor Cleanup
- `notification-service/src/main/resources/application.yaml`
  - Package/class: service configuration, `spring.kafka.consumer.group-id`
  - Why cleanup only: the default consumer group is still `user-service`, but all active notification Kafka listeners explicitly set `groupId = "notification-service"` in `AccountCreatedEventConsumer`, `MessageCreatedEventConsumer`, `ReactionEventConsumer`, and `FriendRequestEventConsumer`. Current runtime behavior is therefore not blocked.
  - Recommended follow-up: rename the default group to `notification-service` during config cleanup so future listeners inherit the correct default.

- `chat-service/src/main/java/com/example/chat/ChatServiceApplication.java`
  - Package/class: `com.example.chat.ChatServiceApplication`
  - Why cleanup only: `@SpringBootApplication(excludeName = "\uFEFFcom.example.common.websocket.config.RealtimeWebSocketAutoConfiguration")` contains a BOM-prefixed exclude target. Current compile/test validation passes, and gateway websocket routes are explicitly marked as temporary service-local websocket ingress pending realtime-edge cutover.
  - Recommended follow-up: normalize or remove the stale exclude target during realtime-edge migration cleanup.

- `presence-service/src/main/java/com/example/presence/PresenceServiceApplication.java`
  - Package/class: `com.example.presence.PresenceServiceApplication`
  - Why cleanup only: same BOM-prefixed websocket auto-configuration exclude target as above. It is not causing startup/test failure under the current transitional websocket routing model.
  - Recommended follow-up: normalize or remove the stale exclude target during realtime-edge migration cleanup.

- `notification-service/src/main/java/com/example/notification/NotificationServiceApplication.java`
  - Package/class: `com.example.notification.NotificationServiceApplication`
  - Why cleanup only: same BOM-prefixed websocket auto-configuration exclude target as above. Notification realtime output is already behind `NotificationRealtimePort`/`NotificationRedisRealtimeAdapter`, and current service validation passes.
  - Recommended follow-up: normalize or remove the stale exclude target during realtime-edge migration cleanup.

- `friendship-service/src/main/java/com/example/friendship/FriendshipServiceApplication.java`
  - Package/class: `com.example.friendship.FriendshipServiceApplication`
  - Why cleanup only: same BOM-prefixed websocket auto-configuration exclude target as above. Current core friendship HTTP/Kafka behavior compiles and tests successfully.
  - Recommended follow-up: normalize or remove the stale exclude target during realtime-edge migration cleanup.

- `notification-service/src/main/java/com/example/notification/controller/NotificationController.java`
  - Package/class: `com.example.notification.notification.adapter.in.rest.NotificationController`
  - Why cleanup only: package/folder mismatch. The class compiles, is component-scanned, and tests pass.
  - Recommended follow-up: align folder layout with package naming after freeze.

- `notification-service/src/main/java/com/example/notification/controller/RoomMuteController.java`
  - Package/class: `com.example.notification.notification.adapter.in.rest.RoomMuteController`
  - Why cleanup only: package/folder mismatch and minor controller parsing/formatting polish only. Current controller behavior delegates to `RoomMuteSettingService` and is covered by tests.
  - Recommended follow-up: align folder layout and move trivial parsing polish if desired after freeze.

- `presence-service/src/main/java/com/example/presence/controller/PresenceController.java`
  - Package/class: `com.example.presence.presence.adapter.in.rest.PresenceController`
  - Why cleanup only: package/folder mismatch. The class compiles, is component-scanned, and the presence context/test suite passes.
  - Recommended follow-up: align folder layout with package naming after freeze.

- `friendship-service/src/main/java/com/example/friendship/controller/FriendController.java`
  - Package/class: `com.example.friendship.friendship.adapter.in.rest.FriendController`
  - Why cleanup only: package/folder mismatch. Controller methods remain thin delegates to command/query services.
  - Recommended follow-up: align folder layout with package naming after freeze.

- `friendship-service/src/main/java/com/example/friendship/controller/InternalFriendController.java`
  - Package/class: `com.example.friendship.friendship.adapter.in.rest.InternalFriendController`
  - Why cleanup only: package/folder mismatch. It is a narrow internal query adapter and compiles/tests successfully.
  - Recommended follow-up: align folder layout with package naming after freeze.

- `upload-service/src/main/java/com/example/upload/controller/UploadController.java`
  - Package/class: `com.example.upload.file.adapter.in.rest.UploadController`
  - Why cleanup only: package/folder mismatch. The controller maps request/response DTOs to application commands and delegates to `UploadSigningService`; upload tests pass.
  - Recommended follow-up: align folder layout with package naming after freeze.

- `friendship-service/src/main/java/com/example/friendship/application/FriendshipKafkaEventApplicationService.java`
  - Package/class: `com.example.friendship.application.FriendshipKafkaEventApplicationService`
  - Why cleanup only: low-risk transitional/dead code. Active friendship command behavior publishes events through `FriendshipEventProducer`, and notification-service consumes the request event topic. This does not block current startup or core service behavior.
  - Recommended follow-up: remove it or wire it intentionally when realtime-edge migration defines the final friendship realtime path.

- `friendship-service/src/main/java/com/example/friendship/kafka/FriendshipEventDedupeGuard.java`
  - Package/class: `com.example.friendship.kafka.FriendshipEventDedupeGuard`
  - Why cleanup only: low-risk unused dedupe helper in the current service path. It has no observed startup or compile/test impact.
  - Recommended follow-up: remove it or consolidate it with the final event dispatch path during realtime-edge cleanup.

- `chat-service/src/test/java/com/example/chat/modules/message/application/command/impl/MessageCommandServiceForwardTransactionalRollbackTest.java`
  - Package/class: `com.example.chat.modules.message.application.command.impl.MessageCommandServiceForwardTransactionalRollbackTest`
  - Why cleanup only: deprecated `@MockBean` warnings only; tests pass.
  - Recommended follow-up: migrate to the current Spring test mocking replacement in a test-maintenance pass.

- `upload-service/src/test/java/com/example/upload/controller/UploadControllerPurposeDeserializationTest.java`
  - Package/class: `com.example.upload.controller.UploadControllerPurposeDeserializationTest`
  - Why cleanup only: deprecated `@MockBean` warnings only; tests pass.
  - Recommended follow-up: migrate to the current Spring test mocking replacement in a test-maintenance pass.

- `notification-service/src/test/java/com/example/notification/service/impl/NotificationCommandServiceTest.java`
  - Package/class: `com.example.notification.service.impl.NotificationCommandServiceTest`
  - Why cleanup only: unchecked-operation warnings only; tests pass.
  - Recommended follow-up: tighten test generics/mocks in a cleanup pass.

## 6. Service Freeze Readiness
- Verdict for the service layer as a whole: YES.
- Reason: no Freeze Blockers were found. Compile validation passed, full service tests passed, the notification Kafka deserialization fix has a focused runtime-style proof, service route ownership is coherent enough to proceed, and no deployable service-to-service compile-time dependency remains.
- This verdict is based only on Freeze Blockers, not on Post-Refactor Cleanup items.

## 7. Final Recommendation
- YES: the service layer should now be treated as frozen enough for the next stage.
- Work should move to integration work, end-to-end service flow verification, and realtime-edge migration rather than more service-layer structural churn.
- Post-refactor cleanup items should be tracked separately and should not reopen broad refactor phases.
- Common-layer redesign remains out of scope.
