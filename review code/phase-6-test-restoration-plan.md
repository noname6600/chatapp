# Phase 6 - Incremental Test Restoration Plan

Date: 2026-05-14
Source of truth:
- review code/service-fix-plan.md
- completed phase notes (Phase 1A..5 in review code)

## Scope and method
- Service-first only: chat-service, friendship-service, notification-service.
- No common/module redesign.
- Restored in small batches with focused gates.
- Priority order used: authorization and contract/delegation tests first.

## A) Candidate tests restored now (completed)

### Batch 1 - friendship-service (restored)
Restored test:
- com/example/friendship/service/impl/FriendCommandServiceTest.java

Why now:
- Critical security/correctness fixes for friendship command path are complete (internal auth + after-commit publication + ID-first producer update already landed in earlier phases).
- Test was stale only because it referenced removed event API symbols; updated to current producer contract (`publish(FriendshipEventType, Friendship)`) without architecture changes.

Exact verification command:
- ./gradlew :friendship-service:test --tests "com.example.friendship.service.impl.FriendCommandServiceTest" --tests "com.example.friendship.service.impl.FriendCommandServiceAfterCommitPublicationTest" --tests "com.example.friendship.controller.InternalFriendControllerTest"

Result:
- BUILD SUCCESSFUL

### Batch 2 - notification-service (restored)
Restored tests:
- com/example/notification/application/NotificationKafkaEventApplicationServiceTest.java
- com/example/notification/kafka/NotificationKafkaConsumersTest.java

Why now:
- Phase 2 notification after-commit side-effect hardening is complete.
- Both tests were stale due signature/import drift (string eventId delegation and package typo), not due unresolved architecture migration.
- Updated tests to current API behavior only.

Exact verification command:
- ./gradlew :notification-service:test --tests "com.example.notification.application.NotificationKafkaEventApplicationServiceTest" --tests "com.example.notification.kafka.NotificationKafkaConsumersTest" --tests "com.example.notification.service.impl.NotificationCommandServiceTest" --tests "com.example.notification.controller.NotificationControllerTest"

Result:
- BUILD SUCCESSFUL

## B) Tests that must stay quarantined (for now)

### chat-service (keep quarantined)
1. com/example/chat/realtime/contract/RealtimeContractBaselineTest.java
Reason:
- References removed/reworked common contract types (for example `RealtimeContractVersions`).
- Requires contract-test rewrite aligned to current common module APIs.

2. com/example/chat/realtime/contract/RealtimeContractValidatorTest.java
Reason:
- References removed/reworked common validator interfaces (`RealtimeContractValidator`, legacy redis/kafka contract types).
- Not a safe unquarantine without rewriting assertions to current contract layer.

3. com/example/chat/realtime/contract/RealtimeMessagingAlignmentTest.java
Reason:
- Uses legacy common kafka event publisher APIs that are no longer present.
- Needs migration to current `EventEnvelope` + producer contracts.

4. com/example/chat/realtime/infrastructure/ChatRealtimeAdapterTest.java
Reason:
- Constructor and behavior drift (`ChatRealtimeAdapter` now depends on room/user broadcasters + redis publisher and has flow-policy paths).
- Old test wiring no longer matches current adapter semantics.

5. com/example/chat/realtime/websocket/handler/ChatWebSocketHandlerTest.java
Reason:
- Handler constructor and responsibilities changed (lifecycle adapter, command dispatcher, membership guard).
- Requires rewrite to current authz-enforced JOIN/dispatch structure.

### friendship-service (keep quarantined)
1. com/example/friendship/kafka/FriendshipRealtimeConsumerTest.java
Reason:
- Targets removed/renamed consumer classes and old kafka event wrappers not present in current code.
- Invalid under current intentional transition (edge/event path changes), must be replaced with current producer/forwarding contract tests.

### notification-service (keep quarantined)
1. com/example/notification/contract/NotificationRealtimeContractBaselineTest.java
Reason:
- Depends on legacy contract constants/types no longer present in current common contract layer.

2. com/example/notification/kafka/FriendRequestEventConsumerTest.java
Reason:
- Uses deprecated `FriendRequestKafkaEvent` wrapper path; current consumer uses `EventEnvelope<FriendRequestPayload>`.

3. com/example/notification/kafka/MessageCreatedEventConsumerTest.java
Reason:
- Uses deprecated `ChatMessageSentEvent` wrapper path; current consumer uses `EventEnvelope<ChatMessagePayload>`.

4. com/example/notification/kafka/ReactionEventConsumerTest.java
Reason:
- Uses deprecated `ChatReactionUpdatedEvent` wrapper path; current consumer uses `EventEnvelope<ReactionPayload>`.

## C) Next incremental restoration batches (planned)

### Batch 3 - notification consumer contract rewrite (small, safe)
Target tests to rewrite and restore:
- FriendRequestEventConsumerTest
- MessageCreatedEventConsumerTest
- ReactionEventConsumerTest

Exact verification command after rewrite:
- ./gradlew :notification-service:test --tests "com.example.notification.kafka.FriendRequestEventConsumerTest" --tests "com.example.notification.kafka.MessageCreatedEventConsumerTest" --tests "com.example.notification.kafka.ReactionEventConsumerTest" --tests "com.example.notification.service.impl.NotificationCommandServiceTest"

### Batch 4 - chat websocket authz regression tests first
Target test to rewrite and restore first:
- ChatWebSocketHandlerTest

Exact verification command after rewrite:
- ./gradlew :chat-service:test --tests "com.example.chat.realtime.websocket.handler.ChatWebSocketHandlerTest" --tests "com.example.chat.modules.room.controller.RoomControllerAuthorizationTest" --tests "com.example.chat.modules.message.application.query.MessageQueryServiceTest"

### Batch 5 - chat realtime adapter + contract tests
Target tests to rewrite and restore:
- ChatRealtimeAdapterTest
- RealtimeContractBaselineTest
- RealtimeContractValidatorTest
- RealtimeMessagingAlignmentTest

Exact verification command after rewrite:
- ./gradlew :chat-service:test --tests "com.example.chat.realtime.infrastructure.ChatRealtimeAdapterTest" --tests "com.example.chat.realtime.contract.RealtimeContractBaselineTest" --tests "com.example.chat.realtime.contract.RealtimeContractValidatorTest" --tests "com.example.chat.realtime.contract.RealtimeMessagingAlignmentTest"

## D) Summary
- Restored now in this phase: 3 quarantined tests (1 friendship, 2 notification).
- Kept quarantined: tests that still bind to intentionally removed legacy wrappers/contracts or heavily changed constructors/flows.
- Regression risk reduced incrementally without broad architecture changes.
