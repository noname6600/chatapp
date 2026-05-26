# Phase 1 Run Note (Recheck Then Fix)

Date: 2026-05-21
Run scope: Phase 1 items from DETAILED_FIX_BACKLOG_FROM_ALL_REPORTS.md
Method: Rechecked each item in code/config first, then fixed only items still present.

## Recheck Results and Fixes

1. SEC-001 (token in websocket session attributes)
- Recheck result: STILL EXISTS.
- Evidence before fix:
  - JwtHandshakeInterceptor stored `attributes.put("accessToken", accessToken)`.
  - RealtimeWebSocketHandler read token from session attributes in multiple handlers.
- Fix applied:
  - Store token in Redis using short-lived token reference (`accessTokenRef`) during handshake.
  - Store only `accessTokenRef` in websocket session attributes.
  - Resolve token by ref per-message in handler; delete token ref key on disconnect.
- Files:
  - chatappBE/realtime-edge-service/src/main/java/com/chatweb/realtime/adapter/in/websocket/JwtHandshakeInterceptor.java
  - chatappBE/realtime-edge-service/src/main/java/com/chatweb/realtime/adapter/in/websocket/RealtimeWebSocketHandler.java
  - Updated tests:
    - chatappBE/realtime-edge-service/src/test/java/com/chatweb/realtime/adapter/in/websocket/JwtHandshakeInterceptorTest.java
    - chatappBE/realtime-edge-service/src/test/java/com/chatweb/realtime/adapter/in/websocket/RealtimeWebSocketHandlerChatRoutingTest.java
    - chatappBE/realtime-edge-service/src/test/java/com/chatweb/realtime/adapter/in/websocket/RealtimeWebSocketHandlerCommandFailureTest.java

2. DB-002 (room owner transfer race)
- Recheck result: STILL EXISTS (leaveRoom used unlocked reads/write sequence).
- Fix applied:
  - Added pessimistic lock repository methods (`findByRoomIdAndUserIdForUpdate`, `findByRoomIdForUpdate`).
  - Updated leaveRoom owner handoff flow to use locked queries.
- Files:
  - chatappBE/chat-service/src/main/java/com/chatweb/chat/modules/room/repository/RoomMemberRepository.java
  - chatappBE/chat-service/src/main/java/com/chatweb/chat/modules/room/service/impl/RoomService.java

3. GATEWAY-001 (readiness missing realtime-edge)
- Recheck result: STILL EXISTS.
- Evidence before fix: gateway readiness list lacked realtime-edge dependency.
- Fix applied:
  - Added realtime-edge URL to `gateway.readiness.required-services`.
- File:
  - chatappBE/gateway-service/src/main/resources/application.yaml

4. ARCH-002 (realtime-edge BOM drift)
- Recheck result: STILL EXISTS.
- Evidence before fix: `spring-cloud-dependencies:2023.0.0` in realtime-edge build file.
- Fix applied:
  - Upgraded realtime-edge BOM to `2025.0.1`.
- File:
  - chatappBE/realtime-edge-service/build.gradle

5. KAFKA-005 (invalid eventId bypass dedupe)
- Recheck result: STILL EXISTS.
- Evidence before fix: dedupe guard returned false for null/blank eventId.
- Fix applied:
  - Invalid/missing/non-UUID event IDs are now rejected by dedupe guard.
  - Added warning logs at application-service call points when eventId missing.
- Files:
  - chatappBE/notification-service/src/main/java/com/chatweb/notification/infrastructure/kafka/NotificationEventDedupeGuard.java
  - chatappBE/notification-service/src/main/java/com/chatweb/notification/application/NotificationKafkaEventApplicationService.java

6. SEC-004 (prepare-token secret can be blank)
- Recheck result: STILL EXISTS.
- Evidence before fix: blank secret allowed via config and fallback logic.
- Fix applied:
  - Added startup validation: in non-local/test/validation profiles, fail startup if `upload.confirm.prepare-token-secret` is blank when prepare-token is required.
- File:
  - chatappBE/upload-service/src/main/java/com/chatweb/upload/service/UploadSigningService.java

7. INFRA-001 (redis keyspace events mismatch between compose profiles)
- Recheck result: STILL EXISTS.
- Evidence before fix: docker-compose.local.yml redis service had no keyspace event command while docker-compose.yml had `--notify-keyspace-events Kx`.
- Fix applied:
  - Added `redis-server --notify-keyspace-events Kx --appendonly yes` command to local compose redis service.
- File:
  - chatappBE/docker-compose.local.yml

8. INFRA-003 (listener enablement visibility/enforcement)
- Recheck result: PARTIALLY EXISTS.
- Existing behavior already had conditional listener bean requiring explicit true.
- Improvement applied this run:
  - Added health indicator to expose listener disabled state clearly in health checks.
- File:
  - chatappBE/realtime-edge-service/src/main/java/com/chatweb/realtime/config/RedisListenerHealthIndicator.java
- Note: hard fail-fast when disabled was intentionally not added in this run to avoid breaking existing boot/test flows that disable listener intentionally.

9. ARCH-003 (media-service vs upload-service naming drift)
- Recheck result: STILL EXISTS in historical docs text (as mismatch discussion).
- Fix applied:
  - Added canonical naming note to standardize future references.
- File:
  - review-reports/SERVICE_NAMING_CANONICAL.md

## Verification During This Run

Executed focused validation command:
- realtime-edge websocket tests (3 test classes)
- chat-service compile
- gateway-service compile
- notification-service compile
- upload-service compile

Result: BUILD SUCCESSFUL.

## Summary

- Rechecked Phase 1 items: 9
- Fixed or improved in this run: 9
- Hard blocker encountered: none
- Known intentional partial item: INFRA-003 fail-fast behavior (health visibility added; fail-fast deferred)
