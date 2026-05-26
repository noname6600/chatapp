# Notification Service First Refactor Result

## 1. Scope
- Only notification-service was changed: chatappBE/notification-service/**
- No edits were made to other services, common modules, gateway, frontend, or infrastructure/deployment

## 2. Changes Applied

### Route ownership cleanup
- Room mute/settings endpoints were restricted to notification-owned namespace only.
- Updated controller mapping from dual ownership:
  - removed /api/v1/rooms
  - kept /api/v1/notifications/rooms/**

Result:
- Notification service no longer claims chat room namespace at controller level.

### Kafka consumer thinning
- Converted Kafka consumers into thin transport adapters that only:
  - receive event
  - dedupe guard check
  - delegate to application-layer service

Implemented application services:
- NotificationMessageEventApplicationService
- NotificationReactionEventApplicationService
- NotificationFriendRequestEventApplicationService

Updated consumers:
- MessageCreatedEventConsumer now delegates to NotificationMessageEventApplicationService
- ReactionEventConsumer now delegates to NotificationReactionEventApplicationService
- FriendRequestEventConsumer now delegates to NotificationFriendRequestEventApplicationService

Behavior retained:
- Existing business classification/orchestration logic was moved from consumers into application services without broad redesign.

### WebSocket thinning (outbound path)
- Introduced dedicated outbound event mapper:
  - NotificationRealtimeEventMapper
- Updated NotificationWebSocketPublisher to delegate frame mapping to mapper and keep publisher focused on outbound transport dispatch.

Handler status:
- NotificationWebSocketHandler remains lifecycle-focused (connect/disconnect and metrics), with no added business logic.

### Dead/redundant inbound path cleanup
- Removed redundant ignore-only chat message consumer path:
  - deleted ChatMessageEventConsumer
- Kept one clear inbound path for chat message events via MessageCreatedEventConsumer.

## 3. Files Changed

### Main
- chatappBE/notification-service/src/main/java/com/example/notification/controller/RoomMuteController.java
- chatappBE/notification-service/src/main/java/com/example/notification/kafka/MessageCreatedEventConsumer.java
- chatappBE/notification-service/src/main/java/com/example/notification/kafka/ReactionEventConsumer.java
- chatappBE/notification-service/src/main/java/com/example/notification/kafka/FriendRequestEventConsumer.java
- chatappBE/notification-service/src/main/java/com/example/notification/kafka/ChatMessageEventConsumer.java (deleted)
- chatappBE/notification-service/src/main/java/com/example/notification/application/NotificationMessageEventApplicationService.java (new)
- chatappBE/notification-service/src/main/java/com/example/notification/application/NotificationReactionEventApplicationService.java (new)
- chatappBE/notification-service/src/main/java/com/example/notification/application/NotificationFriendRequestEventApplicationService.java (new)
- chatappBE/notification-service/src/main/java/com/example/notification/websocket/NotificationRealtimeEventMapper.java (new)
- chatappBE/notification-service/src/main/java/com/example/notification/websocket/NotificationWebSocketPublisher.java

### Tests
- chatappBE/notification-service/src/test/java/com/example/notification/kafka/MessageCreatedEventConsumerTest.java
- chatappBE/notification-service/src/test/java/com/example/notification/kafka/ReactionEventConsumerTest.java
- chatappBE/notification-service/src/test/java/com/example/notification/kafka/FriendRequestEventConsumerTest.java
- chatappBE/notification-service/src/test/java/com/example/notification/contract/NotificationRealtimeContractBaselineTest.java
- chatappBE/notification-service/src/test/java/com/example/notification/websocket/NotificationWebSocketPublisherTest.java

## 4. Validation

Commands run from chatappBE:

1) .\gradlew.bat :notification-service:compileJava --no-daemon
- Result: FAILED
- Primary cause in this workspace: pre-existing compile drift between notification-service and current common contracts (missing/renamed common Kafka/WebSocket/realtime types and topic constants), not introduced by this pass.
- Example failures include unresolved imports/types under:
  - com.example.common.integration.kafka.event.*
  - com.example.common.websocket.protocol.*
  - com.example.common.realtime.policy.*
  - KafkaTopics constants used by notification-service

2) .\gradlew.bat :notification-service:test --no-daemon --rerun-tasks
- Result: FAILED
- Reason: compile phase fails before tests execute.

### Limitations
- Because notification-service does not compile against the current common foundation in this workspace, end-to-end behavioral test execution could not be completed.
- This pass still implemented the requested narrow service-layer refactor boundaries inside notification-service only.

## 5. Remaining Next Steps
- Keep this limited to notification-service:
  1. Align notification-service imports/topic constants to currently available common APIs so module compiles.
  2. Add/restore focused unit tests for the new application-layer handlers (message/reaction/friend-request) to preserve prior business-logic coverage after consumer thinning.
  3. Add a small controller test for route ownership to assert only /api/v1/notifications/rooms/** mappings are present.
  4. Keep websocket adapter thin by maintaining mapper/publisher separation and avoiding business branching inside websocket classes.
