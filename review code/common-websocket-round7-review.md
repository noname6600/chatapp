# Common WebSocket Round 7 Review

## 1. Scope Reviewed
- Reviewed only `chatappBE/common/common-websocket/**` plus the directly referenced shared event contract classes under `chatappBE/common/common-event-contract/src/main/java/com/example/common/event/`.
- Main websocket classes reviewed: `SpringRealtimeConnectionManager`, `SpringRealtimeLifecycleAdapter`, `SpringRealtimeTextWebSocketHandler`, `SpringRealtimeMessageSender`, `SpringRealtimeSession`, `DefaultRealtimeInboundFrameHandler`, `RealtimeAuthorizationPolicy`, `DevelopmentOnlyAllowAllRealtimeAuthorizationPolicy`, `RealtimeDestination`, `RealtimeEventFrame`, `RealtimeObserver`, `CompositeRealtimeObserver`, `LoggingRealtimeObserver`, `MicrometerRealtimeObserver`, `RealtimeWebSocketAutoConfiguration`.
- Main websocket tests reviewed: `SpringRealtimeConnectionManagerTest`, `SpringRealtimeLifecycleAdapterTest`, `SpringRealtimeLifecycleAdapterRaceTest`, `SpringRealtimeMessageSenderTest`, `DefaultRealtimeInboundFrameHandlerTest`, `RealtimeAuthorizationPolicyTest`, `RealtimeObserverTest`, `RealtimeFrameContractTest`, `WebSocketContractGuardTest`, `RealtimeWebSocketAutoConfigurationTest`.
- Direct event-contract boundary reviewed where referenced by websocket: `EventEnvelope` and `EventMetadata`.
- Boundary limits applied: no service modules, gateway, kafka, redis, frontend, or business/domain logic outside the common websocket and direct event-contract boundary were inspected or used as evidence.

## 2. Build/Test Validation
- Commands run from `chatappBE`:
  - `.\gradlew.bat :common:common-websocket:compileJava --no-daemon`
  - `.\gradlew.bat :common:common-websocket:compileTestJava --no-daemon`
  - `.\gradlew.bat :common:common-websocket:test --no-daemon --rerun-tasks`
  - Additional warning diagnostic: `.\gradlew.bat :common:common-websocket:compileTestJava --no-daemon --rerun-tasks --warning-mode all`
- Compile status: PASS.
- Test compile status: PASS.
- Test status: PASS.
- Test totals from `common/common-websocket/build/test-results/test`: 169 tests, 0 failures, 0 errors, 0 skipped.
- Warnings:
  - The forced test run emitted `Note: Some input files use or override a deprecated API. Note: Recompile with -Xlint:deprecation for details.`
  - The extra `--warning-mode all` compile diagnostic still emitted the same generic javac deprecation note, without line-level detail.
  - Allowed-scope evidence shows repeated test usage of deprecated `DevelopmentOnlyAllowAllRealtimeAuthorizationPolicy` in `RealtimeAuthorizationPolicyTest`, `RealtimeWebSocketAutoConfigurationTest`, and `SpringRealtimeHandshakeInterceptorTest`; that is the likely source of the compile warning.
  - The forced test run also emitted an OpenJDK VM warning about bootstrap classpath sharing after dynamic agent attachment. This appears test-runtime/tooling related, not a websocket contract issue.

## 3. What Improved
- `SpringRealtimeConnectionManager` no longer contains the older lower-arity lifecycle overloads. The remaining production lifecycle methods are the current package-private `connect(...)`, `disconnect(...)`, and `transportErrorCleanup(...)` paths used by `SpringRealtimeLifecycleAdapter`.
- `SpringRealtimeConnectionManager` cleanup is phase-aware and reports callback/close failures through `OnCleanupFailed`, while continuing later cleanup phases. Manager tests cover replacement, disconnect, and transport phase continuation, including close failures.
- `SpringRealtimeLifecycleAdapter` now routes replacement, disconnect, and transport cleanup through the manager and reports cleanup failures to `observer.onInternalError(...)` using `REPLACEMENT_CLEANUP_FAILED`, `DISCONNECT_CLEANUP_FAILED`, and `TRANSPORT_ERROR_CLEANUP_FAILED`.
- Adapter real-path tests now exist for replacement cleanup failure from subscription cleanup, observer failure during replacement disconnect notification, observer failure during disconnect cleanup, and observer failure during transport cleanup.
- `CompositeRealtimeObserver` no longer suppresses delegate failures silently in production code: delegate exceptions are logged at warn level and later delegates continue.
- Subscribe authorization is aligned around subscribable destinations. `DefaultRealtimeInboundFrameHandler` rejects non-subscribable destinations before policy evaluation; `DevelopmentOnlyAllowAllRealtimeAuthorizationPolicy` calls `destination.requireSubscribable()`.
- Authorization tests include `SESSION` rejection through `RealtimeAuthorizationPolicyTest.allowAllPolicy_rejects_direct_subscribe_destinations`, and inbound tests reject `GLOBAL`, `USER`, and `SESSION` subscribe destinations.
- The allow-all policy Javadoc now matches runtime behavior: it permits connections and valid `CHANNEL` subscriptions, and rejects direct destinations.
- Dependency boundary remains clean at code/import level: `common-websocket/build.gradle` declares only `api project(':common:common-event-contract')` as a common project dependency, and websocket source imports from the shared contract are limited to `EventEnvelope` in production and `EventEnvelope`/`EventMetadata` in tests.

## 4. Remaining Problems
High
- `common/common-websocket/src/test/java/com/example/common/websocket/observer/RealtimeObserverTest.java` / missing `CompositeRealtimeObserver` tests
  - Why it is still a problem: `CompositeRealtimeObserver` production code logs delegate failures and continues, but the test suite does not test `CompositeRealtimeObserver` at all. The only observer test covers no-op and logging observers.
  - Impact: the Round 7 requirement says tests must prove both observability and continuation. That proof is absent, so a future change could silently remove the warn log or break later-delegate delivery while tests still pass.
  - Recommended fix: add focused `CompositeRealtimeObserver` tests that use a throwing first delegate and a later delegate, assert later delegate invocation, and capture/assert the warn log.

- `common/common-websocket/src/test/java/com/example/common/websocket/adapter/spring/SpringRealtimeLifecycleAdapterTest.java`
  - Why it is still a problem: adapter real-path tests do not prove socket close failure reporting through the final adapter path. Close failures are covered only in `SpringRealtimeConnectionManagerTest`; adapter tests cover subscription cleanup failure and observer failure continuation, but not replacement socket close failure, disconnect socket close failure, or transport socket close failure.
  - Impact: the final public lifecycle path could stop reporting close failures to `observer.onInternalError(...)` and the current adapter tests would not catch it. This leaves the cleanup observability claim partly proven only by manager internals.
  - Recommended fix: add real `SpringRealtimeLifecycleAdapter` path tests where `WebSocketSession.close()` throws for replacement, normal disconnect, and transport error, then assert the corresponding adapter `onInternalError` context and that cleanup continues/removal semantics remain correct.

Medium
- `common/common-websocket/src/test/java/com/example/common/websocket/adapter/spring/SpringRealtimeConnectionManagerTest.java`
  - Why it is still a problem: the suite still normalizes direct raw manager lifecycle usage. It contains `legacy_disconnect_cleanup_remains_phase_routed()` and many direct calls to package-private `connect(...)`, `disconnect(...)`, and `transportErrorCleanup(...)` with raw cleanup callbacks.
  - Impact: although the old lower-arity overloads are gone, tests still anchor lifecycle behavior at the raw manager entry points rather than proving only the final adapter contract. This weakens the claim that only the final standard lifecycle path remains.
  - Recommended fix: rename/remove legacy-framed tests and shift lifecycle-observability assertions to `SpringRealtimeLifecycleAdapterTest`. Keep manager tests narrowly focused on atomic map/lock behavior and guarded phase execution only where that internal contract is intentionally retained.

- `common/common-websocket/src/main/java/com/example/common/websocket/adapter/spring/SpringRealtimeConnectionManager.java`
  - Why it is still a problem: package-private lifecycle entry points remain production code: `connect(...)`, `disconnect(...)`, and `transportErrorCleanup(...)`. This is understandable for adapter composition, but it means the lifecycle is not exclusively represented by the public adapter path inside the package.
  - Impact: future code in the same package can still bypass adapter-level normalization and observer reporting unless discipline is maintained by tests and review.
  - Recommended fix: if these methods must remain package-private, add explicit guard tests/documentation that same-package production callers are limited to the adapter/message sender design. Prefer moving final lifecycle proof to adapter tests so package-private methods are not treated as a supported lifecycle surface.

- `common/common-websocket` test compilation
  - Why it is still a problem: deprecated API warnings still appear during a forced test compile. The likely allowed-scope source is test usage of deprecated `DevelopmentOnlyAllowAllRealtimeAuthorizationPolicy`.
  - Impact: validation is not warning-clean, and warnings can hide new deprecated API usage during freeze.
  - Recommended fix: either suppress deprecation at the narrow test sites that intentionally exercise the development-only policy or replace those test fixtures with local non-deprecated test policies where the deprecated class itself is not under test.

Low
- `common/common-websocket/src/main/java/com/example/common/websocket/adapter/spring/SpringRealtimeConnectionManager.java`
  - Why it is still a problem: production comments still say `Legacy overload removed...` near the current lifecycle methods.
  - Impact: this is stale freeze-patch wording in production source. It is not a behavior bug, but it keeps legacy lifecycle language alive in the class being frozen.
  - Recommended fix: remove the legacy-removal comments or replace them with stable internal API notes.

- `common/common-websocket/src/main/java/com/example/common/websocket/observer/RealtimeObserver.java`
  - Why it is still a problem: Javadoc references `{@link SpringRealtimeLifecycleAdapter}` without importing or fully qualifying the type from `com.example.common.websocket.adapter.spring`.
  - Impact: generated Javadocs may have an unresolved link in a core observer contract.
  - Recommended fix: fully qualify the Javadoc link or avoid linking to the adapter from the core observer contract.

- `common/common-event-contract/src/main/java/com/example/common/event/EventEnvelope.java`
  - Why it is still a problem: class Javadoc opens a `<p>` paragraph for the `Combines...` text and starts another `<p>` without an explicit close.
  - Impact: minor generated-documentation polish debt in the direct shared contract boundary.
  - Recommended fix: close the first paragraph explicitly.

## 5. Regression / Risk Check
- The highest regression risk is observability drift. `CompositeRealtimeObserver` production behavior is improved, but no test proves the warn log or continuation behavior.
- Cleanup reporting can still regress specifically at the final adapter path. Manager unit tests prove phase-level behavior, but adapter tests do not yet prove socket close failure reporting for replacement, disconnect, or transport cleanup.
- Tests still partly mask lifecycle surface ambiguity by exercising package-private manager lifecycle methods directly. This makes it easier for internal manager behavior to be mistaken for the final standard contract.
- Authorization alignment is in good shape: subscribe is `CHANNEL`-only, direct destinations are rejected before policy evaluation, and `SESSION` rejection is covered.
- Dependency boundary is clean in code and Gradle metadata. The only shared common project dependency remains `common-event-contract`; no service/gateway/kafka/redis/frontend coupling was found in websocket source imports or `build.gradle`.

## 6. Freeze Readiness
NO
- Blocker: missing `CompositeRealtimeObserver` tests proving delegate failure observability and later-delegate continuation.
- Blocker: missing `SpringRealtimeLifecycleAdapter` real-path tests proving socket close failure reporting for replacement cleanup, disconnect cleanup, and transport cleanup.
- Blocker: lifecycle tests still normalize package-private raw manager lifecycle usage, including a test explicitly named `legacy_disconnect_cleanup_remains_phase_routed`.

## 7. Final Recommendation
- Add focused `CompositeRealtimeObserver` tests for warn-log observability and continuation.
- Add adapter real-path cleanup tests for replacement, disconnect, and transport socket close failures.
- Remove or rename legacy-framed manager lifecycle tests and move final lifecycle assertions to the adapter test suite.
- Make test compilation warning-clean inside `common-websocket`, especially around intentional usage of deprecated `DevelopmentOnlyAllowAllRealtimeAuthorizationPolicy`.
- Clean the small Javadoc polish issues in `RealtimeObserver`, `SpringRealtimeConnectionManager`, and `EventEnvelope`.
