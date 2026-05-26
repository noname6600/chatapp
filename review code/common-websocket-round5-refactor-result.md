# Common WebSocket Round 5 Refactor Result

## Scope

Modified only inside `chatappBE/common/common-websocket/**` and the minimal shared contract/docs/tests needed for this refactor.
No service modules, gateway, domain/business logic, Kafka module, Redis module, or code outside the strict common websocket scope was changed.

## Files Changed

Main source:
- `chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/adapter/spring/SpringRealtimeConnectionManager.java`
- `chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/adapter/spring/SpringRealtimeLifecycleAdapter.java`
- `chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/adapter/spring/SpringRealtimeHandshakeHandler.java`
- `chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/auth/RealtimeAuthorizationDecision.java`
- `chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/auth/RealtimeAuthorizationPolicy.java`
- `chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/auth/DevelopmentOnlyAllowAllRealtimeAuthorizationPolicy.java`
- `chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/observer/RealtimeObserver.java`
- `chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/observer/CompositeRealtimeObserver.java`

Tests:
- `chatappBE/common/common-websocket/src/test/java/com/example/common/websocket/adapter/spring/SpringRealtimeConnectionManagerTest.java`
- `chatappBE/common/common-websocket/src/test/java/com/example/common/websocket/adapter/spring/SpringRealtimeLifecycleAdapterTest.java`
- `chatappBE/common/common-websocket/src/test/java/com/example/common/websocket/auth/RealtimeAuthorizationPolicyTest.java`
- `chatappBE/common/common-websocket/src/test/java/com/example/common/websocket/adapter/spring/SpringRealtimeMessageSenderTest.java`
- `chatappBE/common/common-websocket/src/test/java/com/example/common/websocket/observer/RealtimeObserverTest.java`

## Legacy Paths Removed or Delegated

Removed:
- `closeSessionBestEffort(...)` was deleted from `SpringRealtimeConnectionManager`.

Delegated to the phase-aware cleanup path:
- `connect(String, WebSocketSession, Runnable, Runnable)` now delegates into the main phase-aware `connect(...)` implementation.
- `disconnect(String, WebSocketSession, Runnable, boolean)` now delegates into the phase-aware 7-argument `disconnect(...)` implementation.

The result is that there is only one real cleanup behavior in main source: the phase-aware cleanup path.

## Cleanup-Failure Reporting

Cleanup-failure reporting is now centralized in `SpringRealtimeConnectionManager`:
- Added an internal helper that reports cleanup failures by session id and phase.
- Replacement cleanup, disconnect cleanup, transport-error cleanup, and socket close now all use the same reporting path.
- Reporter callback failures are caught and logged so cleanup continues.
- There are no remaining silent `WebSocketSession.close()` swallow paths in main source.

## Authorization Contract Alignment

Subscribe authorization is now aligned with the CHANNEL-only model:
- `DevelopmentOnlyAllowAllRealtimeAuthorizationPolicy.allowSubscribe(...)` now calls `destination.requireSubscribable()`.
- Direct destinations (`GLOBAL`, `USER`, `SESSION`) are no longer treated as valid subscribe inputs.
- `RealtimeAuthorizationPolicy` now documents that subscribe authorization is only defined for subscribable destinations.
- `RealtimeAuthorizationPolicyTest` was updated so subscribe authorization tests use only `RealtimeDestination.channel(...)`, and a new test covers direct-destination rejection.

## Contract and Comment Cleanup

- `RealtimeAuthorizationDecision` Javadocs now describe the final allow/deny decision contract instead of migration wording.
- `RealtimeObserver` Javadocs now say observer failures are best-effort, must not propagate, and may be logged/suppressed by adapters/composites.
- `CompositeRealtimeObserver` docs now describe suppression without stale migration language.
- `SpringRealtimeHandshakeHandler` Javadoc no longer contains the stray closing `</p>`.

## Tests Added or Updated

Manager and lifecycle coverage:
- Added reporter-failure tests in `SpringRealtimeConnectionManagerTest` for replacement cleanup, disconnect cleanup, and transport-error cleanup.
- Added real adapter-path tests in `SpringRealtimeLifecycleAdapterTest` covering:
  - replacement subscription cleanup failure still attempts replaced-session observer notification
  - replacement observer notification failure still attempts socket close
  - connected callback still runs after replacement cleanup path
  - disconnect path with a throwing observer still completes later cleanup
  - transport-error path with a throwing observer still completes later cleanup

Authorization coverage:
- Updated `RealtimeAuthorizationPolicyTest` to use channel-only subscribe inputs.
- Added direct destination rejection coverage for `allowSubscribe(...)`.

Polish updates:
- Fixed the broken import line in `SpringRealtimeMessageSenderTest`.
- Cleaned observer/comment formatting in `RealtimeObserverTest`.

## Validation Command Results

All required validations passed:
- `.\gradlew.bat :common:common-websocket:compileJava --no-daemon`
- `.\gradlew.bat :common:common-websocket:compileTestJava --no-daemon`
- `.\gradlew.bat :common:common-websocket:test --no-daemon --rerun-tasks`

Results:
- `BUILD SUCCESSFUL` for `:common:common-websocket:compileJava`
- `BUILD SUCCESSFUL` for `:common:common-websocket:compileTestJava`
- `BUILD SUCCESSFUL` for `:common:common-websocket:test`

## Remaining Risks Inside common-websocket Only

- Legacy overloads remain in `SpringRealtimeConnectionManager` for compatibility, but they now delegate into the phase-aware implementation. If the team later wants to remove package-private compatibility methods entirely, that can be done without changing behavior.
- Observer callback failures in `SpringRealtimeLifecycleAdapter` remain log-only by design through `safeObserver(...)`; that is consistent with the current contract, but it means adapter-level observer failures are not phase-reported as cleanup failures.
- `common-websocket` still has separate standard and compatibility paths in a few places for test/support reasons, but they now share the same cleanup behavior and no longer swallow failures silently.

## Result

`common-websocket` is now freeze-ready for the Round 5 scope:
- silent cleanup paths removed or fully delegated
- cleanup-failure reporting centralized
- close failures phase-reported and logged
- subscribe authorization aligned with CHANNEL-only inputs
- final contract wording cleaned up
- adapter-level failure-path tests added
- required Gradle validations passed