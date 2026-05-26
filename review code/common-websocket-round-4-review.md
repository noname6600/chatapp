# Common WebSocket Round 4 Review

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
- Boundary check: no direct `common-websocket` dependency on `common-kafka`, `common-redis`, service modules, gateway/business/domain logic, or service producers/consumers was reviewed or found.

## 2. Build/Test Validation
- Command run from `chatappBE`: `.\gradlew.bat :common:common-websocket:compileJava --no-daemon --rerun-tasks`
- Main compile status: PASS. `:common:common-event-contract:compileJava` and `:common:common-websocket:compileJava` completed successfully.
- Main-source compile failures: None.
- Command run from `chatappBE`: `.\gradlew.bat :common:common-websocket:compileTestJava --no-daemon --rerun-tasks`
- Test compile status: PASS. `:common:common-websocket:compileTestJava` completed successfully.
- Test compile failures: None.
- Command run from `chatappBE`: `.\gradlew.bat :common:common-websocket:test --no-daemon --rerun-tasks`
- Runtime test status: PASS. Gradle reported `BUILD SUCCESSFUL`; parsed test results show 158 tests, 0 failures, 0 errors, 0 skipped.
- Runtime test failures: None.
- Non-failing notes: Javac/Gradle printed deprecation notes during test compilation, consistent with tests using the deprecated development-only allow-all policy.

## 3. What Improved
- `RealtimeSession.attributes()` has been removed from the public core session contract. `RealtimeSession` now exposes only `sessionId()`, `principal()`, `connectedAt()`, and `isOpen()`.
- `SpringRealtimeSession` no longer exposes Spring attributes through `RealtimeSession`; it resolves a `RealtimePrincipal` from the handshake attribute / Spring principal and fails closed on missing or mismatched identity.
- `SpringRealtimeConnectionManager.connect(...)` now rolls back the physical session map when `commonMutation` fails. On replacement, it restores the previous physical session if the common registry mutation throws.
- Replacement cleanup now closes the replaced Spring socket in a `finally` block, and `SpringRealtimeLifecycleAdapter.onConnected(...)` emits `observer.onConnected(...)` after `connect(...)` returns.
- Replacement cleanup failures are no longer silent in the standard adapter path: `SpringRealtimeLifecycleAdapter` reports `REPLACEMENT_CLEANUP_FAILED` through `observer.onInternalError(...)`.
- `SpringRealtimeConnectionManager.disconnect(...)` now has separate callbacks for session unregister, subscription cleanup, and observer disconnect notification, and stale physical sessions are CAS-rejected without removing the replacement.
- `SpringRealtimeLifecycleAdapter.onTransportError(...)` reports the transport error before running cleanup, then uses the guarded disconnect path with `TRANSPORT_ERROR_CLEANUP_FAILED` reporting.
- Race tests validate late disconnect / late transport error from an old physical session do not remove the replacement, and concurrent replacement converges to one current session.
- `DefaultRealtimeInboundFrameHandler` rejects non-`CHANNEL` destinations before authorization/registry mutation, and the in-memory subscription registry and `RealtimeSubscription` enforce `requireSubscribable()`.
- The active authorization flow uses `RealtimeAuthorizationDecision` for connect and subscribe decisions; subscribe denial uses `reasonCode()` for observer reporting and `clientMessage()` for the error frame.
- `common-websocket` still depends only on `common-event-contract` for `RealtimeEventFrame` payloads and has no direct Kafka/Redis/service coupling.

## 4. Remaining Problems
### High
- Exact file/class: `SpringRealtimeConnectionManager.closeSessionBestEffort(...)`, used by `connect(...)` replacement cleanup and `disconnect(...)` / `SpringRealtimeLifecycleAdapter.onDisconnected(...)` / `onTransportError(...)`
- Why it is still a problem: socket close is still not an independently reported cleanup phase. `closeSessionBestEffort(...)` catches and ignores close failures, so a failed close can leave the old/replaced/errored socket open with no `onInternalError(...)`, no phase-specific log, and no test signal.
- Impact: The code no longer leaves the old socket open when previous cleanup throws and `close()` succeeds, but it can still silently leave the old socket open when `close()` itself fails. That is directly in the replacement and transport cleanup failure window this round was meant to close.
- Recommended fix: Make close a named cleanup phase with failure reporting, e.g. `REPLACEMENT_SOCKET_CLOSE_FAILED`, `DISCONNECT_SOCKET_CLOSE_FAILED`, and `TRANSPORT_SOCKET_CLOSE_FAILED`. Keep it best-effort, but log/report the phase and add tests where `WebSocketSession.close()` throws.

- Exact file/class: `SpringRealtimeLifecycleAdapter.onConnected(...)`
- Why it is still a problem: replacement cleanup is committed-with-best-effort-cleanup, but the cleanup callback still combines `subscriptionRegistry.cleanupSession(sessionId)` and `observer.onDisconnected(...SESSION_REPLACED)` in one runnable. If subscription cleanup throws, the replaced-session observer notification is skipped, stale subscriptions can remain under the same `sessionId`, and only a generic `REPLACEMENT_CLEANUP_FAILED` internal error is emitted.
- Impact: New physical/common session state remains committed and `observer.onConnected(...)` is emitted, but the new session can inherit stale subscription state from the displaced connection if cleanup did not remove it. The state is no longer silent, but it is still inconsistent and the exact failed phase is not observable.
- Recommended fix: Split replacement cleanup into independently guarded phases: subscription cleanup, replaced-session observer notification, and socket close. Report phase-specific internal errors and add tests proving `onConnected(...)` still fires, the old socket close is attempted, observer disconnect is attempted even when subscription cleanup fails, and stale subscriptions are either removed or explicitly reported as unrecoverable under the chosen best-effort boundary.

- Exact file/class: `SpringRealtimeConnectionManager.disconnect(...)` and `SpringRealtimeLifecycleAdapter.onDisconnected(...)` / `onTransportError(...)`
- Why it is still a problem: unregister, subscription cleanup, and disconnect observer callbacks are independently guarded only if the cleanup-failure callback itself cannot throw. In the standard adapter this callback uses `safeObserver(...)`, but the manager contract does not guard `onCleanupFailed.onFailed(e)`. Also, observer failures are swallowed inside `safeObserver(...)`, so the manager never sees `notify-observer` failure and cannot emit `DISCONNECT_CLEANUP_FAILED` / `TRANSPORT_ERROR_CLEANUP_FAILED` for that phase.
- Impact: The standard path is much better than round 3, but phase reporting is incomplete. A failing observer only produces a generic `OBSERVER_EXCEPTION` log, and a future package-local caller could accidentally make one cleanup failure skip later phases by passing a throwing `onCleanupFailed`.
- Recommended fix: Guard `onCleanupFailed` inside the manager and include cleanup phase in the callback. For observer notification, either let the manager wrap the observer callback directly with phase-aware reporting/logging, or document that observer failures are logged-only and add tests for that explicit behavior.

### Medium
- Exact file/class: `SpringRealtimeConnectionManagerTest`, `SpringRealtimeLifecycleAdapterTest`, `SpringRealtimeLifecycleAdapterRaceTest`
- Why it is still a problem: tests do not cover the failure cases required for this review. There is no replacement cleanup failure test, no unregister failure test, no subscription cleanup failure test in disconnect/transport cleanup, no observer failure test for disconnect cleanup, and no socket-close failure test. Existing tests cover common-mutation rollback, stale-session races, and one legacy combined cleanup failure only.
- Impact: The most important lifecycle guarantees are under-tested. The current green build does not prove the remaining blocker scenarios have been fixed.
- Recommended fix: Add targeted tests for replacement cleanup failure, unregister failure, subscription cleanup failure, observer failure, transport-error cleanup failure, and close failure. Assert both final state and emitted/logged internal-error phase.

- Exact file/class: `RealtimeAuthorizationPolicy`, `RealtimeAuthorizationDecision`, `DevelopmentOnlyAllowAllRealtimeAuthorizationPolicy`, `RealtimeAuthorizationPolicyTest`
- Why it is still a problem: typed decisions are active, but the docs still lean on boolean-style wording (`Whether ... is allowed`, `allowed`, `allowSubscribe`) and `RealtimeAuthorizationDecision` still describes itself as replacing boolean-only responses. More importantly, `RealtimeAuthorizationPolicyTest.allowAllPolicy_allowsSubscribe_for_any_destination` asserts that `GLOBAL` and `USER` destinations are valid `allowSubscribe(...)` inputs even though the subscription model is explicitly `CHANNEL` only.
- Impact: Runtime inbound handling rejects direct destinations before policy evaluation, but the policy contract/test narrative still normalizes invalid subscription destination types. That is misleading for a frozen shared contract.
- Recommended fix: Update policy docs to state `allowSubscribe(...)` is only called after destination-shape validation, or enforce `destination.requireSubscribable()` in the policy. Change tests to use only `RealtimeDestination.channel(...)` for subscribe authorization, or document direct-destination behavior as deliberately out-of-contract.

- Exact file/class: `CookieRealtimeHandshakeTokenResolver` and `CookieRealtimeHandshakeTokenResolverTest`
- Why it is still a problem: the production-preferred servlet-cookie path still uses reflection even though servlet APIs are already available in the module via Spring websocket/servlet usage. The reflection fallback is only lightly documented, and tests cover raw `Cookie` header parsing, missing cookie, malformed/multiple headers, and non-servlet fallback, but not servlet cookie extraction or blank cookie values.
- Impact: The resolver works for tested header paths, but the freeze-quality production path is not directly covered. Blank cookie values are part of the requested coverage and remain untested.
- Recommended fix: Use direct `jakarta.servlet.http.Cookie` access for `ServletServerHttpRequest`, or explicitly document why reflection is intentional. Add servlet-cookie extraction and blank-value tests for both servlet and raw-header paths.

### Low
- Exact file/class: `RealtimeObserver`, `CompositeRealtimeObserver`, adapter `safeObserver(...)` methods
- Why it is still a problem: `RealtimeObserver` still says observer errors are "silently swallowed." Actual behavior is mixed: `CompositeRealtimeObserver` suppresses delegate failures, while adapters log `OBSERVER_EXCEPTION` and do not propagate.
- Impact: Runtime behavior is acceptable, but the frozen observer contract would describe failure handling inaccurately.
- Recommended fix: Replace the "silently swallowed" wording with "must not propagate." Document that composites may suppress delegate failures and adapters may log observer failures before continuing.

- Exact file/class: `DefaultRealtimeBroadcasterTest`, `InMemoryRealtimeSessionRegistryTest`
- Why it is still a problem: `RealtimeSession.attributes()` was removed from the interface, but test anonymous `RealtimeSession` implementations still define an extra `attributes()` method returning `Map.of()`.
- Impact: No compile/runtime impact, but it is stale test code around exactly the contract area that was meant to be cleaned. It leaves a small future adapter-leakage trap in examples developers may copy.
- Recommended fix: Remove the extra `attributes()` methods and now-unused `Map` imports from test helpers.

- Exact file/class: `SpringRealtimeMessageSenderTest`, `SpringRealtimeConnectionManager`, `QueryParamRealtimeHandshakeTokenResolver`, `SpringRealtimeHandshakeHandler`
- Why it is still a problem: freeze-polish rough edges remain: `SpringRealtimeMessageSenderTest` has two imports on one line, `SpringRealtimeConnectionManager` still carries package-private "Backward-compat overload" wording, `QueryParamRealtimeHandshakeTokenResolver` still says "backward compatibility with legacy clients," and `SpringRealtimeHandshakeHandler` has a stray closing `</p>` in Javadoc.
- Impact: Low runtime risk, but this is not fully freeze-quality source/test polish.
- Recommended fix: Normalize the import, remove or justify compatibility wording, and clean the Javadocs/comments.

## 5. Regression / Risk Check
- The latest refactor materially improved lifecycle behavior compared with round 3: common registry mutation rollback exists, disconnect cleanup is split into phases, old-session late disconnect/transport errors are CAS-no-ops, and replaced sockets are closed in a `finally` when close succeeds.
- The replacement lifecycle strategy is now explicitly committed-with-best-effort-cleanup after common mutation succeeds. It is not rollback-on-cleanup-failure.
- The remaining risk is the boundary of "best effort": subscription cleanup failure can still leave stale subscriptions, socket close failure is silent, and cleanup phase detail is not consistently observable.
- Removing `RealtimeSession.attributes()` is a public API break, but it is the right contract direction before freezing. The stale test helper methods should be removed so the examples match the final API.
- `RealtimeAuthorizationPolicyTest` still weakens the chosen subscription model by treating direct destinations as valid subscribe policy inputs, even though inbound/registry code rejects them.
- `common-websocket` still has an `api` dependency on `common-event-contract`; that is directly used by `RealtimeEventFrame` and remains the only directly-related shared contract dependency found.
- No direct Kafka/Redis/service dependency was found in `common-websocket`.

## 6. Freeze Readiness
- Answer: NO
- Exact remaining blockers inside `common`:
- `SpringRealtimeConnectionManager.closeSessionBestEffort(...)` silently swallows close failures in replacement, disconnect, and transport-error cleanup.
- `SpringRealtimeLifecycleAdapter.onConnected(...)` replacement cleanup is not phase-independent and can leave stale subscriptions while skipping replaced-session disconnect notification.
- Cleanup failure tests required by this review are missing for replacement cleanup, unregister failure, subscription cleanup failure, observer failure, transport-error cleanup failure, and socket close failure.
- Authorization policy tests still normalize direct destinations as valid subscribe inputs despite the chosen channel-only subscription model.
- Cookie resolver coverage is incomplete for the servlet-cookie and blank-cookie paths.

## 7. Final Recommendation
- `common-websocket` should not be frozen before touching services yet.
- The next best step is a narrow common-only stabilization pass: make replacement/disconnect/transport cleanup phase-aware, report close failures, add the missing lifecycle failure tests, then clean the authorization and cookie-resolver contract/test polish.
- After that, rerun `.\gradlew.bat :common:common-websocket:test --no-daemon --rerun-tasks` and do one final strict freeze review before service migration depends on this module.
