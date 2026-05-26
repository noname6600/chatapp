# Common WebSocket Round 6 Review

## 1. Scope Reviewed
- Reviewed allowed module scope only: `chatappBE/common/common-websocket/**`.
- Reviewed direct shared contract boundary only where referenced by websocket: `chatappBE/common/common-event-contract/**`.
- Exact primary production packages reviewed:
  - `com.example.common.websocket.adapter.spring`
  - `com.example.common.websocket.auth`
  - `com.example.common.websocket.codec`
  - `com.example.common.websocket.config`
  - `com.example.common.websocket.error`
  - `com.example.common.websocket.frame`
  - `com.example.common.websocket.identity`
  - `com.example.common.websocket.inbound`
  - `com.example.common.websocket.observer`
  - `com.example.common.websocket.sender`
  - `com.example.common.websocket.session`
  - `com.example.common.websocket.subscription`
  - Boundary package: `com.example.common.event`
- Exact focus classes reviewed:
  - `SpringRealtimeConnectionManager`
  - `SpringRealtimeLifecycleAdapter`
  - `SpringRealtimeTextWebSocketHandler`
  - `SpringRealtimeMessageSender`
  - `SpringRealtimeHandshakeHandler`
  - `SpringRealtimeHandshakeInterceptor`
  - `RealtimeAuthorizationDecision`
  - `RealtimeAuthorizationPolicy`
  - `DevelopmentOnlyAllowAllRealtimeAuthorizationPolicy`
  - `DefaultRealtimeInboundFrameHandler`
  - `RealtimeDestination`
  - `RealtimeDestinationType`
  - `RealtimeObserver`
  - `CompositeRealtimeObserver`
  - `LoggingRealtimeObserver`
  - `MicrometerRealtimeObserver`
  - `RealtimeEventFrame`
  - Boundary: `EventEnvelope`, `EventMetadata`
- Exact test areas reviewed:
  - `adapter/spring/*Test`
  - `auth/RealtimeAuthorizationPolicyTest`
  - `guard/WebSocketContractGuardTest`
  - `inbound/DefaultRealtimeInboundFrameHandlerTest`
  - `observer/RealtimeObserverTest`
  - `sender/*Test`
  - `subscription/*Test`
  - `config/RealtimeWebSocketAutoConfigurationTest`
- Boundary limits applied:
  - Did not inspect or rely on any service module, gateway, Kafka implementation, Redis implementation, frontend, or business/domain logic outside the allowed common boundary.
  - Kafka/Redis were considered only as forbidden dependency names in `common-websocket` boundary searches and existing guard tests, not as implementation context.

## 2. Build/Test Validation
- Command run from `chatappBE`: `.\gradlew.bat :common:common-websocket:compileJava --no-daemon`
  - Compile status: PASS
  - Result: `BUILD SUCCESSFUL`
  - Tasks: `:common:common-event-contract:compileJava` and `:common:common-websocket:compileJava` were up-to-date.
- Command run from `chatappBE`: `.\gradlew.bat :common:common-websocket:compileTestJava --no-daemon`
  - Compile status: PASS
  - Result: `BUILD SUCCESSFUL`
  - Tasks: 4 actionable tasks, all up-to-date.
- Command run from `chatappBE`: `.\gradlew.bat :common:common-websocket:test --no-daemon --rerun-tasks`
  - Test status: PASS
  - Result: `BUILD SUCCESSFUL`
  - Tasks: 6 actionable tasks executed.
  - Parsed test XML summary: 169 tests, 0 failures, 0 errors, 0 skipped.
- Noteworthy warnings:
  - Gradle emitted an incubating problems-report notice.
  - Java emitted: `Some input files use or override a deprecated API.`
  - JVM emitted the standard class-data-sharing warning: `Sharing is only supported for boot loader classes because bootstrap classpath has been appended`.

## 3. What Improved
- `SpringRealtimeLifecycleAdapter` uses the final manager lifecycle methods in the main production path:
  - `onConnected(...)` calls the six-argument `connectionManager.connect(...)`.
  - `onDisconnected(...)` calls the seven-argument phase-aware `connectionManager.disconnect(...)`.
  - `onTransportError(...)` calls `connectionManager.transportErrorCleanup(...)`.
- `SpringRealtimeConnectionManager` now has phase-aware cleanup for the main replacement, disconnect, and transport-error paths:
  - Replacement: subscription cleanup, observer disconnect, socket close.
  - Disconnect: session unregister, subscription cleanup, observer notify, optional socket close.
  - Transport error: session unregister, subscription cleanup, observer notify, socket close.
- Socket close failures in the manager are not silently swallowed in the main helper: `closeSessionPhase(...)` reports through `reportCleanupFailure(...)`, and `reportCleanupFailure(...)` logs before invoking the reporter.
- Cleanup reporter callback failures are at least logged by `SpringRealtimeConnectionManager`: `reportCleanupFailure(...)` catches reporter failures and logs `cleanup-failure callback threw`.
- Manager tests now cover phase continuation when cleanup phases and reporter callbacks throw:
  - `replacement_cleanup_reports_each_phase_failure_even_when_reporter_throws`
  - `disconnect_cleanup_reports_each_phase_failure_even_when_reporter_throws`
  - `transport_error_cleanup_reports_each_phase_failure_even_when_reporter_throws`
- Subscribe authorization is runtime-aligned with CHANNEL-only subscribable destinations:
  - `DefaultRealtimeInboundFrameHandler.handleSubscribe(...)` rejects non-subscribable destinations before calling the policy.
  - `DevelopmentOnlyAllowAllRealtimeAuthorizationPolicy.allowSubscribe(...)` calls `destination.requireSubscribable()`.
  - Inbound tests reject `GLOBAL`, `USER`, and `SESSION` subscribe attempts.
- Dependency isolation is good at the code/build boundary:
  - `common-websocket` declares only one project dependency: `api project(':common:common-event-contract')`.
  - The only direct boundary import from `common-event-contract` is `EventEnvelope` in `RealtimeEventFrame`.
  - No service, gateway, Kafka, Redis, or frontend implementation imports were found in `common-websocket`.

## 4. Remaining Problems

### High
- File/class: `common-websocket/src/main/java/com/example/common/websocket/adapter/spring/SpringRealtimeConnectionManager.java` / `SpringRealtimeConnectionManager`
  - Why it is a problem: package-private legacy overloads still exist: `connect(String, WebSocketSession, Runnable, Runnable)` and `disconnect(String, WebSocketSession, Runnable, boolean)`. The class-level Javadoc says external code must go through `SpringRealtimeLifecycleAdapter`, but package-private production code in the same package can still call these alternate lifecycle paths.
  - Impact: freeze would lock in split lifecycle APIs. Even though the compatibility overloads route into phase-aware helpers, they collapse explicit cleanup phases into a legacy callback shape and normalize direct manager lifecycle usage outside the final adapter path.
  - Recommended fix: remove the compatibility overloads before freeze, or make them test-only via a test helper outside production code. Update tests to use the final phase-aware manager API or the lifecycle adapter real path.

- File/class: `common-websocket/src/main/java/com/example/common/websocket/observer/CompositeRealtimeObserver.java` / `CompositeRealtimeObserver`
  - Why it is a problem: delegate observer exceptions are caught and silently ignored in `suppress(...)`; there is no log, metric, callback, or structured signal. Because the composite itself does not rethrow, adapter-level `safeObserver(...)` cannot observe that a delegate failed.
  - Impact: observer failures are not truly observable in the common standard path when services use the recommended composite observer. This directly weakens lifecycle adapter failure observability and can hide broken logging/metrics observers during production incidents.
  - Recommended fix: make delegate suppression observable inside `CompositeRealtimeObserver`, at minimum with a warning log that includes the callback context and delegate class. Add tests that prove delegate failures are observable while later delegates still run.

### Medium
- File/class: `common-websocket/src/test/java/com/example/common/websocket/adapter/spring/SpringRealtimeMessageSenderTest.java` / `SpringRealtimeMessageSenderTest`
  - Why it is a problem: many tests still call raw package-private `connectionManager.connect("...", session, null, null)` and `connectionManager.disconnect("...", session, cleanupAction, true)`. One test comment explicitly states replacement happened without cleanup callbacks and cleanup only runs through lifecycle adapter or explicit disconnect.
  - Impact: the test suite continues to exercise and normalize the old direct manager lifecycle behavior. That weakens freeze confidence because regressions in the final adapter cleanup contract could be masked by passing tests that use the legacy path.
  - Recommended fix: keep sender tests focused on sender behavior but seed sessions through a narrow test fixture or the final manager API. Do not keep production legacy overloads only to make sender tests convenient.

- File/class: `common-websocket/src/test/java/com/example/common/websocket/adapter/spring/SpringRealtimeLifecycleAdapterTest.java` / `SpringRealtimeLifecycleAdapterTest`
  - Why it is a problem: early adapter tests verify only manager method invocation against a mocked `SpringRealtimeConnectionManager`. Real-path tests were added, but observer failure-path tests mainly assert cleanup continues; they do not assert the failure is actually observable via log capture or a structured observer signal.
  - Impact: adapter observer failures can regress from observable to silent without these tests failing, especially because `CompositeRealtimeObserver` already suppresses delegate failures silently.
  - Recommended fix: add real-path tests with log capture or another explicit observable signal for observer callback failures during replacement, disconnect, and transport cleanup.

- File/class: `common-websocket/src/test/java/com/example/common/websocket/adapter/spring/SpringRealtimeLifecycleAdapterTest.java` and `SpringRealtimeConnectionManagerTest`
  - Why it is a problem: close-failure reporting is tested at manager level, but not consistently through the lifecycle adapter real path. There is no adapter test that makes replaced-session close, disconnect close, or transport close throw and then verifies `observer.onInternalError(...)` receives the cleanup failure context.
  - Impact: the final reusable adapter contract could fail to report close failures while manager unit tests still pass.
  - Recommended fix: add adapter real-path close-failure tests for replacement, normal disconnect, and transport error cleanup, verifying the expected cleanup failure contexts.

- File/class: `common-websocket/src/test/java/com/example/common/websocket/adapter/spring/SpringRealtimeConnectionManagerTest.java` / `SpringRealtimeConnectionManagerTest`
  - Why it is a problem: `legacy_disconnect_cleanup_remains_phase_routed` still explicitly tests legacy behavior. This is useful during migration, but it is stale for a freeze-readiness suite.
  - Impact: the test suite communicates that the legacy overload is still a supported production behavior.
  - Recommended fix: replace this with tests around the final phase-aware disconnect API only, and remove the legacy overload.

### Low
- File/class: `common-websocket/src/main/java/com/example/common/websocket/auth/DevelopmentOnlyAllowAllRealtimeAuthorizationPolicy.java`
  - Why it is a problem: Javadoc says it "unconditionally permits all connections and subscriptions", but runtime rejects non-subscribable destinations by calling `destination.requireSubscribable()`.
  - Impact: docs and runtime behavior disagree at the subscribe authorization boundary.
  - Recommended fix: update the wording to say it permits all valid subscribable CHANNEL subscriptions and rejects direct destinations.

- File/class: `common-websocket/src/test/java/com/example/common/websocket/auth/RealtimeAuthorizationPolicyTest.java`
  - Why it is a problem: `allowAllPolicy_rejects_direct_subscribe_destinations` covers `GLOBAL` and `USER`, but not `SESSION`. Inbound tests cover `SESSION`, so runtime coverage exists, but the auth policy test itself is incomplete.
  - Impact: a future change could accidentally allow `SESSION` in the policy test layer and still leave this test green.
  - Recommended fix: include `RealtimeDestination.session(...)` in the direct-subscribe rejection test.

- File/class: `common-websocket/src/test/java/com/example/common/websocket/auth/RealtimeAuthorizationPolicyTest.java`
  - Why it is a problem: `customPolicy_canDenyConnect_for_anonymous` calls `allowConnect(null)`, while `RealtimeAuthorizationPolicy` documents that `principal` is never null.
  - Impact: test examples contradict the common authorization contract and can encourage null-tolerant policy implementations even though the interface says callers should provide a non-null principal.
  - Recommended fix: remove the null principal assertion from the custom policy example or move null handling to an explicit defensive test that does not imply normal contract behavior.

- File/class: `common-websocket/src/main/java/com/example/common/websocket/observer/RealtimeObserver.java`
  - Why it is a problem: `onDisconnected(...)` says `closeReason may be null`, while `SpringRealtimeLifecycleAdapter` normalizes null close reasons to `DISCONNECTED`.
  - Impact: the public observer contract is looser than the standard adapter behavior, leaving consumers unsure whether they must handle null in the common standard path.
  - Recommended fix: document the standard adapter behavior and reserve null only for custom/manual callers, or require structured non-null reason labels.

- File/class: `common-websocket/src/main/java/com/example/common/websocket/observer/CompositeRealtimeObserver.java`
  - Why it is a problem: the Javadoc has a `<pre>` block nested inside a `<p>` block and then closes `</p>` after `</pre>`, which is malformed HTML-style Javadoc structure.
  - Impact: contract documentation looks unfinished and may produce poor generated Javadoc.
  - Recommended fix: close the paragraph before the `<pre>` block or use a standalone `<pre>{@code ...}</pre>` block.

- File/class: `common-event-contract/src/main/java/com/example/common/event/EventEnvelope.java`
  - Why it is a problem: the first Javadoc paragraph opens `<p>` but is not closed before a second `<p>` begins.
  - Impact: the directly referenced shared event boundary has minor malformed contract documentation.
  - Recommended fix: close the first paragraph before the transport-neutral paragraph.

- File/class: `common-websocket/src/main/java/com/example/common/websocket/auth/DevelopmentOnlyAllowAllRealtimeAuthorizationPolicy.java` and tests using it
  - Why it is a problem: the class is deprecated and tests instantiate it, causing Gradle's deprecation warning during validation.
  - Impact: freeze validation is green but not clean.
  - Recommended fix: either remove the deprecation warning source from freeze tests or replace test usage with local test policies that do not trigger deprecated API warnings.

## 5. Regression / Risk Check
- Main lifecycle behavior that could regress:
  - Replacement cleanup could stop closing the replaced socket.
  - Disconnect or transport cleanup could stop reporting phase failures.
  - Observer callback failures could become silent, especially through `CompositeRealtimeObserver`.
  - Direct raw manager calls could bypass lifecycle adapter cleanup conventions if future same-package production code uses the remaining overloads.
- Freeze confidence is still weak where tests use split paths:
  - `SpringRealtimeMessageSenderTest` seeds and replaces sessions through legacy `connectionManager.connect(...)`.
  - `SpringRealtimeMessageSenderTest` disconnects through the legacy combined cleanup overload.
  - `SpringRealtimeConnectionManagerTest` still contains a legacy-specific test.
- Misleading tests and docs remain:
  - The allow-all auth policy docs imply all subscriptions are allowed, while runtime rejects non-CHANNEL destinations.
  - A custom auth policy test accepts `allowConnect(null)` despite the interface documenting a non-null principal contract.
  - `RealtimeObserver` allows nullable close reasons in docs while the standard adapter normalizes them.
- Hidden maintenance traps:
  - The manager overloads are package-private, not private; package-private production APIs are still production APIs.
  - Composite observer suppression prevents adapter-level observer failure logging from seeing delegate failures.
- Boundary risk:
  - No code-level service/gateway/Kafka/Redis coupling was found.
  - The only direct shared code dependency remains `common-event-contract`.

## 6. Freeze Readiness
NO

Exact blockers inside `common` only:
- `SpringRealtimeConnectionManager` still contains package-private legacy lifecycle overloads that preserve alternate cleanup entry points.
- `CompositeRealtimeObserver` silently suppresses delegate observer failures, making observer failure observability incomplete in the recommended common observer composition path.
- Adapter real-path tests do not prove observer failure observability or close-failure reporting through the final lifecycle adapter contract.
- Tests still exercise and normalize old raw manager lifecycle behavior, especially in `SpringRealtimeMessageSenderTest` and the legacy manager test.

## 7. Final Recommendation
- Before freezing, remove the legacy package-private manager overloads and update tests to use either the final phase-aware manager API or `SpringRealtimeLifecycleAdapter` real paths.
- Make observer delegate failures observable in `CompositeRealtimeObserver`, then add tests proving failures are logged or otherwise represented while later delegates still run.
- Add adapter real-path tests for replacement, normal disconnect, and transport close failures, including cleanup reporter failure behavior.
- Tighten the auth/documentation polish inside common only: fix allow-all policy wording, add the missing `SESSION` policy rejection assertion, remove the null-principal example, and clean malformed Javadocs/deprecation-warning sources.
