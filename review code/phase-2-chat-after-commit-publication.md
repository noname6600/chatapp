# Phase 2 - Chat-Service After-Commit Message Event Publication

## Scope
- Service: chat-service only
- Goal: replace unreliable fire-and-forget async message-created publication in send pipeline with safer after-commit publication
- Out of scope: outbox, broad pipeline redesign, common-module redesign

## Files Changed
- chatappBE/chat-service/src/main/java/com/example/chat/modules/message/application/pipeline/send/steps/PublishMessageEventStep.java
- chatappBE/chat-service/src/test/java/com/example/chat/modules/message/application/pipeline/send/steps/PublishMessageEventStepTest.java

## Problem
- `PublishMessageEventStep` used `CompletableFuture.runAsync(...)` to publish critical message-created events.
- This could publish at unreliable timing relative to transaction boundaries and had fire-and-forget failure behavior.

## Implementation
1. Removed `CompletableFuture.runAsync(...)` fire-and-forget publication.
2. Added local transaction-aware publication path in `PublishMessageEventStep`:
   - If transaction synchronization is active, register `afterCommit` callback and publish there.
   - If synchronization is not active, publish immediately (safe fallback).
3. Kept failure handling best-effort and non-throwing for publication side effects by wrapping publish in try/catch and logging warning.

## Semantics Preservation
- Preserved existing message send behavior for core write path:
  - room projection update (`roomService.updateLastMessage(...)`) remains synchronous
  - sender read marker update (`roomService.markRoomRead(...)`) remains synchronous
- Publication side effect remains non-blocking for business success in the sense that publish failures are logged and do not fail send response.
- Reliability is improved by publishing after commit when transaction synchronization is present.

## Tests Added
- `PublishMessageEventStepTest` with focused coverage:
  - Registers publish for after-commit when transaction synchronization is active.
  - Publishes immediately when no transaction synchronization is active.
  - Does not throw when publication fails in after-commit callback.

## Verification Gate
- Command:
  - `./gradlew :chat-service:test --tests com.example.chat.modules.message.application.pipeline.send.steps.PublishMessageEventStepTest --tests com.example.chat.modules.message.application.command.impl.MessageCommandServiceForwardTransactionalRollbackTest`
- Result:
  - BUILD SUCCESSFUL

## Risks / Follow-up
- Publish failures after commit are still best-effort (logged, not retried).
- This is intentional for this narrow Phase 2 change; durable retry/outbox remains a later phase.
