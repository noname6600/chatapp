# Service Integration Test Pass

## 1. Scope
Focused integration-style service-layer test hardening was applied within:
- chatappBE/user-service
- chatappBE/notification-service
- chatappBE/gateway-service
- chatappBE/presence-service

Focused validation also included existing behavior tests in:
- chatappBE/chat-service
- chatappBE/friendship-service
- chatappBE/upload-service

Out-of-scope respected:
- chatappBE/common/**
- frontend
- structural production refactors

## 2. Tests Added/Updated
Added:
- chatappBE/user-service/src/test/java/com/example/user/application/UserKafkaAccountCreatedApplicationServiceTest.java
  - Verifies account-created profile creation from payload.
  - Verifies username collision retry (suffix allocation).
  - Verifies idempotent skip when profile already exists.

- chatappBE/notification-service/src/test/java/com/example/notification/application/NotificationKafkaEventApplicationServiceTest.java
  - Verifies chat message event delegation and dedupe-by-eventId.
  - Verifies friendship request event delegation, type propagation, and dedupe.
  - Verifies account-created event delegation to welcome notification path.

- chatappBE/gateway-service/src/test/java/com/example/gateway/filter/JwtAuthFilterGatewayFilterFactoryTest.java
  - Verifies JWT-authenticated requests get X-User-Id propagation header.
  - Verifies non-JWT principal path does not inject propagation header.

Updated:
- chatappBE/presence-service/src/test/java/com/example/presence/service/PresenceServiceTest.java
  - Added notifyRoomOnlineUsers_publishesAggregatedRoomState.
  - Verifies ROOM_ONLINE_USERS publication contains both ONLINE and AWAY users as expected from state transitions.

## 3. Flows Now Covered
1. Account registration -> account-created handling -> profile creation
- Covered by new user-service application test + existing consumer test.
- Includes normalization, collision retry, and idempotent skip semantics.

2. Chat message event -> notification creation path
- Covered at orchestration seam by notification Kafka event application service delegation + dedupe tests.
- Existing notification consumer delegation tests remain in place.

3. Friendship request event -> notification creation path
- Covered at orchestration seam by event-type propagation/delegation + dedupe tests.
- Existing notification consumer delegation tests remain in place.

4. Chat block-check path through friendship client
- Covered by existing chat-service CheckBlockedPairStepTest (validated in this pass).

5. Gateway routing / CORS / auth propagation
- Auth propagation strengthened by new JwtAuthFilterGatewayFilterFactoryTest.
- Existing SecurityConfig/Gateway CORS integration coverage remains available.

6. Upload metadata application path (service-local behavior)
- Existing upload controller purpose deserialization path validated in this pass.

7. Presence heartbeat/publication path
- Existing heartbeat/offline tests validated.
- New room online-users aggregate publication assertion added.

## 4. Validation
Command run from chatappBE:
- .\gradlew.bat :gateway-service:test --tests *JwtAuthFilterGatewayFilterFactoryTest :user-service:test --tests *UserKafkaAccountCreatedApplicationServiceTest --tests *AccountCreatedConsumerTest :notification-service:test --tests *NotificationKafkaEventApplicationServiceTest --tests *NotificationKafkaConsumersTest :chat-service:test --tests *CheckBlockedPairStepTest :friendship-service:test --tests *FriendCommandServiceTest :upload-service:test --tests *UploadControllerPurposeDeserializationTest :presence-service:test --tests *PresenceServiceTest --no-daemon

Result:
- BUILD SUCCESSFUL
- 56 actionable tasks: 2 executed, 54 up-to-date

Note:
- One initial failure in the newly added gateway filter test (auth principal not marked authenticated) was fixed, then the full focused suite above passed cleanly.

## 5. Remaining Unproved Flows
- Full end-to-end runtime chain across real infra boundaries (gateway -> service -> Kafka -> notification persistence -> websocket/client observation) is still not proven by these focused tests.
- OAuth and gateway external-provider callback behavior is not re-validated in this pass (existing tests remain, but not part of this lean run).
- Upload room-avatar path standardization versus upload-service mediated flow remains architectural consistency work, not test-only proof.
- Presence typing-specific transport behavior is not newly covered in this pass (current coverage focuses on heartbeat/status and room online-users publication).
