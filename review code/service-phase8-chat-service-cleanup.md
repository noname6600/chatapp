# Service Phase8: Chat-Service Focused Cleanup

## 1) Scope

In scope:
- chat-service only

Out of scope respected:
- other backend services
- common modules
- frontend
- deployment/infrastructure

This phase performs focused architecture cleanup while preserving the `IRoomService` public API and existing behavior.

## 2) Cleanup goals completed

1. Split oversized room orchestration responsibilities behind focused application services.
2. Keep websocket ingress as a thin adapter and avoid command orchestration leakage.
3. Remove hidden side effects from message payload factory logic.
4. Keep outbound event publication behind adapter/port boundaries.

## 3) Files changed

### Room service responsibility split
- `chatappBE/chat-service/src/main/java/com/example/chat/modules/room/service/impl/RoomService.java`
- `chatappBE/chat-service/src/main/java/com/example/chat/modules/room/service/impl/RoomLifecycleApplicationService.java`
- `chatappBE/chat-service/src/main/java/com/example/chat/modules/room/service/impl/RoomMembershipApplicationService.java`
- `chatappBE/chat-service/src/main/java/com/example/chat/modules/room/service/impl/RoomModerationApplicationService.java`
- `chatappBE/chat-service/src/main/java/com/example/chat/modules/room/service/impl/RoomReadStateApplicationService.java`
- `chatappBE/chat-service/src/main/java/com/example/chat/modules/room/service/impl/RoomMetadataApplicationService.java`
- `chatappBE/chat-service/src/main/java/com/example/chat/modules/room/service/impl/RoomMessageStateApplicationService.java`

Result:
- `RoomService` is now a facade/delegator keeping interface compatibility.
- Lifecycle, membership, moderation, read-state, metadata/avatar, and room last-message concerns are isolated.

### Payload factory side-effect removal
- `chatappBE/chat-service/src/main/java/com/example/chat/modules/message/event/factory/ChatMessagePayloadFactory.java`
- `chatappBE/chat-service/src/main/java/com/example/chat/modules/message/infrastructure/kafka/MessageSenderProfileResolver.java`
- `chatappBE/chat-service/src/main/java/com/example/chat/modules/message/infrastructure/kafka/KafkaChatMessageEventPublisher.java`

Result:
- `ChatMessagePayloadFactory` is now pure mapping logic.
- Sender profile backfill/sync side effects moved into `MessageSenderProfileResolver` and called by Kafka publisher before payload creation.

## 4) Boundary status after cleanup

### Room application boundary
- Domain orchestration remains service-owned.
- Existing API shape (`IRoomService`) remains stable for callers.
- Internal behavior is decomposed into focused services for maintainability and future extraction.

### Websocket adapter boundary
- No additional orchestration logic was moved into websocket handlers.
- Existing thin adapter model remains intact.

### Outbound publication boundary
- Message publish path continues through output adapter (`KafkaChatMessageEventPublisher`).
- Payload assembly is cleanly separated from side-effectful membership/profile synchronization.

## 5) Validation

Compile command:
- `./gradlew.bat :chat-service:compileJava --no-daemon`

Compile result:
- `BUILD SUCCESSFUL`

Test command:
- `./gradlew.bat :chat-service:test --no-daemon`

Test result:
- `:chat-service:compileTestJava` failed with pre-existing missing symbols/classpath drift in realtime/common contracts (same known pattern from earlier phases), e.g. unresolved `RealtimeEventDedupeGuard`, `RedisMessage`, `RealtimeWsEvent` and related test-side types.
- No new compileJava regressions from phase8 cleanup.

## 6) Notes

- Behavior-preserving cleanup was prioritized.
- No realtime-edge introduction was performed.
- This phase prepares chat-service internals for later extraction/cutover work without broad cross-service rewrites.
