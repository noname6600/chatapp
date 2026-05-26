# Service Final Two Blockers Fix Result

## 1. Scope

Only `chat-service` and `notification-service` were modified in this pass.

---

## 2. Blockers Fixed

### Blocker 1 — Notification event adapters not wired end to end for non-account events

**Issue:**
`NotificationKafkaEventApplicationService` contained application handlers for chat message sent, reaction updated, and friendship request events. The three inbound Kafka consumer classes existed (`MessageCreatedEventConsumer`, `ReactionEventConsumer`, `FriendRequestEventConsumer`) but were broken at compile time because they referenced:
- `KafkaTopics.CHAT_MESSAGE_SENT` — constant does not exist in `KafkaTopics`
- `KafkaTopics.CHAT_REACTION_UPDATED` — constant does not exist in `KafkaTopics`
- `KafkaTopics.FRIENDSHIP_REQUEST_EVENTS` — wrong constant name (correct is `TOPIC_FRIENDSHIP_REQUEST_EVENTS`)
- `ChatMessageSentEvent`, `ChatReactionUpdatedEvent`, `FriendRequestKafkaEvent` — classes do not exist in any common module

Additionally, `chat-service`'s `ChatMessageEventPublisherAdapter` only published message and reaction events via Redis (for real-time WebSocket fanout). There was no Kafka publishing for these events, meaning the notification-service Kafka consumers had nothing to consume even if they compiled.

**Fix applied:**

1. **`MessageCreatedEventConsumer`** — rewritten to:
   - Use topic `"chat.message.sent"` (matching `ChatEventType.MESSAGE_SENT.value()`)
   - Accept `EventEnvelope<ChatMessagePayload>` (correct shared contract type)
   - Extract `eventId` from `event.metadata().getEventId()` with `UUID.fromString()`

2. **`ReactionEventConsumer`** — rewritten to:
   - Use topic `"chat.reaction.updated"` (matching `ChatEventType.REACTION_UPDATED.value()`)
   - Accept `EventEnvelope<ReactionPayload>`

3. **`FriendRequestEventConsumer`** — rewritten to:
   - Use `KafkaTopics.TOPIC_FRIENDSHIP_REQUEST_EVENTS` (correct existing constant)
   - Accept `EventEnvelope<FriendRequestPayload>`
   - Extract `eventType` from `event.metadata().getEventType()`

4. **`ChatMessageEventPublisherAdapter`** — updated to dual-publish:
   - `publishMessageCreated()` now sends to Redis (real-time fanout) **and** publishes `EventEnvelope<ChatMessagePayload>` to Kafka topic `"chat.message.sent"` for cross-service consumers
   - `publishReactionUpdated()` now sends to Redis **and** publishes `EventEnvelope<ReactionPayload>` to Kafka topic `"chat.reaction.updated"` for cross-service consumers
   - Injected `KafkaEventProducer` (already a dependency of `chat-service` via `common-kafka`)

**Files changed:**
- `chatappBE/notification-service/src/main/java/com/example/notification/kafka/MessageCreatedEventConsumer.java`
- `chatappBE/notification-service/src/main/java/com/example/notification/kafka/ReactionEventConsumer.java`
- `chatappBE/notification-service/src/main/java/com/example/notification/kafka/FriendRequestEventConsumer.java`
- `chatappBE/chat-service/src/main/java/com/example/chat/modules/message/infrastructure/redis/ChatMessageEventPublisherAdapter.java`

**Status: Fully resolved.** All four consumers now compile against real types, delegate to the existing application service methods, and have a real upstream Kafka publisher for message/reaction events.

---

### Blocker 2 — Chat realtime adapter silently drops room membership events

**Issue:**
`RoomMembershipApplicationService` emits `MEMBER_JOINED`, `MEMBER_LEFT`, and `MEMBER_REMOVED` through `ChatRealtimePort`. `RoomModerationApplicationService` emits `MEMBER_REMOVED`. `ChatRealtimeAdapter.publishRoomEvent()` only handled pin/unpin events and explicitly logged a skip for all other room events, meaning membership transitions were never forwarded to any outbound path.

**Fix applied:**

1. **`ChatRedisPublisher`** — added `publishMemberEvent(UUID roomId, String eventType)`:
   - Publishes to the room Redis channel with `null` payload
   - `null` payload is required because `SharedEventCatalog` declares `MEMBER_JOINED`, `MEMBER_LEFT`, `MEMBER_REMOVED` as payload-less event types; the `DefaultRedisEventPublisher` enforces this contract and will reject non-null payloads for these types

2. **`ChatRealtimeAdapter.publishRoomEvent()`** — extended to handle membership events:
   - Added `isMemberEvent(String eventType)` helper checking all three membership event type values
   - When `eventType` matches a membership event, delegates to `chatRedisPublisher.publishMemberEvent(roomId, eventType)`
   - Replaced the generic skip log with a targeted "unsupported room event" log for genuinely unhandled types

**Files changed:**
- `chatappBE/chat-service/src/main/java/com/example/chat/modules/message/infrastructure/redis/ChatRedisPublisher.java`
- `chatappBE/chat-service/src/main/java/com/example/chat/realtime/infrastructure/ChatRealtimeAdapter.java`

**Status: Fully resolved.** Membership events emitted by application services are now forwarded through the outbound publisher path rather than silently dropped.

---

## 3. Runtime Wiring

### Notification inbound event paths now wired

| Event | Transport | Topic | Consumer | Handler |
|-------|-----------|-------|----------|---------|
| chat message sent | Kafka | `chat.message.sent` | `MessageCreatedEventConsumer` | `NotificationKafkaEventApplicationService.handleChatMessageSentEvent()` |
| reaction updated | Kafka | `chat.reaction.updated` | `ReactionEventConsumer` | `NotificationKafkaEventApplicationService.handleReactionUpdatedEvent()` |
| friend request | Kafka | `friendship.request.events` | `FriendRequestEventConsumer` | `NotificationKafkaEventApplicationService.handleFriendRequestEvent()` |
| account created | Kafka | `account.created` | `AccountCreatedEventConsumer` | `NotificationKafkaEventApplicationService.handleAccountCreatedEvent()` |

All four notification event paths now have runtime adapters. Chat-service publishes message and reaction events to both Redis (real-time fanout) and Kafka (cross-service). Friendship-service already published to Kafka; the consumer fix was type-only.

### Chat room membership realtime events now forwarded

| Event type | Emitted by | Forwarded via | Channel |
|------------|-----------|---------------|---------|
| `chat.room.member.joined` | `RoomMembershipApplicationService` | `ChatRedisPublisher.publishMemberEvent()` | `realtime.chat.room.<roomId>` |
| `chat.room.member.left` | `RoomMembershipApplicationService` | `ChatRedisPublisher.publishMemberEvent()` | `realtime.chat.room.<roomId>` |
| `chat.room.member.removed` | `RoomMembershipApplicationService`, `RoomModerationApplicationService` | `ChatRedisPublisher.publishMemberEvent()` | `realtime.chat.room.<roomId>` |

Events are published as payload-less per the `SharedEventCatalog` contract. Room identity is implicit in the channel name.

---

## 4. Validation

### Compile result
```
.\gradlew.bat :chat-service:compileJava :notification-service:compileJava --no-daemon
BUILD SUCCESSFUL in 29s
17 actionable tasks: 2 executed, 15 up-to-date
```

### Test result
```
.\gradlew.bat :chat-service:test :notification-service:test --no-daemon --rerun-tasks
BUILD SUCCESSFUL in 1m 59s
33 actionable tasks: 33 executed
```

### Test coverage added/updated

**chat-service:** 98 tests total (was 93), 0 failures, 0 errors
- New: `ChatRealtimeAdapterTest` (5 tests)
  - `publishRoomEvent_memberJoined_forwardsToPublisher`
  - `publishRoomEvent_memberLeft_forwardsToPublisher`
  - `publishRoomEvent_memberRemoved_forwardsToPublisher`
  - `publishRoomEvent_messagePinned_forwardsPinPayload`
  - `publishRoomEvent_unsupportedType_doesNotCallPublisher`

**notification-service:** 26 tests total (was 19), 0 failures, 0 errors
- New: `NotificationKafkaConsumersTest` (7 tests)
  - `messageConsumer_delegatesToApplicationService`
  - `messageConsumer_nullEnvelope_doesNotDelegate`
  - `messageConsumer_nullPayload_doesNotDelegate`
  - `reactionConsumer_delegatesToApplicationService`
  - `reactionConsumer_nullEnvelope_doesNotDelegate`
  - `friendRequestConsumer_delegatesToApplicationService`
  - `friendRequestConsumer_nullEnvelope_doesNotDelegate`

---

## 5. Remaining Risks

### 1. Membership events published as payload-less
`MEMBER_JOINED`, `MEMBER_LEFT`, and `MEMBER_REMOVED` are declared payload-less in `SharedEventCatalog`. The Redis message contains the event type and channel (which encodes the roomId) but no user identity payload. Clients that receive these events via WebSocket fanout will need to query the membership endpoint to determine who joined or left. This is a semantic gap that requires either a `SharedEventCatalog` change (adding membership payloads as payload-bearing types) or a convention to embed actor identity in event metadata correlationId.

### 2. Chat Kafka publishing is fire-and-forget
`ChatMessageEventPublisherAdapter.publishToKafka()` does not handle Kafka publish failures separately from the Redis publish. If the Kafka broker is unavailable, the error is propagated up and the Redis publish that already succeeded is not rolled back. This is an at-least-once / partial-delivery risk that exists in the current architecture. No change to this risk was introduced; it is the existing pattern used by `FriendshipEventProducer`.

### 3. `FriendRequestEventConsumer` listens to all friendship request event types
The consumer listens to `friendship.request.events` which receives `SENT`, `ACCEPTED`, `DECLINED`, and `CANCELLED` events. `NotificationKafkaEventApplicationService.handleFriendRequestEvent()` passes the event type string through to `NotificationFriendRequestEventApplicationService.handle()`. If that delegate does not handle all four event types, some events will be silently no-oped. This is a pre-existing logic risk in the application service layer, not introduced by this fix.

---

## 6. Ready For Freeze Review

**YES** — the two runtime wiring/correctness blockers identified in the post-blocker-fix freeze review are now resolved:

1. Notification-service has real inbound adapters wired to the application service for all four intended notification-producing event types (account-created, chat-message-sent, reaction-updated, friend-request).
2. Chat-service's realtime adapter no longer silently drops room membership events; `MEMBER_JOINED`, `MEMBER_LEFT`, and `MEMBER_REMOVED` are forwarded through the outbound Redis publication path.

Both services compile clean and pass all tests. The service layer is ready to be reviewed for freeze again.
