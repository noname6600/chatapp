# Notification-Service Compile Fix

## Scope
- Only notification-service source was targeted.
- Goal was to make notification-service compile cleanly while preserving existing architecture and prior blocker fixes.

## Compile Verification
- Command run:
  - ./gradlew.bat :notification-service:compileJava --no-daemon
- Result:
  - BUILD SUCCESSFUL

## Exact Files Changed
- chatappBE/notification-service/src/main/java/com/example/notification/application/NotificationKafkaEventApplicationService.java
- chatappBE/notification-service/src/main/java/com/example/notification/application/NotificationFriendRequestEventApplicationService.java
- chatappBE/notification-service/src/main/java/com/example/notification/application/NotificationMessageEventApplicationService.java
- chatappBE/notification-service/src/main/java/com/example/notification/application/NotificationReactionEventApplicationService.java
- chatappBE/notification-service/src/main/java/com/example/notification/kafka/FriendRequestEventConsumer.java
- chatappBE/notification-service/src/main/java/com/example/notification/kafka/NotificationEventDedupeGuard.java
- chatappBE/notification-service/src/main/java/com/example/notification/realtime/infrastructure/NotificationRedisRealtimeAdapter.java

## Exact Imports Fixed

### Invalid package pattern removed
- com.example.notification.notification.application.*

### Replaced with existing classes
- In NotificationKafkaEventApplicationService:
  - com.example.notification.notification.application.NotificationDomainService
  - changed to com.example.notification.service.impl.NotificationDomainService

- In NotificationFriendRequestEventApplicationService:
  - com.example.notification.notification.application.NotificationCommandService
  - changed to com.example.notification.service.impl.NotificationCommandService

- In NotificationMessageEventApplicationService:
  - com.example.notification.notification.application.NotificationCommandService
  - changed to com.example.notification.service.impl.NotificationCommandService
  - com.example.notification.notification.application.RoomMuteSettingService
  - changed to com.example.notification.service.impl.RoomMuteSettingService

- In NotificationReactionEventApplicationService:
  - com.example.notification.notification.application.NotificationCommandService
  - changed to com.example.notification.service.impl.NotificationCommandService

## Exact Legacy References Removed

### Legacy friendship event contract removed from notification consumer
- Removed import:
  - com.example.common.integration.friendship.FriendRequestEvent

- Removed legacy listener type:
  - EventEnvelope<FriendRequestEvent>
  - replaced with EventEnvelope<FriendRequestPayload>

- Removed payload enum branching on legacy nested type:
  - FriendRequestEvent.Type.SENT
  - FriendRequestEvent.Type.ACCEPTED
  - FriendRequestEvent.Type.DECLINED
  - FriendRequestEvent.Type.CANCELLED

- Replaced with canonical model handling:
  - Event type from envelope metadata: envelope.metadata().getEventType()
  - Canonical payload: FriendRequestPayload
  - Delegation to NotificationFriendRequestEventApplicationService.handle(eventType, payload)

## Additional Compile-Only Fixes Needed Inside Notification-Service

### Dedupe guard eventId type alignment
- Notification metadata eventId is String.
- NotificationEventDedupeGuard was expecting UUID.
- Updated:
  - Map<UUID, Instant> to Map<String, Instant>
  - isDuplicate(UUID eventId) to isDuplicate(String eventId)

### Realtime port interface conformance
- NotificationRealtimePort requires:
  - publishUserEvent(UUID userId, String eventType, Object payload, RealtimeFlowId flowId)
- NotificationRedisRealtimeAdapter was missing this override.
- Added override and delegated to existing publishUserEvent(userId, eventType, payload).

## Missing Symbols Resolution Outcome
- NotificationCommandService:
  - Found at com.example.notification.service.impl.NotificationCommandService
  - All invalid imports updated to this class.

- NotificationDomainService:
  - Found at com.example.notification.service.impl.NotificationDomainService
  - Invalid import updated.

- RoomMuteSettingService:
  - Found at com.example.notification.service.impl.RoomMuteSettingService
  - Invalid import updated.

## Canonical Kafka Consumer Alignment Outcome
- FriendRequestEventConsumer now consumes canonical EventEnvelope<FriendRequestPayload> and routes by metadata eventType.
- This aligns with current envelope/event-type contract and removes legacy FriendRequestEvent dependency.

## Remaining Errors
- None for notification-service compile target.
- :notification-service:compileJava passes cleanly.
