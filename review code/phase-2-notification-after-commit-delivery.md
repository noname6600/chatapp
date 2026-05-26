# Phase 2 - Notification-Service After-Commit Delivery

## Scope
- Service: notification-service only
- Goal: move realtime push/delivery side effects out of transactional write bodies into an after-commit trigger
- Out of scope: outbox introduction, broad notification architecture redesign

## Files Changed
- chatappBE/notification-service/src/main/java/com/example/notification/service/impl/NotificationCommandService.java
- chatappBE/notification-service/src/test/java/com/example/notification/service/impl/NotificationCommandServiceTest.java

## Problem
- `NotificationCommandService` invoked realtime push side effects directly during transactional write methods:
  - `pushService.pushToUser(...)` in create flow
  - `pushService.pushUnreadCount(...)` in read/resolve/mark-all/mark-room flows
- This allowed delivery side effects to run before transaction commit safety.

## Implementation
1. Added service-local transaction-aware side-effect trigger in `NotificationCommandService`:
   - `runAfterCommit(Runnable action)`
   - Registers `TransactionSynchronization.afterCommit()` when transaction synchronization and actual transaction are active.
   - Falls back to immediate execution when no active synchronization context exists.

2. Routed push side effects through after-commit trigger:
   - `createNotification(...)` now schedules `pushService.pushToUser(...)` after commit.
   - `markRead(...)`, `resolveActionRequired(...)`, `markAllRead(...)`, and `markReadByRoom(...)` now schedule unread-count push after commit.

3. Added safe error handling for side effects:
   - `runSafely(...)` wraps side effects and logs failures without interrupting authoritative persistence behavior.

## Business Behavior
- Persisted notification writes remain authoritative and unchanged.
- Side effects are delayed until commit when transaction context is active.
- Fallback immediate delivery remains for non-transactional contexts/tests.

## Focused Test Updates
- Extended `NotificationCommandServiceTest` with two focused tests:
  - `createNotification_pushesAfterCommit_whenTransactionSynchronizationActive`
  - `createNotification_pushesImmediately_whenNoTransactionSynchronization`
- Added cleanup for transaction synchronization state in test teardown.

## Verification Gate
- Command:
  - `./gradlew :notification-service:test --tests com.example.notification.service.impl.NotificationCommandServiceTest --tests com.example.notification.controller.NotificationControllerTest`
- Result:
  - BUILD SUCCESSFUL

## Risks / Follow-up
- Delivery remains best-effort (errors logged, no retry queue).
- This is intentional for Phase 2 narrow reliability hardening; outbox/retry remains a later phase.
