# Phase 2 Run Note (Recheck Then Fix)

Date: 2026-05-21
Run scope: Phase 2 slice with mandatory recheck-before-fix
Method: verified each target issue in current code first, fixed only confirmed-open items, then validated via Gradle.

## Recheck Matrix

1. KAFKA-002 Durable dedupe (friendship + realtime-edge friendship consumers)
- Recheck result: STILL EXISTS.
- Evidence before fix:
  - friendship-service FriendshipEventDedupeGuard used in-memory ConcurrentHashMap + TTL sweep.
  - realtime-edge FriendshipEventDedupeGuard used in-memory ConcurrentHashMap + TTL sweep.
- Action in this run: FIXED.

2. KAFKA-003 Retry/backoff policy (notification-service)
- Recheck result: STILL EXISTS.
- Evidence before fix:
  - notification KafkaConsumerConfig used FixedBackOff(1000L, 3).
- Action in this run: FIXED.

3. REDIS-001 Room membership auth cache staleness window (realtime-edge)
- Recheck result: STILL EXISTS (TTL 60s and no targeted invalidation method).
- Action in this run: PARTIAL FIX.
  - Reduced TTL from 60s to 30s.
  - Added targeted invalidation method `invalidateRoomAccess(userId, roomId)`.

4. REALTIME-001 Blocking side effects in websocket path
- Recheck result: STILL EXISTS.
- Evidence:
  - RealtimeWebSocketHandler still calls presenceDomainClient methods inline in message handling path.
- Action in this run: NOT FIXED (deferred to next Phase 2 pass).

5. REALTIME-002 Synchronized send lock on websocket sessions
- Recheck result: STILL EXISTS.
- Evidence:
  - synchronized(webSocketSession) remains in chat/presence/notification/friendship delivery services.
- Action in this run: NOT FIXED (deferred to next Phase 2 pass).

## Changes Applied

1. Durable dedupe in friendship-service
- Switched dedupe store from in-memory map to Redis `setIfAbsent` with TTL.
- File:
  - chatappBE/friendship-service/src/main/java/com/chatweb/friendship/infrastructure/kafka/FriendshipEventDedupeGuard.java

2. Durable dedupe in realtime-edge friendship kafka ingress
- Switched dedupe store from in-memory map to Redis `setIfAbsent` with TTL.
- File:
  - chatappBE/realtime-edge-service/src/main/java/com/chatweb/realtime/adapter/in/kafka/FriendshipEventDedupeGuard.java

3. Notification retry policy upgraded to configurable exponential backoff
- Replaced FixedBackOff with ExponentialBackOff configuration.
- Added configurable retry properties (attempts, initial interval, max interval, multiplier).
- Files:
  - chatappBE/notification-service/src/main/java/com/chatweb/notification/infrastructure/kafka/KafkaConsumerConfig.java
  - chatappBE/notification-service/src/main/resources/application.yaml

4. Friendship Redis dependency added for new dedupe guard
- File:
  - chatappBE/friendship-service/build.gradle

5. Room access cache hardening (partial)
- Reduced room access cache TTL to 30s.
- Added targeted invalidation helper for user+room key.
- File:
  - chatappBE/realtime-edge-service/src/main/java/com/chatweb/realtime/subscription/ChannelSubscriptionManager.java

6. Test maintenance updates needed by Phase 2 changes
- Adjusted notification Kafka config test to assert handler presence robustly.
- Adjusted realtime-edge ChannelSubscriptionManagerTest stubbing to avoid strict Mockito unnecessary stubbing failure.
- Files:
  - chatappBE/notification-service/src/test/java/com/chatweb/notification/infrastructure/kafka/KafkaConsumerConfigTest.java
  - chatappBE/realtime-edge-service/src/test/java/com/chatweb/realtime/subscription/ChannelSubscriptionManagerTest.java

## Validation Performed

Command executed:
- notification-service test: KafkaConsumerConfigTest
- realtime-edge test: ChannelSubscriptionManagerTest
- compile checks: friendship-service, realtime-edge-service, notification-service

Result:
- BUILD SUCCESSFUL

## Fixed This Run (summary)

- FIXED: In-memory dedupe for friendship-service Kafka events.
- FIXED: In-memory dedupe for realtime-edge friendship Kafka events.
- FIXED: Notification fixed backoff policy replaced with configurable exponential backoff.
- PARTIAL: Room membership auth cache staleness mitigation (TTL reduced + invalidation hook added).

## Remaining for Next Phase 2 Pass

1. REALTIME-001 full async side-effect decoupling for websocket path.
2. REALTIME-002 replace synchronized send path with bounded queue/worker strategy.
3. REDIS-001 full event-driven room membership cache invalidation wiring (hook currently present, not yet wired to membership events).

## Phase 2 Pass 2 (Realtime Async/Queue Hardening)

Date: 2026-05-21
Run scope: REALTIME-001 + REALTIME-002 recheck then fix
Method: revalidated current code paths first, then changed only confirmed-open issues, then ran focused realtime-edge compile/tests.

### Recheck Matrix (Pass 2)

1. REALTIME-001 Blocking side effects in websocket path
- Recheck result: STILL EXISTS before this pass.
- Evidence before fix:
  - `RealtimeWebSocketHandler` performed presence lifecycle and presence domain calls inline in websocket handling path.
- Action in this pass: FIXED.

2. REALTIME-002 Synchronized send lock on websocket sessions
- Recheck result: STILL EXISTS before this pass.
- Evidence before fix:
  - `synchronized(webSocketSession)` was still used in presence/friendship/handoff delivery paths.
- Action in this pass: FIXED.

### Changes Applied (Pass 2)

1. Websocket handler side-effects moved off hot path
- Integrated `RealtimeSideEffectQueue` into `RealtimeWebSocketHandler`.
- Offloaded connect/disconnect heartbeat/join/leave/typing side effects to queued execution.
- Added outbound queue cleanup on websocket close (`clearSession`).
- File:
  - chatappBE/realtime-edge-service/src/main/java/com/chatweb/realtime/adapter/in/websocket/RealtimeWebSocketHandler.java

2. Non-blocking outbound delivery queue applied to remaining delivery paths
- Replaced synchronized direct websocket sends with `WebSocketOutboundDeliveryQueue.enqueue(...)` in:
  - Presence delivery
  - Friendship delivery
  - Cross-instance handoff listener delivery
- Files:
  - chatappBE/realtime-edge-service/src/main/java/com/chatweb/realtime/delivery/PresenceRealtimeDeliveryService.java
  - chatappBE/realtime-edge-service/src/main/java/com/chatweb/realtime/delivery/FriendshipRealtimeDeliveryService.java
  - chatappBE/realtime-edge-service/src/main/java/com/chatweb/realtime/dispatch/EdgeDeliveryHandoffListener.java

3. Test updates for constructor changes and async behavior
- Updated websocket handler tests to inject queue dependencies.
- Updated notification delivery tests to use outbound queue and timeout-based verification for async dispatch.
- Files:
  - chatappBE/realtime-edge-service/src/test/java/com/chatweb/realtime/adapter/in/websocket/RealtimeWebSocketHandlerChatRoutingTest.java
  - chatappBE/realtime-edge-service/src/test/java/com/chatweb/realtime/adapter/in/websocket/RealtimeWebSocketHandlerCommandFailureTest.java
  - chatappBE/realtime-edge-service/src/test/java/com/chatweb/realtime/delivery/NotificationRealtimeDeliveryServiceTest.java
  - chatappBE/realtime-edge-service/src/test/java/com/chatweb/realtime/delivery/NotificationRealtimeLoadValidationTest.java

### Validation Performed (Pass 2)

Command executed:
- `:realtime-edge-service:compileJava`
- `:realtime-edge-service:test --tests "com.chatweb.realtime.adapter.in.websocket.RealtimeWebSocketHandlerChatRoutingTest"`
- `:realtime-edge-service:test --tests "com.chatweb.realtime.adapter.in.websocket.RealtimeWebSocketHandlerCommandFailureTest"`
- `:realtime-edge-service:test --tests "com.chatweb.realtime.delivery.NotificationRealtimeDeliveryServiceTest"`
- `:realtime-edge-service:test --tests "com.chatweb.realtime.delivery.NotificationRealtimeLoadValidationTest"`

Result:
- BUILD SUCCESSFUL

### Fixed This Pass (summary)

- FIXED: REALTIME-001 websocket side effects are queued off the hot path.
- FIXED: REALTIME-002 synchronized websocket send locks removed from remaining delivery paths.

## Phase 2 Pass 3 (REDIS-001 Event-Driven Invalidation Wiring)

Date: 2026-05-21
Run scope: REDIS-001 recheck then fix
Method: revalidated whether invalidation wiring was still missing, implemented only after confirmed-open, then ran focused realtime-edge compile/tests.

### Recheck Matrix (Pass 3)

1. REDIS-001 Room membership auth cache invalidation wiring
- Recheck result: STILL EXISTS before this pass.
- Evidence before fix:
  - `ChannelSubscriptionManager.invalidateRoomAccess(...)` existed but was not called from realtime event ingress on room membership change events.
- Action in this pass: FIXED.

### Changes Applied (Pass 3)

1. Wired targeted invalidation from Redis chat room membership events
- Updated realtime-edge Redis ingress listener to invalidate room access cache when membership events are received:
  - `chat.room.member.joined`
  - `chat.room.member.left`
  - `chat.room.member.removed`
- Invalidation keying uses `(userId, roomId)` for targeted delete only.
- File:
  - chatappBE/realtime-edge-service/src/main/java/com/chatweb/realtime/adapter/in/redis/RedisEventListener.java

2. Added/updated tests for event-driven invalidation behavior
- Updated notification routing test for envelope format and new listener dependency.
- Added membership invalidation tests:
  - membership event triggers `invalidateRoomAccess(userId, roomId)`
  - non-membership chat event does not invalidate
- Files:
  - chatappBE/realtime-edge-service/src/test/java/com/chatweb/realtime/adapter/in/redis/RedisEventListenerNotificationTest.java
  - chatappBE/realtime-edge-service/src/test/java/com/chatweb/realtime/adapter/in/redis/RedisEventListenerRoomMembershipInvalidationTest.java

### Validation Performed (Pass 3)

Command executed:
- `:realtime-edge-service:compileJava`
- `:realtime-edge-service:test --tests "com.chatweb.realtime.adapter.in.redis.RedisEventListenerNotificationTest"`
- `:realtime-edge-service:test --tests "com.chatweb.realtime.adapter.in.redis.RedisEventListenerRoomMembershipInvalidationTest"`
- `:realtime-edge-service:test --tests "com.chatweb.realtime.subscription.ChannelSubscriptionManagerTest"`

Result:
- BUILD SUCCESSFUL

### Fixed This Pass (summary)

- FIXED: REDIS-001 full event-driven room membership cache invalidation wiring in realtime-edge ingress.

## Phase 2 Closure Status

- FIXED: KAFKA-002
- FIXED: KAFKA-003
- FIXED: REALTIME-001
- FIXED: REALTIME-002
- FIXED: REDIS-001

Phase 2 items tracked in this run note are fully fixed.
