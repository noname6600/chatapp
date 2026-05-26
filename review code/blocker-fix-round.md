# Blocker Fix Round (Strict Order)

## Scope Followed
- Fixed in strict order: BLOCKER-0 -> BLOCKER-1 -> BLOCKER-2 -> BLOCKER-3
- Only hard blocker fixes and minimal compile-unblock compatibility edits were applied.
- No architecture refactor was introduced.

## 1) BLOCKER-0: common-kafka compile failure

### Files changed
- `chatappBE/common/common-kafka/src/main/java/com/example/common/kafka/producer/DefaultKafkaEventProducer.java`
- `chatappBE/common/common-kafka/src/main/java/com/example/common/kafka/serialization/EventEnvelopeKafkaSerializer.java`
- `chatappBE/common/common-kafka/src/main/java/com/example/common/kafka/serialization/EventEnvelopeKafkaDeserializer.java`

### Exact compile errors resolved
- `SharedEventCatalog.PAYLOAD_BEARING_EVENT_TYPES` not found
- `SharedEventCatalog.validatePayloadContract(...)` not found
- `KafkaEventObserver.logProduceSuccess(...)` not found
- `KafkaEventObserver.logProduceError(...)` not found
- `SharedEventCatalog.isKnownEventType(...)` not found (serializer/deserializer)

### What was changed (minimal API alignment)
- Replaced observer calls with existing API:
  - `logProduceSuccess` -> `logPublish`
  - `logProduceError` -> `logError`
- Removed references to non-existent `PAYLOAD_BEARING_EVENT_TYPES`.
- Removed references to non-existent `validatePayloadContract(...)`.
- Deserializer now determines known event types via existing APIs:
  - payload-less: `SharedEventCatalog.PAYLOAD_LESS_EVENT_TYPES.contains(eventType)`
  - payload-bearing: `registry.contains(eventType)`

### Narrow verification run
- Command: `./gradlew.bat :common:common-kafka:compileJava --no-daemon`
- Result: `BUILD SUCCESSFUL`

### Status
- **BLOCKER-0 fully cleared: YES**
- **Can next blocker be addressed: YES**

---

## 2) BLOCKER-1: friendship-service compile failure

### Files changed
- Deleted `chatappBE/friendship-service/src/main/java/com/example/friendship/kafka/FriendshipEventConsumer.java`
- Deleted `chatappBE/friendship-service/src/main/java/com/example/friendship/kafka/FriendshipRequestEventConsumer.java`
- Updated `chatappBE/friendship-service/src/main/java/com/example/friendship/kafka/FriendshipEventProducer.java`
- Updated `chatappBE/friendship-service/src/main/java/com/example/friendship/service/impl/FriendCommandService.java`
- Updated `chatappBE/friendship-service/src/main/java/com/example/friendship/websocket/FriendshipWebSocketPublisher.java`

### Exact compile errors resolved
- Missing symbol: `KafkaTopics.FRIENDSHIP_EVENTS`
- Missing class import: `com.example.common.integration.kafka.event.FriendshipEvent`
- Missing class import: `com.example.common.integration.kafka.event.FriendRequestKafkaEvent`
- Missing class import/type: `com.example.common.integration.friendship.FriendRequestEvent`
- Missing symbol: `KafkaTopics.FRIENDSHIP_REQUEST_EVENTS`

### What was changed (minimal unblock)
- Removed broken legacy consumers that referenced removed/legacy event models.
- Removed `FriendRequestEvent`-based producer and websocket branches that no longer match current shared-event model.
- Kept existing `FriendshipPayload` + `FriendshipEventType` path intact.

### Narrow verification run
- Command: `./gradlew.bat :friendship-service:compileJava --no-daemon`
- Result: `BUILD SUCCESSFUL`

### Status
- **BLOCKER-1 fully cleared: YES**
- **Can next blocker be addressed: YES**

---

## 3) BLOCKER-2: notification trusted.packages misconfiguration

### Files changed
- Updated `chatappBE/notification-service/src/main/resources/application.yaml`

### Exact blocker fix
- Changed:
  - `spring.json.trusted.packages: "com.example.common.kafka.*"`
- To:
  - `spring.json.trusted.packages: "*"`

This removes the runtime Kafka deserialization trust mismatch for `EventEnvelope` (`com.example.common.event.*`) and shared payload packages.

### Narrow verification run
- Command: `./gradlew.bat :notification-service:compileJava --no-daemon`
- Result: compile did not complete due to **unrelated pre-existing notification module compile errors**, not due to YAML change.

### Remaining compile errors seen during verification
- Wrong import package path in notification application services:
  - `com.example.notification.notification.application.*` does not exist
- Missing symbols derived from that wrong import package:
  - `NotificationCommandService`
  - `NotificationDomainService`
  - `RoomMuteSettingService`
- Legacy removed type still referenced in notification:
  - `com.example.common.integration.friendship.FriendRequestEvent`
  - in `notification-service/kafka/FriendRequestEventConsumer.java`

### Status
- **BLOCKER-2 config issue fixed: YES**
- **Module compile fully clean: NO (blocked by unrelated pre-existing errors above)**
- **Can next blocker be addressed: YES**

---

## 4) BLOCKER-3: notification dual consumers on chat.message.sent

### Files changed
- Deleted `chatappBE/notification-service/src/main/java/com/example/notification/kafka/ChatMessageEventConsumer.java`

### Exact blocker fix
- Removed the no-op duplicate listener that competed with `MessageCreatedEventConsumer` on:
  - topic: `chat.message.sent`
  - group: `notification-service`

This eliminates partition-race silent drop risk caused by duplicate consumers in same group on single-partition auto-created topics.

### Narrow verification run
- Command: `./gradlew.bat :notification-service:compileJava --no-daemon`
- Result: still fails with the same unrelated pre-existing notification compile errors listed in BLOCKER-2 section.

### Status
- **BLOCKER-3 fix applied: YES**
- **Verification blocked by unrelated notification compile failures: YES**

---

## Additional minimal compatibility edits required to keep blocker checks runnable

These surfaced while running required narrow compile checks and were fixed minimally (no architecture changes):

- Updated `chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/frame/RealtimeEventFrame.java`
- Updated `chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/codec/JsonRealtimeFrameCodec.java`
  - Removed references to non-existent `SharedEventCatalog.isKnownEventType(...)` and `validatePayloadContract(...)`

- Updated `chatappBE/common/common-redis/src/main/java/com/example/common/redis/subscriber/RedisPubSubSubscriberAdapter.java`
  - Fixed dispatcher call signature:
  - `dispatcher.dispatch(channel, envelope)` -> `dispatcher.dispatch(envelope)`

These were necessary to allow downstream module compile checks to execute.

---

## Final blocker state

- BLOCKER-0: **CLEARED**
- BLOCKER-1: **CLEARED**
- BLOCKER-2: **FIX APPLIED** (runtime trust-package blocker addressed), but notification module currently has unrelated compile failures.
- BLOCKER-3: **FIX APPLIED** (duplicate consumer removed), verification currently blocked by unrelated notification compile failures.

## Remaining compile errors (current)

Notification-service still fails compile due to unrelated code issues outside the four hard blockers:
- invalid imports under `com.example.notification.notification.application.*`
- missing classes `NotificationCommandService`, `NotificationDomainService`, `RoomMuteSettingService`
- legacy `FriendRequestEvent` references in notification Kafka consumer

These should be resolved next before rerunning full Phase 5 verification.
