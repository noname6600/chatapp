# Phase 2 - Auth-Service After-Commit Account-Created Publication

## Scope
- Service: auth-service only
- Goal: move account-created event emission out of in-transaction timing into a service-local after-commit mechanism
- Out of scope: full outbox, common event redesign

## Files Changed
- chatappBE/auth-service/src/main/java/com/example/auth/service/impl/LocalAuthService.java
- chatappBE/auth-service/src/main/java/com/example/auth/service/impl/OAuthAuthService.java
- chatappBE/auth-service/src/main/java/com/example/auth/service/event/AfterCommitExecutor.java
- chatappBE/auth-service/src/main/java/com/example/auth/service/event/TransactionalAfterCommitExecutor.java
- chatappBE/auth-service/src/main/java/com/example/auth/service/event/AccountCreatedAfterCommitPublisher.java
- chatappBE/auth-service/src/test/java/com/example/auth/service/impl/LocalAuthServiceTest.java
- chatappBE/auth-service/src/test/java/com/example/auth/service/impl/OAuthAuthServiceTest.java

## Implementation Summary
1. Added a service-local after-commit helper abstraction:
   - `AfterCommitExecutor` with `runAfterCommit(operationName, action)`.
   - `TransactionalAfterCommitExecutor` implementation using Spring transaction synchronization.
   - Behavior:
     - If an active transaction exists, register `afterCommit` callback.
     - If no active transaction exists, execute immediately (safe fallback for non-transactional contexts/tests).
     - Callback exceptions are logged and not rethrown.

2. Added auth-local account-created after-commit publisher:
   - `AccountCreatedAfterCommitPublisher` wraps `AccountCreatedEventProducer`.
   - Schedules producer call through `AfterCommitExecutor`.
   - Logs warning when producer returns false.

3. Rewired auth services:
   - `LocalAuthService`: replaced direct producer calls with `accountCreatedAfterCommitPublisher.publishAfterCommit(account)`.
   - `OAuthAuthService`: replaced direct producer calls with `accountCreatedAfterCommitPublisher.publishAfterCommit(account)`.

## Behavior Notes
- Account-created events are no longer emitted before transaction commit in transactional service flows.
- Token/account creation flow remains intact (same account/idp mutation path and returned IDs).
- Previous behavior that could fail registration/login flow due to producer publish result was removed from in-transaction path.
  - Event publication is now post-commit best-effort with explicit logging on failure.

## Test Updates
- Updated focused unit tests to verify after-commit publisher scheduling calls instead of direct producer calls:
  - `LocalAuthServiceTest`
  - `OAuthAuthServiceTest`
- Removed expectations tied to immediate publish-failure exceptions in those service tests.

## Verification Gate
- Command:
  - `./gradlew :auth-service:test --tests com.example.auth.service.impl.LocalAuthServiceTest --tests com.example.auth.service.impl.OAuthAuthServiceTest`
- Result:
  - BUILD SUCCESSFUL

## Risks / Follow-up
- Post-commit publication failure does not rollback persisted auth/account state; failures are logged.
- This is intentional for Phase 2 minimal reliability refactor and avoids risky in-transaction event emission.
- Full delivery guarantees (retry/outbox) remain a later phase.
