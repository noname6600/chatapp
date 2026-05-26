# Phase 2 - Friendship-Service After-Commit Publication

## Scope
- Service: friendship-service only
- Goal: move friendship event publication out of in-transaction timing into a service-local after-commit mechanism
- Out of scope: outbox introduction, broad module redesign

## Files Changed
- chatappBE/friendship-service/src/main/java/com/example/friendship/service/impl/FriendCommandService.java
- chatappBE/friendship-service/src/test/java/com/example/friendship/service/impl/FriendCommandServiceAfterCommitPublicationTest.java

## Problem
- `FriendCommandService` emitted friendship events directly via `producer.publish(...)` from inside transactional command methods.
- This allowed publication timing before transaction commit safety and increased rollback/event-ordering risk.

## Implementation
1. Added a local after-commit publish path inside `FriendCommandService`:
   - `publishAfterCommit(FriendshipEventType, Friendship)`
   - Registers `TransactionSynchronization.afterCommit()` when synchronization is active.
   - Falls back to immediate publish when synchronization is not active.

2. Centralized producer invocation into `publishSafely(...)`:
   - Wraps `producer.publish(...)` in try/catch.
   - Logs publish failures without throwing.

3. Replaced all direct `producer.publish(...)` invocations in command mutations with `publishAfterCommit(...)`:
   - send request
   - auto-accept path
   - accept
   - decline
   - cancel
   - unfriend
   - block
   - unblock

## Business Behavior
- Friendship state mutation logic is unchanged.
- Event type selection and payload source entity remain unchanged.
- Publication timing is now transaction-safe when transaction synchronization is active.

## Focused Tests Added
- `FriendCommandServiceAfterCommitPublicationTest`
  - verifies no publish occurs before commit callback when synchronization is active
  - verifies publish occurs immediately when no synchronization context exists

## Verification Gate
- Command:
  - `./gradlew :friendship-service:test --tests com.example.friendship.service.impl.FriendCommandServiceAfterCommitPublicationTest --tests com.example.friendship.controller.InternalFriendControllerTest`
- Result:
  - BUILD SUCCESSFUL

## Risks / Follow-up
- Publication remains best-effort; failures are logged, not retried.
- This is intentional for Phase 2 narrow reliability hardening; durable retry/outbox is a later phase.
