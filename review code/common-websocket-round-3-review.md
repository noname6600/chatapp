# Common WebSocket Round 3 Review

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
- `common-websocket` test packages under `com.example.common.websocket.adapter.spring`, `auth`, `codec/frame/error`, `config`, `guard`, `identity`, `inbound`, `observer`, `sender`, `session`, and `subscription`
- Direct shared contract dependency because `common-websocket` has `api project(':common:common-event-contract')`: `chatappBE/common/common-event-contract/build.gradle`, `EventEnvelope`, `EventMetadata`
- Boundary check: no main-source dependency from `common-websocket` to `common-kafka`, `common-redis`, service modules, gateway/business/domain logic, or service producers/consumers was reviewed or found.

## 2. Build/Test Validation
- Command run from `chatappBE`: `.\gradlew :common:common-websocket:compileJava --no-daemon`
- Main-source compile status: PASS. `:common:common-event-contract:compileJava` and `:common:common-websocket:compileJava` completed successfully.
- Main-source compile failures: None.
- Command run from `chatappBE`: `.\gradlew :common:common-websocket:compileTestJava --no-daemon`
- Test compile status: PASS. `:common:common-websocket:compileTestJava` completed successfully.
- Test compile failures: None.
- Command run from `chatappBE`: `.\gradlew :common:common-websocket:test --no-daemon`
- Runtime test status: PASS, but Gradle reported the test task `UP-TO-DATE`.
- Supplemental command run from `chatappBE`: `.\gradlew :common:common-websocket:test --no-daemon --rerun-tasks`
- Runtime test status after forced execution: PASS. Build successful with 6 executed tasks.
- Runtime test failures: None.
- Non-failing note from the forced run: Gradle/Javac printed deprecation notes, likely from tests exercising the deprecated development-only allow-all policy.

## 3. What Improved
- The previous stale test compile blockers are fixed. Tests now call `handleRawFrame(String sessionId, String rawPayload)` instead of the removed `handleRawFrame(sessionId, principal, rawPayload)` shape.
- Inbound tests now seed the principal through `RealtimeSessionRegistry` / `RealtimeSession` setup. `DefaultRealtimeInboundFrameHandler` derives the principal from the registered session only.
- `RealtimeDestination.user(UUID)` is now the active public user-destination factory, and `RealtimeDestination` validates USER identifiers as UUIDs when deserialized from the generic record constructor.
- Channel-only subscription semantics are enforced in both places that matter for registration: `DefaultRealtimeInboundFrameHandler` rejects non-subscribable destinations before authorization/registry mutation, and `InMemoryRealtimeSubscriptionRegistry` calls `destination.requireSubscribable()` for subscribe and unsubscribe.
- `RealtimeSubscription` also enforces `destination.requireSubscribable()`, so direct destinations cannot be smuggled in through record construction.
- Broadcaster routing and registry semantics now agree: `DefaultRealtimeBroadcaster` routes `GLOBAL`, `USER`, and `SESSION` directly, while `CHANNEL` delivery uses `RealtimeSubscriptionRegistry.listByDestination(...)`.
- Authorization is now actively typed-decision based. `RealtimeAuthorizationPolicy.allowConnect(...)` and `allowSubscribe(...)` return `RealtimeAuthorizationDecision`, and inbound subscription denial uses `reasonCode()` for observer reporting plus `clientMessage()` for the error frame.
- Principal kinds are explicit as `USER`, `ANONYMOUS`, and `SYSTEM`. `RealtimeIdentity` enforces USER requires a UUID, non-USER identities cannot carry a user UUID, and anonymous identities cannot carry authorities.
- Anonymous/system subscribe no longer turns into a null-user registry error. `DefaultRealtimeInboundFrameHandler` rejects non-USER or null-user principals before calling `subscribeIfAbsent(...)`.
- User routing is UUID-only across `RealtimePrincipal`, `RealtimeIdentity`, `RealtimeDestination.user(UUID)`, `RealtimeSubscription`, `RealtimeSubscriptionRegistry`, `RealtimeSessionRegistry`, `RealtimeBroadcaster`, and `DefaultRealtimeBroadcaster`.
- Handshake/session identity drift is mostly fixed. `SpringRealtimeSession` reads the handshake-resolved principal from `SpringRealtimeHandshakeInterceptor.ATTR_PRINCIPAL`, compares it with the Spring session principal when both exist, prefers the handshake principal, and fails closed when all identity sources are missing or when both identities mismatch.
- `SpringRealtimeSession.attributes()` no longer exposes Spring handshake/session attributes. It returns an immutable empty common-owned map, and tests assert the sanitized behavior.
- Auto-configuration is clearer: a no-op observer is auto-configured by default, authorization is not auto-configured, inbound/lifecycle beans require an explicit `RealtimeAuthorizationPolicy`, and handshake beans require token resolver, identity resolver, authorization policy, and observer prerequisites.
- Logging/Micrometer observers are manual beans. `MicrometerRealtimeObserver` now documents that it is not auto-registered and should be provided by services, typically through `CompositeRealtimeObserver`.
- Obsolete websocket DTO/handshake/session wrapper classes are guarded by tests as absent, and no direct Kafka/Redis implementation dependency is present in the websocket module.

## 4. Remaining Problems
### High
- Exact file/class: `SpringRealtimeConnectionManager.connect(...)` and `SpringRealtimeLifecycleAdapter.onConnected(...)`
- Why it is still a problem: Replacement cleanup is still not failure-safe. `connect(...)` commits the new physical session and runs `commonMutation`, then calls `onPreviousReplaced.run()` before closing the old physical session. In the adapter that callback performs `subscriptionRegistry.cleanupSession(sessionId)` and observer reporting. If cleanup throws, the new physical session and common session registry have already been committed, the old physical session is not closed, old subscriptions can remain under the same `sessionId`, and `observer.onConnected(realtimeSession)` is never emitted.
- Impact: A failed replacement cleanup can make the new session inherit stale subscriptions from the replaced connection while the replaced socket remains open outside the manager's current-session map. Lifecycle observers can also miss the new connection event even though the state changed.
- Recommended fix: Make replacement cleanup transactional or explicitly contained. At minimum, close the previous session in a `finally`, isolate subscription cleanup from observer callbacks, report cleanup failure through `onInternalError`, and either roll back the new physical/common registration or mark the replacement failed if old subscription cleanup cannot be completed. Add a test where `onPreviousReplaced` / `subscriptionRegistry.cleanupSession(...)` throws during replacement.

- Exact file/class: `SpringRealtimeConnectionManager.disconnect(...)` and `SpringRealtimeLifecycleAdapter.onDisconnected(...)` / `onTransportError(...)`
- Why it is still a problem: `disconnect(...)` removes the physical Spring session first and then runs one combined cleanup callback. If `sessionRegistry.unregister(sessionId)` throws, `subscriptionRegistry.cleanupSession(sessionId)` and `observer.onDisconnected(...)` are skipped. The manager catches and logs the exception, returns `true`, and the physical session is gone from the manager while common registry/subscription state may remain.
- Impact: Physical Spring session state and common registry state can still diverge on callback failure. Observers may not receive a truthful disconnect event, and stale subscriptions can remain addressable after the socket is no longer current.
- Recommended fix: Split disconnect cleanup into separately guarded steps, guarantee best-effort subscription cleanup even when session unregister fails, emit an internal-error observer event with the failed cleanup phase, and only emit `onDisconnected` after state is consistent enough to report. Add tests for unregister failure, subscription cleanup failure, and observer failure in disconnect and transport-error paths.

### Medium
- Exact file/class: `RealtimeSession` and `SpringRealtimeSession`
- Why it is still a problem: The Spring adapter no longer leaks handshake attributes, but the public `RealtimeSession.attributes()` contract still exposes an arbitrary `Map<String, Object>` described as connection-scoped handshake/lifecycle attributes. There is no explicit whitelist or typed common-owned metadata model.
- Impact: The current Spring implementation is clean, but freezing this API blesses an unstructured attribute bag in the generic session contract. Future adapters or services can reintroduce adapter-owned/session-handshake leakage while still satisfying the interface.
- Recommended fix: Remove `attributes()` from `RealtimeSession` before freeze, or replace it with a typed/whitelisted common-owned metadata view with documented keys and invariants.

- Exact file/class: `RealtimeAuthorizationPolicy`, `RealtimeAuthorizationDecision`, `DevelopmentOnlyAllowAllRealtimeAuthorizationPolicy`, `RealtimeAuthorizationPolicyTest`
- Why it is still a problem: The active auth contract is typed, but the interface Javadocs still say `@return true to allow` / `false to deny`, and `RealtimeAuthorizationDecision.fromBoolean(...)` is documented as backward compatibility even though the boolean policy API is gone. The development allow-all policy and its test still treat direct destinations like `GLOBAL` and `USER` as valid `allowSubscribe(...)` inputs even though the chosen subscription model is `CHANNEL`.
- Impact: The code executes as typed-decision based, but the frozen contract would carry stale boolean wording and a weak policy test that normalizes invalid subscription destinations.
- Recommended fix: Update auth Javadocs to describe `RealtimeAuthorizationDecision`, remove or clearly justify `fromBoolean(...)`, and make policy tests use `CHANNEL` destinations only or document that `allowSubscribe(...)` is invoked after destination-shape validation.

- Exact file/class: `SpringRealtimeHandshakeInterceptor`
- Why it is still a problem: Policy denial during handshake uses `RealtimeAuthorizationDecision.reasonCode()` for observer reporting, but `clientMessage()` is not surfaced or documented as unavailable during handshake. The method simply returns `false` without setting an explicit response status/body or otherwise standardizing client-facing handshake denial behavior.
- Impact: Subscribe denial has standardized client-facing error frames, while handshake denial only has observer reason codes. Services may assume `clientMessage()` is consistently exposed for all authorization decisions when it is not.
- Recommended fix: Either document that handshake denial is observer-only and cannot emit realtime error frames, or set an explicit HTTP status/body policy for rejected handshakes if the common module is meant to standardize client-facing handshake errors.

### Low
- Exact file/class: `CookieRealtimeHandshakeTokenResolver`
- Why it is still a problem: The resolver uses reflection against `ServletServerHttpRequest.getServletRequest().getCookies()` and carries a stale comment saying this avoids compile errors if the servlet API is absent. The module already imports servlet-backed Spring request types elsewhere and has query-param servlet tests. The cookie tests cover header parsing, but not the servlet-cookie branch.
- Impact: Runtime behavior currently passes tests, but the production-preferred cookie path is rougher and less directly guarded than the header fallback.
- Recommended fix: Use direct servlet cookie access if servlet APIs are part of the supported adapter surface, or document the non-servlet reason for reflection. Add a servlet-cookie test and a blank servlet-cookie-value test.

- Exact file/class: `RealtimeObserver`, `CompositeRealtimeObserver`, adapter `safeObserver(...)` methods
- Why it is still a problem: `RealtimeObserver` says observer errors are "silently swallowed"; `CompositeRealtimeObserver` truly suppresses them, while adapter `safeObserver(...)` methods log warnings. The runtime behavior is acceptable, but the wording is not exact.
- Impact: Low operational risk, but not freeze-polish accurate.
- Recommended fix: Change the Javadoc to "must not propagate" and mention adapters may log observer failures while composites suppress delegate failures.

- Exact file/class: `InMemoryRealtimeSubscriptionRegistryTest`, `RealtimeIdentityInvariantTest`, `SpringRealtimeHandshakeInterceptorTest`, `QueryParamRealtimeHandshakeTokenResolver`
- Why it is still a problem: Tests and comments are functionally current but still have freeze-polish rough edges: inconsistent indentation around test methods, a structural logging test with slightly stale narrative wording, and explicit "legacy clients" compatibility wording around query-parameter tokens.
- Impact: No runtime impact, but the source is not fully freeze-clean.
- Recommended fix: Normalize indentation/comments and either keep the query-param resolver as explicitly development-only or remove compatibility wording if legacy support is not part of the frozen standard.

## 5. Regression / Risk Check
- The latest refactor fixed the prior compile/test blockers. `compileJava`, `compileTestJava`, the requested test command, and a forced runtime test rerun all pass.
- The test suite is materially fresher: it no longer calls the removed three-argument inbound API, and it now uses registry-seeded principals for inbound authorization flow.
- The API is now UUID-only for user routing. That is internally consistent, but it is an API compatibility point for services that previously treated user destinations as arbitrary strings.
- Channel-only subscriptions are now enforced consistently in inbound handling, subscription records, and the in-memory registry. The remaining risk is policy/test wording, not registration behavior.
- The lifecycle refactor improved rollback for `commonMutation` failures, but replacement/disconnect cleanup callbacks still have failure windows that can leave physical and common state diverged.
- The session abstraction is cleaner at runtime because Spring attributes are sanitized to an empty map, but the public `RealtimeSession.attributes()` API still leaves a future leakage path.
- `common-websocket` still directly depends only on the minimal `common-event-contract` for `RealtimeEventFrame` payloads. No direct Kafka/Redis producer/consumer dependency was found in main source.

## 6. Freeze Readiness
- Answer: NO
- Exact remaining blockers inside `common`:
- `SpringRealtimeConnectionManager` / `SpringRealtimeLifecycleAdapter` replacement cleanup can throw after new physical/common state is committed, leaving stale subscriptions, an unclosed replaced socket, and incomplete observer events.
- `SpringRealtimeConnectionManager` / `SpringRealtimeLifecycleAdapter` disconnect cleanup can remove the physical session while leaving common session/subscription state stale when registry cleanup throws.
- `RealtimeSession.attributes()` remains an arbitrary unwhitelisted map in the frozen public contract, even though the Spring implementation currently returns an empty sanitized map.
- Auth contract polish is not freeze-clean: typed decisions are active, but boolean-return Javadocs and backward-compatibility wording remain, and the allow-all policy tests still authorize direct destinations as subscribe inputs.

## 7. Final Recommendation
- `common-websocket` should not be frozen before touching services yet.
- The next best step is a common-only stabilization pass focused on lifecycle failure compensation first: harden replacement and disconnect cleanup mutation paths, add tests for cleanup exceptions, then remove or whitelist `RealtimeSession.attributes()` and clean the auth Javadocs/tests.
- After that, rerun `.\gradlew :common:common-websocket:test --no-daemon --rerun-tasks` and do one last strict freeze check before service migration depends on this module.
