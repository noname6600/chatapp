# Common WebSocket Final Review

## 1. Scope Reviewed
- `chatappBE/common/common-websocket/build.gradle`
- `chatappBE/common/common-websocket/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Main package `com.example.common.websocket.adapter.spring`: `CookieRealtimeHandshakeTokenResolver`, `HeaderRealtimeHandshakeTokenResolver`, `QueryParamRealtimeHandshakeTokenResolver`, `RealtimeHandshakeTokenResolver`, `SpringRealtimeConnectionManager`, `SpringRealtimeHandshakeHandler`, `SpringRealtimeHandshakeInterceptor`, `SpringRealtimeLifecycleAdapter`, `SpringRealtimeMessageSender`, `SpringRealtimeSession`, `SpringRealtimeTextWebSocketHandler`
- Main package `com.example.common.websocket.auth`: `AllowAllRealtimeAuthorizationPolicy`, `RealtimeAuthorizationDecision`, `RealtimeAuthorizationPolicy`, `RealtimeIdentityResolver`
- Main package `com.example.common.websocket.codec`: `JsonRealtimeFrameCodec`, `RealtimeCodecException`, `RealtimeFrameCodec`
- Main package `com.example.common.websocket.config`: `RealtimeWebSocketAutoConfiguration`
- Main package `com.example.common.websocket.error`: `RealtimeError`, `RealtimeErrorCode`, `RealtimeSendFailureReason`
- Main package `com.example.common.websocket.frame`: `RealtimeCommandFrame`, `RealtimeCommandType`, `RealtimeErrorFrame`, `RealtimeEventFrame`, `RealtimeFrame`
- Main package `com.example.common.websocket.identity`: `RealtimeIdentity`, `RealtimePrincipal`
- Main package `com.example.common.websocket.inbound`: `DefaultRealtimeInboundFrameHandler`, `RealtimeInboundFrameHandler`
- Main package `com.example.common.websocket.observer`: `CompositeRealtimeObserver`, `LoggingRealtimeObserver`, `MicrometerRealtimeObserver`, `NoOpRealtimeObserver`, `RealtimeObserver`
- Main package `com.example.common.websocket.sender`: `DefaultRealtimeBroadcaster`, `RealtimeBroadcaster`, `RealtimeMessageSender`, `RealtimeSendResult`
- Main package `com.example.common.websocket.session`: `InMemoryRealtimeSessionRegistry`, `RealtimeSession`, `RealtimeSessionRegistry`
- Main package `com.example.common.websocket.subscription`: `InMemoryRealtimeSubscriptionRegistry`, `RealtimeDestination`, `RealtimeDestinationType`, `RealtimeSubscription`, `RealtimeSubscriptionRegistry`
- Test package `com.example.common.websocket.adapter.spring`: `SpringRealtimeHandshakeHandlerTest`, `SpringRealtimeHandshakeInterceptorTest`, `SpringRealtimeInboundGuardTest`, `SpringRealtimeLifecycleAdapterRaceTest`, `SpringRealtimeLifecycleAdapterTest`, `SpringRealtimeMessageSenderTest`
- Test package `com.example.common.websocket.auth`: `RealtimeAuthorizationPolicyTest`
- Test package `com.example.common.websocket.config`: `RealtimeWebSocketAutoConfigurationTest`
- Test package `com.example.common.websocket.error`: `RealtimeErrorInvariantTest`
- Test package `com.example.common.websocket.frame`: `RealtimeFrameContractTest`
- Test package `com.example.common.websocket.guard`: `WebSocketContractGuardTest`
- Test package `com.example.common.websocket.identity`: `RealtimeIdentityInvariantTest`
- Test package `com.example.common.websocket.inbound`: `DefaultRealtimeInboundFrameHandlerTest`
- Test package `com.example.common.websocket.observer`: `RealtimeObserverTest`
- Test package `com.example.common.websocket.sender`: `DefaultRealtimeBroadcasterTest`, `RealtimeSendResultInvariantTest`
- Test package `com.example.common.websocket.session`: `InMemoryRealtimeSessionRegistryTest`
- Test package `com.example.common.websocket.subscription`: `InMemoryRealtimeSubscriptionRegistryTest`, `RealtimeDestinationTest`, `RealtimeSubscriptionInvariantTest`
- Direct shared contract dependency because `common-websocket` still has `api project(':common:common-event-contract')`: `chatappBE/common/common-event-contract/build.gradle`, `EventEnvelope`, `EventMetadata`
- Boundary searches confirmed no direct main-source dependency from `common-websocket` or `common-event-contract` to `common-kafka` or `common-redis`; only guard-test class-existence checks mention those packages.
- No service module, gateway/business/domain logic, Kafka implementation, Redis implementation, or code outside the requested common scope was reviewed.

## 2. Build/Test Validation
- Command run from `chatappBE`: `.\gradlew.bat :common:common-websocket:compileJava --no-daemon`
- Compile status: PASS. `:common:common-event-contract:compileJava` and `:common:common-websocket:compileJava` completed successfully.
- Command run from `chatappBE`: `.\gradlew.bat :common:common-websocket:test --no-daemon`
- Test status: FAIL at test compile, before runtime tests execute.
- Failure type: `:common:common-websocket:compileTestJava` failure. This is not a runtime test failure.
- Exact test compile failures: 27 Java compile errors.
- `SpringRealtimeLifecycleAdapterTest.java:70,81,94,135`: `RealtimeInboundFrameHandler.handleRawFrame` is verified/stubbed with `(String, RealtimePrincipal, String)`, but the current interface requires `(String, String)`.
- `DefaultRealtimeInboundFrameHandlerTest.java:78,95,113,114,132,133,145,158,169,191,196,214,216,218,228,244,258,272,287`: calls still use removed `handleRawFrame(sessionId, principal, rawPayload)` shape; current method is `handleRawFrame(sessionId, rawPayload)`.
- `RealtimeAuthorizationPolicyTest.java:41`, `RealtimeObserverTest.java:55`, `DefaultRealtimeBroadcasterTest.java:123`, `RealtimeDestinationTest.java:18`: tests pass `UUID` to `RealtimeDestination.user(...)`, but the current factory accepts `String`.
- Latent stale test found but not executed because compilation stops first: `SpringRealtimeLifecycleAdapterRaceTest.java:48` calls `subscribeIfAbsent("r1", null, ...)` even though `InMemoryRealtimeSubscriptionRegistry` rejects null `userId`.

## 3. What Was Fixed Successfully
- Main source now compiles. The prior `CookieRealtimeHandshakeTokenResolver` and `SpringRealtimeTextWebSocketHandler` compile blockers are fixed at `compileJava`.
- `CookieRealtimeHandshakeTokenResolver` no longer calls a nonexistent `ServerHttpRequest.getCookies()`. It now checks `ServletServerHttpRequest` and falls back to parsing the `Cookie` header.
- `SpringRealtimeTextWebSocketHandler` now extends `TextWebSocketHandler` and overrides Spring-valid methods: `afterConnectionEstablished`, `handleTextMessage(WebSocketSession, TextMessage)`, `afterConnectionClosed`, `handleTransportError`, and `supportsPartialMessages`.
- The reusable Spring bridge exists in main source: `SpringRealtimeConnectionManager`, `SpringRealtimeLifecycleAdapter`, `SpringRealtimeMessageSender`, `SpringRealtimeSession`, `SpringRealtimeHandshakeInterceptor`, `SpringRealtimeHandshakeHandler`, and `SpringRealtimeTextWebSocketHandler`.
- `RealtimeInboundFrameHandler` now has the intended current contract, `handleRawFrame(String sessionId, String rawPayload)`, and `DefaultRealtimeInboundFrameHandler` derives the principal from `RealtimeSessionRegistry`.
- `common-websocket` now depends directly on the minimal `common-event-contract` module for `EventEnvelope` and `EventMetadata`, not on the broader event catalog/payload module.
- Core public contracts do not expose Spring `WebSocketSession`; Spring ownership is isolated to `adapter.spring`.
- `SpringRealtimeSession` now returns an immutable snapshot of session attributes rather than the live Spring-owned map.
- Auto-configuration no longer registers a default allow-all authorization policy. Without a `RealtimeAuthorizationPolicy`, inbound and handshake beans are not auto-created.
- Send results and error records now have stronger constructor invariants than earlier rounds.

## 4. Remaining Problems
### High
- Problem: The test suite is still stale and does not compile.
  - Exact file/class: `SpringRealtimeLifecycleAdapterTest`, `DefaultRealtimeInboundFrameHandlerTest`, `RealtimeAuthorizationPolicyTest`, `RealtimeObserverTest`, `DefaultRealtimeBroadcasterTest`, `RealtimeDestinationTest`, `SpringRealtimeLifecycleAdapterRaceTest`
  - Why it is still a problem: Tests still assume removed APIs like `handleRawFrame(sessionId, principal, rawPayload)` and UUID-based `RealtimeDestination.user(UUID)`. The current source has moved to registry-derived principals and `RealtimeDestination.user(String)`.
  - Impact: `./gradlew :common:common-websocket:test --no-daemon` cannot run any tests. Freeze readiness is unverified, and regressions in the refactor are not guarded.
  - Recommended fix: Update tests to call `handleRawFrame(sessionId, rawPayload)`, seed principals through `RealtimeSessionRegistry`, use the final user-destination identifier type, and remove the null `userId` fixture in `SpringRealtimeLifecycleAdapterRaceTest`.

- Problem: Direct delivery destinations can still be registered as subscriptions.
  - Exact file/class: `DefaultRealtimeInboundFrameHandler`, `InMemoryRealtimeSubscriptionRegistry`, `RealtimeDestination`, `DefaultRealtimeBroadcaster`
  - Why it is still a problem: `RealtimeDestination` distinguishes direct targets (`GLOBAL`, `USER`, `SESSION`) from subscription topics (`CHANNEL`), but `handleSubscribe` and `subscribeIfAbsent` never check `destination.isSubscriptionTopic()`. `DefaultRealtimeBroadcaster.sendToDestination` bypasses the subscription registry for direct targets.
  - Impact: A client can create subscription state for `GLOBAL`, `USER`, or `SESSION`, but broadcaster routing will ignore those subscription entries. Pub/sub semantics and direct-delivery semantics remain inconsistent.
  - Recommended fix: Reject non-`CHANNEL` destinations in both inbound subscribe handling and the subscription registry. Return a standard client error such as `UNKNOWN_DESTINATION` or a dedicated invalid-subscription-destination code, and add tests for `GLOBAL`, `USER`, and `SESSION` rejection.

- Problem: Authorization decisions are half-finished.
  - Exact file/class: `RealtimeAuthorizationPolicy`, `RealtimeAuthorizationDecision`, `AllowAllRealtimeAuthorizationPolicy`, `SpringRealtimeHandshakeInterceptor`, `DefaultRealtimeInboundFrameHandler`, `RealtimeObserver`
  - Why it is still a problem: `RealtimeAuthorizationDecision` exists and says it replaces boolean-only responses, but the active policy methods still return `boolean`. Handshake denial reports hard-coded `AUTHORIZATION_DENIED`; subscribe denial reports hard-coded `SUBSCRIBE_DENIED` and sends generic "Subscription denied".
  - Impact: Services cannot provide typed reason codes or safe client-facing messages through the common contract. Denial handling is not standardized across handshake, inbound processing, error frames, and observer reporting.
  - Recommended fix: Either change `allowConnect` and `allowSubscribe` to return `RealtimeAuthorizationDecision` and wire `reasonCode()` / `clientMessage()` through observer and error frames, or remove `RealtimeAuthorizationDecision` until the active contract actually uses it.

- Problem: Identity and user routing are internally inconsistent.
  - Exact file/class: `RealtimePrincipal`, `RealtimeIdentity`, `RealtimeDestination`, `RealtimeSubscription`, `RealtimeSubscriptionRegistry`, `RealtimeSessionRegistry`, `RealtimeBroadcaster`, `DefaultRealtimeBroadcaster`, `DefaultRealtimeInboundFrameHandler`
  - Why it is still a problem: `RealtimeDestination.user(String)` suggests generic user routing, but the principal, session registry, broadcaster, and subscription records still use `UUID`. `DefaultRealtimeBroadcaster` reparses the destination identifier with `UUID.fromString(...)`. `RealtimePrincipal.userId()` is nullable for anonymous or system clients, but `InMemoryRealtimeSubscriptionRegistry` rejects null `userId`.
  - Impact: Non-UUID user IDs silently do not route. Anonymous/system identities are advertised but not fully supported. If an authorization policy allows an anonymous subscribe, the null user ID becomes an internal error path rather than a clear authorization or unsupported-identity response.
  - Recommended fix: Choose one final model before freeze: either make user routing explicitly UUID-only everywhere, including `RealtimeDestination.user(UUID)`, or change all user/session/subscription APIs to a generic subject identifier. Add an explicit principal kind such as `USER`, `ANONYMOUS`, `SYSTEM` and define which kinds can subscribe.

- Problem: Handshake-resolved identity can silently drift from registered session identity.
  - Exact file/class: `SpringRealtimeHandshakeInterceptor`, `SpringRealtimeHandshakeHandler`, `SpringRealtimeSession`
  - Why it is still a problem: The interceptor stores the resolved principal in `ATTR_PRINCIPAL`, and the handshake handler promotes that attribute to the Spring session principal. But `SpringRealtimeSession` ignores `ATTR_PRINCIPAL` and trusts only `WebSocketSession.getPrincipal()`, falling back to `RealtimeIdentity.anonymous(...)` if the Spring principal is absent or not a `RealtimePrincipal`.
  - Impact: If a service wires the interceptor without the matching handler, or a custom handler returns a different principal, an authenticated handshake can be registered as anonymous without failing closed.
  - Recommended fix: Make `SpringRealtimeSession` derive identity from the stored handshake attribute, compare it with the Spring principal when both exist, and fail closed on absence or mismatch instead of silently downgrading authenticated setups to anonymous.

- Problem: Lifecycle mutations are not rollback-safe across physical and common state.
  - Exact file/class: `SpringRealtimeConnectionManager`, `SpringRealtimeLifecycleAdapter`
  - Why it is still a problem: `connect` writes the physical session map with `sessions.put(...)` before running `commonMutation`. If common registry mutation throws, the physical manager can retain a session that the common registry did not register. In `disconnect`, the physical session is removed before `cleanupAction`; if cleanup throws, close and common cleanup can be incomplete.
  - Impact: Custom registries or cleanup implementations can leave physical session state and common registry state divergent. Observer events may also report lifecycle transitions that did not fully complete.
  - Recommended fix: Add rollback or compensation around `commonMutation` and `cleanupAction`; keep cleanup best-effort but non-escaping; close sockets consistently; emit observer lifecycle events only after the physical and common state changes have actually converged.

### Medium
- Problem: The session abstraction still leaks adapter-owned attribute content.
  - Exact file/class: `RealtimeSession`, `SpringRealtimeSession`, `SpringRealtimeHandshakeInterceptor`
  - Why it is still a problem: `SpringRealtimeSession` snapshots `delegate.getAttributes()` and exposes it through the core `RealtimeSession.attributes()` contract. The map is immutable, but it can still contain Spring handshake keys such as `realtimePrincipal` and arbitrary adapter/interceptor values.
  - Impact: The implementation is safer than a live mutable map, but it is not fully transport-neutral in practice.
  - Recommended fix: Expose only whitelisted common-owned metadata, remove attributes from the core contract, or create an adapter-specific metadata view that cannot be mistaken for common realtime state.

- Problem: Cookie token extraction is compile-valid but under-guarded and still rough.
  - Exact file/class: `CookieRealtimeHandshakeTokenResolver`
  - Why it is still a problem: The resolver uses reflection against `ServletServerHttpRequest` even though this module already compiles with servlet APIs through Spring WebSocket, has no cookie-specific tests, has an unused `HttpCookie` import, and its example omits the current `SpringRealtimeHandshakeInterceptor` observer constructor argument.
  - Impact: The previous compile blocker is fixed, but cookie extraction can regress without test coverage and the production example is misleading.
  - Recommended fix: Use direct servlet cookie access or a well-tested header parser, remove the unused import, update the example constructor, and add tests for servlet cookies, raw `Cookie` headers, missing cookies, and blank cookie values.

- Problem: Observability documentation and auto-configuration are not fully aligned.
  - Exact file/class: `RealtimeWebSocketAutoConfiguration`, `MicrometerRealtimeObserver`, `LoggingRealtimeObserver`, `CompositeRealtimeObserver`
  - Why it is still a problem: Auto-configuration creates only `NoOpRealtimeObserver` by default. `MicrometerRealtimeObserver` says it is loaded when Micrometer and `MeterRegistry` are available, but no auto-configuration creates or composes it.
  - Impact: Runtime behavior is no-op by default, while the metrics observer Javadoc implies conditional loading that does not happen.
  - Recommended fix: Either document logging and Micrometer observers as manual beans only, or add conditional observer auto-configuration and composition when `MeterRegistry` is present.

- Problem: The reusable Spring transport bridge is source-valid but not yet freeze-proven.
  - Exact file/class: `SpringRealtimeTextWebSocketHandler`, `SpringRealtimeLifecycleAdapter`, `SpringRealtimeConnectionManager`, `RealtimeWebSocketAutoConfiguration`
  - Why it is still a problem: The handler now overrides correct Spring methods, but the test suite does not compile, so the bridge cannot be called test-clean. Auto-configuration creates the lifecycle adapter but not a `SpringRealtimeTextWebSocketHandler` bean, so services still compose the final endpoint registration manually.
  - Impact: The bridge is much closer to production-valid, but the current review cannot certify it as freeze-ready until tests compile and lifecycle failure semantics are hardened.
  - Recommended fix: Fix tests first, then add focused tests proving handler-to-lifecycle delegation, stale-session guards, connect failure behavior, disconnect cleanup, and auto-config expectations.

### Low
- Problem: Mojibake, stale comments, and stale examples remain.
  - Exact file/class: `InMemoryRealtimeSubscriptionRegistryTest`, `SpringRealtimeHandshakeInterceptorTest`, `CookieRealtimeHandshakeTokenResolver`, `SpringRealtimeTextWebSocketHandler`
  - Why it is still a problem: Several test comments contain corrupted encoding sequences. The cookie resolver example still shows an outdated interceptor constructor. `SpringRealtimeTextWebSocketHandler` imports `RealtimeObserver` but does not use it.
  - Impact: Runtime behavior is not affected, but this is not freeze-quality cleanup.
  - Recommended fix: Normalize comments to clean ASCII or valid UTF-8, update examples against current constructors, and remove dead imports.

- Problem: Development-only allow-all authorization remains in main source.
  - Exact file/class: `AllowAllRealtimeAuthorizationPolicy`
  - Why it is still a problem: It is deprecated and not auto-configured, but it remains easy for production code to import.
  - Impact: Low immediate risk because services must explicitly wire it, but it is still a freeze-polish concern.
  - Recommended fix: Move it to test fixtures, rename it more explicitly as development-only, or keep it only if there is a documented accepted use case.

- Problem: The direct event contract is minimal, but still service-worded.
  - Exact file/class: `EventMetadata`
  - Why it is still a problem: `sourceService` is required metadata. That is probably intentional for this application, but it is service-shaped terminology in the shared event contract.
  - Impact: Low risk and not a websocket freeze blocker by itself.
  - Recommended fix: Keep it if service-origin metadata is the final standard; otherwise rename to a more generic `source` before freezing the contract.

## 5. Regression / Risk Check
- The latest refactor fixed the previous main-source compile blockers, but it regressed or failed to update the test suite. `compileJava` passes; `compileTestJava` fails with 27 errors.
- The inbound API change to registry-owned principals is the right direction, but stale tests still encode the removed caller-supplied principal model. More importantly, the implementation now depends on the session registry being identity-correct.
- `RealtimeDestination.user(String)` appears to broaden routing to arbitrary user identifiers, but the rest of the module remains UUID-only. This is an API compatibility and semantic risk.
- Direct destination modeling improved, but subscription enforcement was not completed. This can create accepted-but-ignored subscription state.
- Adding `RealtimeAuthorizationDecision` without using it creates a breaking-change risk later: freezing the boolean policy now would make the typed decision refactor harder.
- `SpringRealtimeSession` snapshotting attributes is safer than exposing the live Spring map, but it still exposes adapter-owned content through a core contract.
- No direct Kafka or Redis dependency was found in the reviewed websocket main source; the websocket module still directly depends on `common-event-contract` through `RealtimeEventFrame`.

## 6. Freeze Readiness
- Answer: NO
- Exact remaining blockers inside `common`:
- `:common:common-websocket:test --no-daemon` fails during `compileTestJava`.
- Stale common-websocket tests still assume removed inbound and destination APIs.
- `GLOBAL`, `USER`, and `SESSION` can still enter the subscription registry even though broadcaster semantics treat them as direct targets.
- Authorization is still boolean in the active policy contract while a typed decision abstraction remains unused.
- Identity and routing are split between generic string destinations and UUID-only principals/registries/broadcaster APIs.
- Anonymous/system identity support is advertised but not defined as a complete subscription/session/routing model.
- Handshake-resolved identity and Spring session identity can diverge without fail-closed behavior.
- Lifecycle connect/disconnect mutation failures can leave physical Spring sessions and common registries inconsistent.
- Session attributes are immutable but still adapter-derived, so the transport-neutral session abstraction is not clean enough for freeze.

## 7. Final Recommendation
- `common-websocket` cannot be frozen before touching services.
- The next best step is a common-only stabilization pass: first make tests compile against the current contracts, then enforce channel-only subscriptions, then settle the authorization decision contract, the identity/routing model, and handshake/session identity derivation.
- After those fixes, rerun `.\gradlew.bat :common:common-websocket:test --no-daemon` and do one final strict freeze review before any service migration depends on this module.
