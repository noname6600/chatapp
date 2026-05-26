# Common WebSocket Round 5 Review

## 1. Scope Reviewed
- `chatappBE/common/common-websocket/build.gradle`
- `chatappBE/common/common-websocket/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- `com.example.common.websocket.adapter.spring`: `CookieRealtimeHandshakeTokenResolver`, `HeaderRealtimeHandshakeTokenResolver`, `QueryParamRealtimeHandshakeTokenResolver`, `RealtimeHandshakeTokenResolver`, `SpringRealtimeConnectionManager`, `SpringRealtimeHandshakeHandler`, `SpringRealtimeHandshakeInterceptor`, `SpringRealtimeLifecycleAdapter`, `SpringRealtimeMessageSender`, `SpringRealtimeSession`, `SpringRealtimeTextWebSocketHandler`
- `com.example.common.websocket.auth`: `DevelopmentOnlyAllowAllRealtimeAuthorizationPolicy`, `RealtimeAuthorizationDecision`, `RealtimeAuthorizationPolicy`, `RealtimeIdentityResolver`
- `com.example.common.websocket.codec`: `JsonRealtimeFrameCodec`, `RealtimeCodecException`, `RealtimeFrameCodec`
- `com.example.common.websocket.config`: `RealtimeWebSocketAutoConfiguration`
- `com.example.common.websocket.error`: `RealtimeError`, `RealtimeErrorCode`, `RealtimeSendFailureReason`
- `com.example.common.websocket.frame`: `RealtimeCommandFrame`, `RealtimeCommandType`, `RealtimeErrorFrame`, `RealtimeEventFrame`, `RealtimeFrame`
- `com.example.common.websocket.identity`: `RealtimeIdentity`, `RealtimePrincipal`, `RealtimePrincipalKind`
- `com.example.common.websocket.inbound`: `DefaultRealtimeInboundFrameHandler`, `RealtimeInboundFrameHandler`
- `com.example.common.websocket.observer`: `CompositeRealtimeObserver`, `LoggingRealtimeObserver`, `MicrometerRealtimeObserver`, `NoOpRealtimeObserver`, `RealtimeObserver`
- `com.example.common.websocket.sender`: `DefaultRealtimeBroadcaster`, `RealtimeBroadcaster`, `RealtimeMessageSender`, `RealtimeSendResult`
- `com.example.common.websocket.session`: `InMemoryRealtimeSessionRegistry`, `RealtimeSession`, `RealtimeSessionRegistry`
- `com.example.common.websocket.subscription`: `InMemoryRealtimeSubscriptionRegistry`, `RealtimeDestination`, `RealtimeDestinationType`, `RealtimeSubscription`, `RealtimeSubscriptionRegistry`
- `common-websocket` tests under `adapter.spring`, `auth`, `config`, `error`, `frame`, `guard`, `identity`, `inbound`, `observer`, `sender`, `session`, and `subscription`
- Direct shared contract dependency because `common-websocket` still has `api project(':common:common-event-contract')`: `chatappBE/common/common-event-contract/build.gradle`, `EventEnvelope`, `EventMetadata`
- Boundary check: no service module, gateway/business/domain logic, Kafka producer/consumer, Redis producer/consumer, or other code outside the strict common websocket/event-contract scope was reviewed.

## 2. Build/Test Validation
- Command run from `chatappBE`: `.\gradlew.bat :common:common-websocket:compileJava --no-daemon`
- Main compile status: PASS. `:common:common-event-contract:compileJava` and `:common:common-websocket:compileJava` completed successfully.
- Main-source compile failures: None.
- Command run from `chatappBE`: `.\gradlew.bat :common:common-websocket:compileTestJava --no-daemon`
- Test compile status: PASS. `:common:common-websocket:compileTestJava` completed successfully.
- Test compile failures: None.
- Command run from `chatappBE`: `.\gradlew.bat :common:common-websocket:test --no-daemon --rerun-tasks`
- Runtime test status: PASS. Gradle reported `BUILD SUCCESSFUL`.
- Runtime test result files: 164 tests, 0 failures, 0 errors, 0 skipped.
- Runtime test failures: None.
- Non-failing notes: the runtime test command emitted javac deprecation notes, consistent with tests still using the deprecated development-only allow-all authorization policy.

## 3. What Improved
- `CookieRealtimeHandshakeTokenResolver` now directly handles `ServletServerHttpRequest` cookies through `jakarta.servlet.http.Cookie` instead of reflection, and falls back to raw `Cookie` header parsing only for non-servlet requests.
- Cookie resolver tests now cover servlet cookie extraction, blank servlet cookie values, raw header parsing, blank raw header cookie values, missing cookies, malformed headers, and non-servlet fallback.
- `SpringRealtimeConnectionManager.connect(...)` now has a phase-aware replacement cleanup path for subscription cleanup, replaced-session observer notification, and socket close.
- Replacement socket close is now an explicit `REPLACEMENT_SOCKET_CLOSE_FAILED` phase in the new manager path, and `SpringRealtimeConnectionManagerTest.replacement_cleanup_reports_each_phase_failure` covers subscription, observer, and socket-close failures in one replacement.
- Normal disconnect cleanup now has independently guarded session unregister, subscription cleanup, observer notification, and socket close phases in the new 7-argument `disconnect(...)`.
- Transport-error cleanup now has independently guarded session unregister, subscription cleanup, observer notification, and socket close phases in `transportErrorCleanup(...)`.
- `closeSessionPhase(...)` reports socket close failures with phase-specific `PhaseCleanupException` values and logs the close failure.
- `SpringRealtimeLifecycleAdapter.onConnected(...)` calls the new replacement cleanup signature and still emits `observer.onConnected(newSession)` after the new-session commit path returns.
- `SpringRealtimeLifecycleAdapter.onDisconnected(...)` and `onTransportError(...)` now route through the phase-aware manager cleanup methods in the standard adapter path.
- `DefaultRealtimeInboundFrameHandler` rejects direct `GLOBAL`, `USER`, and `SESSION` destinations before subscribe authorization and registry mutation; subscription registry writes also enforce `requireSubscribable()`.
- `common-websocket` still directly depends only on `common-event-contract` for `RealtimeEventFrame` payloads. No direct `common-kafka`, `common-redis`, or service dependency was found in the reviewed websocket module.

## 4. Remaining Problems
### High
- Exact file/class: `SpringRealtimeConnectionManager`, specifically the package-private legacy overloads `connect(String, WebSocketSession, Runnable, Runnable)`, `disconnect(String, WebSocketSession, Runnable, boolean)`, and `closeSessionBestEffort(...)`.
- Why it is still a problem: the new standard adapter path uses phase-aware cleanup, but the old overloads remain in main source and still ignore cleanup reporting. The 4-argument `connect(...)` passes `null` for `onReplacementCleanupFailed`, so replacement cleanup callback failures are swallowed. The 4-argument `disconnect(...)` still routes socket close through `closeSessionBestEffort(...)`, whose catch block ignores `WebSocketSession.close()` failures with no phase report and no log.
- Impact: the explicit round-5 close-failure objective is not fully true while `closeSessionBestEffort(...)` remains. Even though services cannot call these package-private methods directly, this is still production main code inside the lifecycle owner and tests continue to exercise it, leaving a maintenance trap where future internal/common usage can silently lose cleanup failures.
- Recommended fix: remove the legacy overloads if they are only test scaffolding, or route them through the same phase-aware cleanup helper with explicit logging/reporting. Delete `closeSessionBestEffort(...)` or make it delegate to `closeSessionPhase(...)` with a concrete phase and reporter/log path.

### Medium
- Exact file/class: `SpringRealtimeConnectionManager` replacement, disconnect, and transport cleanup reporting callbacks.
- Why it is still a problem: cleanup-failure callback failures are caught but silently ignored in most non-close phases (`catch (Exception ignore) {}`). `closeSessionPhase(...)` logs callback failure, but replacement subscription/observer, disconnect unregister/subscription/observer, and transport unregister/subscription/observer do not.
- Impact: cleanup flow itself does continue, which is good, but the required "cleanup-failure callback failure" behavior is not observable or tested consistently. A failing reporter can erase the only phase report for replacement cleanup failures.
- Recommended fix: centralize cleanup-failure reporting in a helper like `reportCleanupFailure(sessionId, phase, cause, reporter)` that logs callback failure for every phase, and add tests proving reporter failure does not stop later cleanup phases.

- Exact file/class: `SpringRealtimeLifecycleAdapter` and `SpringRealtimeLifecycleAdapterTest`.
- Why it is still a problem: adapter tests verify that the new manager signatures are invoked, but they do not exercise standard-path cleanup failures with real adapter callbacks. There is no adapter-level test proving replacement subscription cleanup failure still leads to replaced-session observer notification, socket close attempt, and `observer.onConnected(newSession)`. There is also no adapter-level test for actual `observer.onDisconnected(...)` throwing during disconnect or transport cleanup.
- Impact: the manager unit tests prove the lower-level runnable sequencing, but the standard lifecycle integration remains under-tested. Because the adapter wraps observer callbacks in `safeObserver(...)`, actual observer failures become generic `OBSERVER_EXCEPTION` logs rather than `DISCONNECT_OBSERVER_NOTIFY_FAILED`, `TRANSPORT_OBSERVER_NOTIFY_FAILED`, or replacement observer phase reports.
- Recommended fix: add adapter integration tests with throwing session/subscription registries and throwing observers. Either pass raw observer notification into the manager so the manager can phase-report observer failures, or explicitly document/test that observer callback failures are logged-only and not cleanup-failure events.

- Exact file/class: `RealtimeAuthorizationPolicy`, `DevelopmentOnlyAllowAllRealtimeAuthorizationPolicy`, and `RealtimeAuthorizationPolicyTest`.
- Why it is still a problem: the policy Javadoc says `allowSubscribe(...)` receives a subscribable destination, i.e. `CHANNEL`, but `RealtimeAuthorizationPolicyTest.allowAllPolicy_allowsSubscribe_for_any_destination` still asserts that `GLOBAL` and `USER` are valid subscribe authorization inputs. `DevelopmentOnlyAllowAllRealtimeAuthorizationPolicy.allowSubscribe(...)` also accepts direct destinations without requiring `destination.requireSubscribable()`.
- Impact: runtime inbound handling rejects direct subscribe destinations before authorization, but the frozen auth contract/tests still normalize direct destinations as valid subscribe inputs. That weakens the chosen `CHANNEL`-only subscription model.
- Recommended fix: change authorization tests to use only `RealtimeDestination.channel(...)` for subscribe authorization. If policy validation is intended to be pre-validated by inbound code, document that explicitly in `RealtimeAuthorizationPolicy`; otherwise enforce `destination.requireSubscribable()` in `DevelopmentOnlyAllowAllRealtimeAuthorizationPolicy`.

- Exact file/class: `RealtimeAuthorizationDecision` and `RealtimeAuthorizationPolicy`.
- Why it is still a problem: typed decisions are implemented, but some Javadocs still read like migration notes rather than final contract language, especially `RealtimeAuthorizationDecision` saying it "Replaces boolean-only authorization responses".
- Impact: no runtime issue, but this is stale boolean-era wording in a contract that is being frozen as the standard API.
- Recommended fix: rewrite the comments as final-state contract language: "Authorization result containing the allow/deny outcome, machine-readable reason code, and safe client message."

- Exact file/class: `SpringRealtimeConnectionManager` Javadocs around `connect(...)`.
- Why it is still a problem: there are two adjacent Javadoc blocks for the same method. The first still references `onPreviousReplaced`, "old socket is closed in a finally block", and generic observer `INTERNAL_ERROR` reporting, which no longer matches the new phase-aware signature.
- Impact: the most important lifecycle method still has misleading lifecycle wording at freeze time. It blurs what is committed, what is best-effort, what is reported, and what is not rolled back.
- Recommended fix: replace the duplicate Javadocs with a single final contract that states: map/common session mutation commit and rollback behavior, replacement cleanup is best-effort after commit, cleanup failures are phase-reported, and cleanup failures do not roll back the new session.

### Low
- Exact file/class: `SpringRealtimeConnectionManagerTest`.
- Why it is still a problem: the new cleanup tests are badly indented and visually nested even though they compile.
- Impact: no behavioral risk, but the tests do not look freeze-quality and are harder to scan in exactly the area future maintainers will inspect first.
- Recommended fix: normalize indentation and keep the phase-failure assertions readable.

- Exact file/class: `SpringRealtimeMessageSenderTest`.
- Why it is still a problem: `RealtimeSubscriptionRegistry` and `BeforeEach` imports are on the same line.
- Impact: compile still passes, but this is obvious freeze-polish debt.
- Recommended fix: split the imports and run formatter.

- Exact file/class: `RealtimeObserver`, `RealtimeObserverTest`, and several test Javadocs/comments.
- Why it is still a problem: mojibake appears in comments such as `â€”`, and `RealtimeObserver` still says observer errors are "silently swallowed" even though adapter `safeObserver(...)` logs generic observer failures while `CompositeRealtimeObserver` suppresses delegate failures.
- Impact: runtime behavior is acceptable, but the frozen contract wording is not precise.
- Recommended fix: replace mojibake with ASCII punctuation and clarify that observer failures must not propagate; adapters may log them and composites may suppress delegate exceptions.

- Exact file/class: `SpringRealtimeHandshakeHandler`.
- Why it is still a problem: the class Javadoc has a stray closing `</p>` with no matching paragraph tag.
- Impact: no runtime issue, but it is stale Javadoc polish.
- Recommended fix: remove the stray closing tag.

## 5. Regression / Risk Check
- The latest refactor materially improved the standard lifecycle path: replacement, normal disconnect, and transport-error cleanup are now phase-aware in the main adapter flow.
- The new behavior is committed-with-best-effort-cleanup: once the new session map/common registry mutation succeeds, replacement cleanup failures are reported but do not roll back the new session. That is acceptable only if the final Javadocs say it plainly.
- The largest regression risk is the remaining split between new phase-aware methods and old package-private overloads. Tests still use the old overloads, so future common-only work can accidentally exercise silent cleanup behavior while the standard adapter appears fixed.
- Observer failure semantics are still a little muddy: manager tests can report observer-phase failures only when the observer runnable throws, but the standard adapter wraps observer callbacks before the manager sees them.
- API compatibility concern: removing or changing package-private manager overloads should not affect service modules directly, but it will require updating common-websocket tests that currently use those overloads.
- Cookie resolver quality is now acceptable for freeze: servlet cookies are direct and tested, raw header fallback is tested, blank values return empty, and missing cookies return empty.
- No direct Kafka/Redis/service dependency was found in `common-websocket`; the remaining direct shared contract is `common-event-contract`.

## 6. Freeze Readiness
- Answer: NO
- Exact remaining blockers inside `common`:
- `SpringRealtimeConnectionManager.closeSessionBestEffort(...)` still silently swallows socket close failures through the legacy disconnect overload.
- `SpringRealtimeConnectionManager` legacy cleanup overloads remain in main source and can still swallow replacement cleanup/reporting failures.
- Cleanup-failure callback failure handling is not consistently logged/reported across all phases and is not covered by tests.
- `RealtimeAuthorizationPolicyTest` and `DevelopmentOnlyAllowAllRealtimeAuthorizationPolicy` still treat direct destinations as valid `allowSubscribe(...)` inputs despite the chosen `CHANNEL`-only subscription model.
- Lifecycle contract Javadocs in `SpringRealtimeConnectionManager` still contain stale replacement cleanup wording.

## 7. Final Recommendation
- `common-websocket` should not be frozen before touching services yet.
- The final common-only fix pass should be narrow: remove or phase-route the legacy manager cleanup overloads, delete or replace `closeSessionBestEffort(...)`, centralize cleanup-failure reporting so reporter failures are logged without breaking cleanup, add adapter-level failure tests for the real standard path, and align the authorization subscribe tests/docs with `CHANNEL`-only subscriptions.
- After that pass, freeze `common-websocket` first. Then refactor services next against the frozen common websocket API and the already-direct `common-event-contract` event frame model.
