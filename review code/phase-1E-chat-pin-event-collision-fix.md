# Phase 1E - Chat Pin Event Collision Fix (chat-service only)

## Scope
- Service: `chat-service` only
- Goal: Remove startup payload registration collision for `chat.message.pinned` and `chat.message.unpinned` while preserving pin/unpin realtime fanout behavior.

## Files Changed
- `chatappBE/chat-service/src/main/java/com/example/chat/config/ChatRedisEventConfig.java`
- `chatappBE/chat-service/src/main/java/com/example/chat/modules/room/dto/RoomMessagePinEventPayload.java`
- `chatappBE/chat-service/src/main/java/com/example/chat/realtime/subscriber/ChatMessagePinnedRedisSubscriber.java`
- `chatappBE/chat-service/src/main/java/com/example/chat/realtime/subscriber/ChatMessageUnpinnedRedisSubscriber.java`
- `chatappBE/chat-service/src/main/java/com/example/chat/modules/room/service/impl/RoomPinService.java`
- `chatappBE/chat-service/src/main/java/com/example/chat/realtime/infrastructure/ChatRealtimeAdapter.java`
- `chatappBE/chat-service/src/main/java/com/example/chat/modules/message/infrastructure/redis/ChatRedisPublisher.java`
- `chatappBE/chat-service/src/test/java/com/example/chat/realtime/CrossInstanceRealtimeFanoutIntegrationTest.java`

## Old Collision Cause
- Shared catalog in `common-events` already registers:
  - `chat.message.pinned -> MessagePinPayload`
  - `chat.message.unpinned -> MessagePinPayload`
- `chat-service` re-registered the same event types to a different class (`RoomMessagePinEventPayload`) in `ChatRedisEventConfig`.
- `DefaultEventPayloadRegistry` rejects conflicting class re-registration for the same event type, causing startup/context failure.

## New Mapping and Registration Rule
- `chat-service` now registers both pin event types with the shared payload contract:
  - `chat.message.pinned -> MessagePinPayload`
  - `chat.message.unpinned -> MessagePinPayload`
- Producer path (`RoomPinService` -> `ChatRealtimeAdapter` -> `ChatRedisPublisher`) now emits `MessagePinPayload` for pin/unpin events.
- Subscriber path consumes `MessagePinPayload` and maps to internal `RoomMessagePinEventPayload` before websocket room fanout, preserving existing internal fanout payload shape.
- Dedupe for pin/unpin now uses envelope metadata event id (`dedupeGuard.isDuplicateKey(envelope.metadata().getEventId())`) rather than payload-local UUID.

## Runtime Behavior Preservation Notes
- Pin/unpin events still fan out to room websocket subscribers on the same event types.
- Internal websocket payload remains `RoomMessagePinEventPayload` (mapped from shared external payload), including room/message/actor/timestamp semantics.
- Event id for internal payload is parsed from envelope metadata event id.

## Regression Risks
- Low: any caller that sent pin/unpin through `ChatRealtimeAdapter` with an incompatible payload type now triggers `IllegalArgumentException` for pin/unpin event types.
- Low: if a non-UUID event id appears in metadata, mapped internal `eventId` becomes `null` (fanout continues).
- Medium: downstream systems that implicitly depended on old producer payload field names in raw Redis JSON are now aligned to shared contract field names (`actorUserId`, `pinnedAt`).

## Verification Gate Run
- Compile gate:
  - `./gradlew :chat-service:compileJava :chat-service:compileTestJava`
- Targeted stability/behavior tests:
  - `./gradlew :chat-service:test --tests com.example.demo.ChatappApplicationTests --tests com.example.chat.realtime.CrossInstanceRealtimeFanoutIntegrationTest`

## Result
- Compile and targeted test gates passed.
- Startup registration collision for pin/unpin event payload mapping is resolved in `chat-service`.
