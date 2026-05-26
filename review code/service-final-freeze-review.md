# Service Layer Final Freeze Review

## 1. Scope Reviewed
- Exact service/app modules reviewed:
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
  - deployment/infrastructure, except minimal `gateway-service` routing/security/config needed to understand service boundaries
  - database schema outside service-owned code/resources directly used by the reviewed modules
  - UI/client behavior
- Exact boundary limits applied:
  - Reviewed startup/wiring health, compile/test health, service boundary correctness, adapter thinness, gateway route/API namespace ownership, and readiness to move toward integration/realtime-edge migration.
  - Did not review common-layer implementation internals as a redesign target.
  - Did not treat naming, package/folder mismatch, comments, formatting, or acknowledged transitional websocket routes as freeze blockers.

## 2. Build/Test Validation
- Commands run:
  - `.\gradlew.bat :auth-service:compileJava :user-service:compileJava :chat-service:compileJava :presence-service:compileJava :notification-service:compileJava :friendship-service:compileJava :upload-service:compileJava :gateway-service:compileJava --no-daemon`
  - `.\gradlew.bat :auth-service:test :user-service:test :chat-service:test :presence-service:test :notification-service:test :friendship-service:test :upload-service:test :gateway-service:test --no-daemon --rerun-tasks`
- Compile status:
  - `BUILD SUCCESSFUL`.
  - Compiled service modules: `auth-service`, `user-service`, `chat-service`, `presence-service`, `notification-service`, `friendship-service`, `upload-service`, `gateway-service`.
- Test status:
  - `BUILD SUCCESSFUL`.
  - Tested service modules:
    - `auth-service`: 13 suites, 47 tests, 0 failures, 0 errors, 0 skipped
    - `user-service`: 6 suites, 15 tests, 0 failures, 0 errors, 0 skipped
    - `chat-service`: 32 suites, 98 tests, 0 failures, 0 errors, 0 skipped
    - `presence-service`: 2 suites, 4 tests, 0 failures, 0 errors, 0 skipped
    - `notification-service`: 7 suites, 26 tests, 0 failures, 0 errors, 0 skipped
    - `friendship-service`: 4 suites, 6 tests, 0 failures, 0 errors, 0 skipped
    - `upload-service`: 3 suites, 5 tests, 0 failures, 0 errors, 0 skipped
    - `gateway-service`: 5 suites, 17 tests, 0 failures, 0 errors, 0 skipped
- Failures/errors/warnings:
  - No compile failures.
  - No test failures.
  - Remaining warnings were non-blocking: Gradle incubating problems-report notice, unchecked/unsafe operation notes, deprecated API notes, deprecated `@MockBean` warnings in tests, and OpenJDK class-sharing warnings.

## 3. What Improved
- `chat-service` runtime wiring is now complete enough to compile and run its service tests:
  - `chat-service/src/main/java/com/example/chat/modules/message/infrastructure/redis/ChatMessageEventPublisherAdapter.java` is a Spring component implementing `IMessageEventPublisher` and `IReactionEventPublisher`.
  - It publishes message/reaction realtime events to Redis and also publishes message/reaction envelopes to Kafka topics `chat.message.sent` and `chat.reaction.updated`.
- `chat-service` no longer drops the prior room membership realtime events:
  - `chat-service/src/main/java/com/example/chat/realtime/infrastructure/ChatRealtimeAdapter.java` forwards `MEMBER_JOINED`, `MEMBER_LEFT`, and `MEMBER_REMOVED` to `ChatRedisPublisher.publishMemberEvent(...)`.
  - `chat-service/src/test/java/com/example/chat/realtime/infrastructure/ChatRealtimeAdapterTest.java` covers membership forwarding and pin forwarding.
- `notification-service` inbound event classes now exist and delegate to application services:
  - `notification-service/src/main/java/com/example/notification/kafka/MessageCreatedEventConsumer.java` listens to `chat.message.sent`.
  - `notification-service/src/main/java/com/example/notification/kafka/ReactionEventConsumer.java` listens to `chat.reaction.updated`.
  - `notification-service/src/main/java/com/example/notification/kafka/FriendRequestEventConsumer.java` listens to `KafkaTopics.TOPIC_FRIENDSHIP_REQUEST_EVENTS`.
  - `notification-service/src/main/java/com/example/notification/kafka/AccountCreatedEventConsumer.java` listens to `account.created`.
  - `notification-service/src/test/java/com/example/notification/kafka/NotificationKafkaConsumersTest.java` covers consumer delegation behavior.
- Service boundary checks found no deployable service-to-service Gradle project dependency:
  - Reviewed service build files depend on `common:*` projects and external libraries, not on other deployable service projects.
  - Targeted import checks found no direct main-code imports from one reviewed deployable service package into another.
- Gateway route ownership is coherent enough for this stage:
  - HTTP routes map auth, user, chat, presence, friendship, notification, and upload API namespaces to their owning services.
  - Websocket routes are explicitly documented as temporary service-local forwarding routes pending realtime-edge cutover.

## 4. Freeze Blockers
### 1. Notification Kafka consumer production deserialization is still configured to reject the event envelopes/payloads it now listens for
- Exact service/module/file/package/class:
  - Service/module: `chatappBE/notification-service`
  - File: `notification-service/src/main/resources/application.yaml`
  - Classes affected:
    - `com.example.notification.kafka.MessageCreatedEventConsumer`
    - `com.example.notification.kafka.ReactionEventConsumer`
    - `com.example.notification.kafka.FriendRequestEventConsumer`
    - `com.example.notification.kafka.AccountCreatedEventConsumer`
- Why it is a blocker:
  - The notification inbound listener code is now present, but production Kafka consumer config still uses `org.springframework.kafka.support.serializer.JsonDeserializer` with `spring.json.trusted.packages: "com.example.common.kafka.*"`.
  - The listener methods consume `EventEnvelope<ChatMessagePayload>`, `EventEnvelope<ReactionPayload>`, `EventEnvelope<FriendRequestPayload>`, and `EventEnvelope<AccountCreatedPayload>`, whose envelope/payload packages are outside `com.example.common.kafka.*`.
  - This is a runtime integration/wiring bug: Kafka records can be rejected during deserialization before the new `@KafkaListener` methods delegate to `NotificationKafkaEventApplicationService`.
  - The green service tests do not prove this production deserialization path because the current consumer unit tests call listener methods directly rather than exercising the configured Kafka deserializer.
- Impact:
  - Chat message, reaction, friend-request, and account-created notification events can fail at the notification service inbound boundary despite the consumer classes compiling.
  - This prevents safe service freeze because the newly fixed critical notification behavior is still materially unproved and likely broken under production Kafka configuration.
- Narrow recommended next fix:
  - In `notification-service/src/main/resources/application.yaml`, align the consumer trusted packages/deserializer configuration with the actual envelope and payload packages consumed by the service, or align it with the same working service Kafka configuration pattern used by the other event-consuming services.
  - Add one focused runtime wiring test for notification Kafka deserialization using the production-style consumer configuration and one representative `EventEnvelope` payload.
  - Do not reopen broader notification, common-kafka, or cross-service event architecture refactors for this fix.

## 5. Post-Refactor Cleanup
### 1. Package declarations and physical file paths remain inconsistent in several service modules
- Exact service/module/file/package/class:
  - `auth-service/src/main/java/com/example/auth/controller/AuthController.java`, package `com.example.auth.auth.adapter.in.rest`
  - `user-service/src/main/java/com/example/user/controller/UserProfileController.java`, package `com.example.user.profile.adapter.in.rest`
  - `presence-service/src/main/java/com/example/presence/controller/PresenceController.java`, package `com.example.presence.presence.adapter.in.rest`
  - `notification-service/src/main/java/com/example/notification/controller/NotificationController.java`, package `com.example.notification.notification.adapter.in.rest`
  - `friendship-service/src/main/java/com/example/friendship/controller/FriendController.java`, package `com.example.friendship.friendship.adapter.in.rest`
  - `upload-service/src/main/java/com/example/upload/controller/UploadController.java`, package `com.example.upload.file.adapter.in.rest`
- Why it is cleanup only:
  - The reviewed modules compile and their tests pass.
  - Component scanning reaches these packages.
  - This is folder/package polish, not a current runtime or service-boundary blocker.
- Recommended follow-up:
  - Align physical paths and declared packages in a later cleanup pass after freeze-critical runtime wiring is settled.

### 2. Gateway websocket routes are explicitly transitional
- Exact service/module/file/package/class:
  - `gateway-service/src/main/resources/application.yaml`, routes `chat-service-ws`, `presence-service-ws`, `friendship-service-ws`, `notification-service-ws`
  - `gateway-service/src/main/java/com/example/gateway/config/SecurityConfig.java`, public `/ws/**` allowance
- Why it is cleanup only:
  - The routes are documented as temporary service-local websocket forwarding routes.
  - Gateway compile and integration tests pass.
  - Transitional websocket routes already acknowledged and not breaking startup are cleanup only under the review rule.
- Recommended follow-up:
  - Retarget or remove these routes when realtime-edge owns websocket ingress.

### 3. Notification Kafka default group id is misleading
- Exact service/module/file/package/class:
  - `notification-service/src/main/resources/application.yaml`, `spring.kafka.consumer.group-id: user-service`
  - Listener classes currently override with `groupId = "notification-service"`:
    - `com.example.notification.kafka.MessageCreatedEventConsumer`
    - `com.example.notification.kafka.ReactionEventConsumer`
    - `com.example.notification.kafka.FriendRequestEventConsumer`
    - `com.example.notification.kafka.AccountCreatedEventConsumer`
- Why it is cleanup only:
  - The active notification listeners set explicit `notification-service` group ids.
  - This does not currently break compile, startup, or the reviewed listener group ownership.
- Recommended follow-up:
  - Correct the default group id to `notification-service` to remove future confusion for any listener that omits an explicit group id.

### 4. Websocket auto-configuration exclusion strings are brittle but currently startup-safe
- Exact service/module/file/package/class:
  - `chat-service/src/main/java/com/example/chat/ChatServiceApplication.java`
  - `presence-service/src/main/java/com/example/presence/PresenceServiceApplication.java`
  - `notification-service/src/main/java/com/example/notification/NotificationServiceApplication.java`
  - `friendship-service/src/main/java/com/example/friendship/FriendshipServiceApplication.java`
- Why it is cleanup only:
  - The application context tests for the reviewed services pass.
  - The current exclusion spelling is a narrow startup compatibility measure, not a present startup failure.
- Recommended follow-up:
  - Normalize this into a clearer local opt-out or documented service bootstrap setting during a later cleanup pass.

## 6. Service Freeze Readiness
- Verdict for the service layer as a whole: NO
- Brief reason:
  - The verdict is based only on Freeze Blockers.
  - Compile and tests are green, service-to-service compile boundaries are clean, gateway route ownership is coherent enough, and the two prior code-level blockers are materially improved.
  - However, `notification-service` still has a production Kafka consumer deserialization configuration that can prevent the newly added inbound event adapters from receiving their envelope/payload types at runtime.

## 7. Final Recommendation
- NO: only the listed notification-service Kafka deserialization blocker should be fixed next.
- Do not open new broad refactor phases before that blocker is resolved.
- After that narrow fix and a focused runtime wiring proof, the service layer should be reviewed only for blocker closure and then treated as frozen enough for integration/realtime-edge migration rather than more structural churn.
- Common-layer redesign remains explicitly out of scope.
