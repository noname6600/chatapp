# Common WebSocket Post-Refactor Review

## 1. Scope Reviewed
- `chatappBE/common/common-websocket/build.gradle`
- `chatappBE/common/common-websocket/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- `chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/adapter/spring/CookieRealtimeHandshakeTokenResolver.java`
- `chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/adapter/spring/HeaderRealtimeHandshakeTokenResolver.java`
- `chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/adapter/spring/QueryParamRealtimeHandshakeTokenResolver.java`
- `chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/adapter/spring/RealtimeHandshakeTokenResolver.java`
- `chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/adapter/spring/SpringRealtimeConnectionManager.java`
- `chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/adapter/spring/SpringRealtimeHandshakeHandler.java`
- `chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/adapter/spring/SpringRealtimeHandshakeInterceptor.java`
- `chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/adapter/spring/SpringRealtimeLifecycleAdapter.java`
- `chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/adapter/spring/SpringRealtimeMessageSender.java`
- `chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/adapter/spring/SpringRealtimeSession.java`
- `chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/adapter/spring/SpringRealtimeTextWebSocketHandler.java`
- `chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/auth/AllowAllRealtimeAuthorizationPolicy.java`
- `chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/auth/RealtimeAuthorizationDecision.java`
- `chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/auth/RealtimeAuthorizationPolicy.java`
- `chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/auth/RealtimeIdentityResolver.java`
- `chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/codec/JsonRealtimeFrameCodec.java`
- `chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/codec/RealtimeCodecException.java`
- `chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/codec/RealtimeFrameCodec.java`
- `chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/config/RealtimeWebSocketAutoConfiguration.java`
- `chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/error/RealtimeError.java`
- `chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/error/RealtimeErrorCode.java`
- `chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/error/RealtimeSendFailureReason.java`
- `chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/frame/RealtimeCommandFrame.java`
- `chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/frame/RealtimeCommandType.java`
- `chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/frame/RealtimeErrorFrame.java`
- `chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/frame/RealtimeEventFrame.java`
- `chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/frame/RealtimeFrame.java`
- `chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/identity/RealtimeIdentity.java`
- `chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/identity/RealtimePrincipal.java`
- `chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/inbound/DefaultRealtimeInboundFrameHandler.java`
- `chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/inbound/RealtimeInboundFrameHandler.java`
- `chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/observer/CompositeRealtimeObserver.java`
- `chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/observer/LoggingRealtimeObserver.java`
- `chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/observer/MicrometerRealtimeObserver.java`
- `chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/observer/NoOpRealtimeObserver.java`
- `chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/observer/RealtimeObserver.java`
- `chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/sender/DefaultRealtimeBroadcaster.java`
- `chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/sender/RealtimeBroadcaster.java`
- `chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/sender/RealtimeMessageSender.java`
- `chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/sender/RealtimeSendResult.java`
- `chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/session/InMemoryRealtimeSessionRegistry.java`
- `chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/session/RealtimeSession.java`
- `chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/session/RealtimeSessionRegistry.java`
- `chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/subscription/InMemoryRealtimeSubscriptionRegistry.java`
- `chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/subscription/RealtimeDestination.java`
- `chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/subscription/RealtimeDestinationType.java`
- `chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/subscription/RealtimeSubscription.java`
- `chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/subscription/RealtimeSubscriptionRegistry.java`
- Direct shared contract dependency: `chatappBE/common/common-event-contract/build.gradle`
- Direct shared contract dependency: `chatappBE/common/common-event-contract/src/main/java/com/example/common/event/EventEnvelope.java`
- Direct shared contract dependency: `chatappBE/common/common-event-contract/src/main/java/com/example/common/event/EventMetadata.java`
- Targeted common-websocket tests/source checked for stale contract coverage: `SpringRealtimeLifecycleAdapterTest.java`, `SpringRealtimeLifecycleAdapterRaceTest.java`, `SpringRealtimeInboundGuardTest.java`, `DefaultRealtimeInboundFrameHandlerTest.java`, `RealtimeWebSocketAutoConfigurationTest.java`, `WebSocketContractGuardTest.java`, `InMemoryRealtimeSessionRegistryTest.java`, `InMemoryRealtimeSubscriptionRegistryTest.java`, `RealtimeDestinationTest.java`, `DefaultRealtimeBroadcasterTest.java`, `RealtimeAuthorizationPolicyTest.java`, `RealtimeIdentityInvariantTest.java`
- Verification run: `./gradlew :common:common-websocket:test --no-daemon` failed at `compileJava`; `./gradlew :common:common-event-contract:test --no-daemon` passed with no test sources.

## 2. Refactor Validation Summary
- The architecture moved in the right direction: `common-websocket` no longer depends on the domain-heavy `common-events` catalog/payload packages and now depends only on the small `common-event-contract` module for `EventEnvelope` and `EventMetadata`.
- The old websocket DTO/wrapper shape was replaced by typed frame contracts: `RealtimeFrame`, `RealtimeEventFrame`, `RealtimeCommandFrame`, and `RealtimeErrorFrame`.
- The sender and codec contracts are now frame-typed instead of raw `Object`-typed, which is a real standardization improvement.
- The in-memory session registry now removes stale user-index entries when a session ID is re-registered under a different user.
- `SpringRealtimeConnectionManager` no longer has an unbounded per-session lock map. It uses fixed lock stripes and CAS-style disconnects, so the previous lock-retention leak shape is gone.
- Inbound handling now derives the principal from `RealtimeSessionRegistry`, not from a caller-provided inbound parameter. That is the right single-source-of-truth direction.
- Common now includes a reusable Spring lifecycle adapter and an intended reusable Spring handler base, so services should need much less lifecycle glue after the compile blockers are fixed.
- The destination model is more explicit than before: `GLOBAL`, `USER`, `SESSION`, and `CHANNEL` are separate types, and direct targets are distinguished from subscription topics at the model level.
- The layer is not freeze-ready. It does not compile, the tests are stale against removed APIs, destination subscription semantics are still not enforced, authorization decisions remain boolean-only in the active contract, and identity is still UUID/user-centric rather than fully transport-neutral.

## 3. Remaining Problems
### High
- Problem: `common-websocket` does not compile.
  - Exact file/class: `chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/adapter/spring/CookieRealtimeHandshakeTokenResolver.java`, `CookieRealtimeHandshakeTokenResolver`
  - Why it is still a problem: `resolve(ServerHttpRequest request)` calls `request.getCookies()`, but `org.springframework.http.server.ServerHttpRequest` does not expose `getCookies()`.
  - Impact: `:common:common-websocket:compileJava` fails, so the module cannot be published, tested, or frozen.
  - Recommended fix: Parse cookies from `request.getHeaders().get("Cookie")`, or support servlet requests by casting to `ServletServerHttpRequest` and reading `HttpServletRequest#getCookies()`. Add a resolver test for servlet and non-servlet requests.
- Problem: The reusable Spring handler base does not compile.
  - Exact file/class: `chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/adapter/spring/SpringRealtimeTextWebSocketHandler.java`, `SpringRealtimeTextWebSocketHandler`
  - Why it is still a problem: The class extends `AbstractWebSocketHandler` but declares `@Override protected void handleTextMessage(WebSocketSession, WebSocketMessage<?>)`, which is not a matching superclass method.
  - Impact: The intended lifecycle standardization bridge is unavailable; services cannot safely standardize on this base.
  - Recommended fix: Extend `TextWebSocketHandler` and override `handleTextMessage(WebSocketSession, TextMessage)`, or override `handleMessage(WebSocketSession, WebSocketMessage<?>)` on `AbstractWebSocketHandler` and safely reject non-text frames.
- Problem: The test suite is stale against the refactored public contracts.
  - Exact file/class: `SpringRealtimeLifecycleAdapterTest`, `DefaultRealtimeInboundFrameHandlerTest`, `RealtimeDestinationTest`, `DefaultRealtimeBroadcasterTest`, `RealtimeAuthorizationPolicyTest`
  - Why it is still a problem: Several tests still call removed signatures such as `handleRawFrame(sessionId, principal, raw)` even though `RealtimeInboundFrameHandler` now accepts only `(sessionId, rawPayload)`. Several tests also pass `UUID` to `RealtimeDestination.user(...)`, while the current factory accepts `String`.
  - Impact: After the main compile errors are fixed, test compilation is expected to fail or validate the wrong contract. The refactor has no reliable freeze safety net.
  - Recommended fix: Update tests to the registry-owned-principal model, remove caller-provided principal assertions, use the current destination API, and add negative tests for stale inbound sessions, direct-destination subscribe rejection, and anonymous/system principal behavior.
- Problem: Direct delivery targets can still be subscribed to.
  - Exact file/class: `chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/inbound/DefaultRealtimeInboundFrameHandler.java`, `DefaultRealtimeInboundFrameHandler`; `chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/subscription/InMemoryRealtimeSubscriptionRegistry.java`, `InMemoryRealtimeSubscriptionRegistry`
  - Why it is still a problem: `RealtimeDestination` says `GLOBAL`, `USER`, and `SESSION` are direct targets and only `CHANNEL` is a subscription topic, but `handleSubscribe` and `subscribeIfAbsent` accept any `RealtimeDestination`.
  - Impact: A client can receive successful subscription state for destinations that `DefaultRealtimeBroadcaster.sendToDestination` later bypasses. That keeps direct delivery and pub/sub semantics ambiguously mixed.
  - Recommended fix: Reject non-`CHANNEL` destinations in the inbound subscribe path and/or in `RealtimeSubscriptionRegistry.subscribeIfAbsent`, returning `UNKNOWN_DESTINATION` or a dedicated `INVALID_SUBSCRIPTION_DESTINATION` error.

### Medium
- Problem: Typed authorization decisions exist but are not wired into the active authorization contract.
  - Exact file/class: `chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/auth/RealtimeAuthorizationPolicy.java`, `RealtimeAuthorizationPolicy`; `RealtimeAuthorizationDecision.java`; `DefaultRealtimeInboundFrameHandler`; `SpringRealtimeHandshakeInterceptor`
  - Why it is still a problem: `RealtimeAuthorizationDecision` claims to replace boolean-only authorization responses, but `allowConnect` and `allowSubscribe` still return `boolean`. Denial observers and error frames still receive hard-coded strings such as `AUTHORIZATION_DENIED` and `SUBSCRIBE_DENIED`.
  - Impact: Services cannot provide standardized denial reason codes or safe client messages through the common policy surface.
  - Recommended fix: Change the policy methods to return `RealtimeAuthorizationDecision`, adapt the interceptor and inbound handler to use `reasonCode()` and `clientMessage()`, and keep boolean helpers only as compatibility adapters.
- Problem: Anonymous and system identity semantics are incomplete and internally inconsistent.
  - Exact file/class: `chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/identity/RealtimePrincipal.java`, `RealtimePrincipal`; `RealtimeIdentity`; `DefaultRealtimeInboundFrameHandler`; `InMemoryRealtimeSubscriptionRegistry`; `RealtimeSubscriptionRegistry`
  - Why it is still a problem: `RealtimePrincipal.userId()` is documented as null for anonymous or system clients, and `RealtimeIdentity.anonymous(...)` is allowed. The subscribe path then passes that null to `subscribeIfAbsent`, which rejects null user IDs and turns an allowed anonymous subscription into an internal error.
  - Impact: The model advertises anonymous/system support but the registry and inbound flow only really support authenticated UUID users.
  - Recommended fix: Introduce an explicit principal kind such as `USER`, `ANONYMOUS`, `SYSTEM`; decide whether anonymous/system subscriptions are supported; and make `RealtimeSubscription` carry a generic subject/principal reference instead of requiring UUID user identity.
- Problem: Core routing remains UUID/user-shaped rather than generic realtime identity.
  - Exact file/class: `RealtimePrincipal`, `RealtimeSessionRegistry`, `RealtimeBroadcaster`, `DefaultRealtimeBroadcaster`, `RealtimeDestination`, `RealtimeSubscription`, `RealtimeSubscriptionRegistry`
  - Why it is still a problem: User-targeted APIs require `UUID`, while `RealtimeDestination.user(String)` accepts arbitrary strings and `DefaultRealtimeBroadcaster` silently parses the string back to UUID.
  - Impact: The common layer is reusable for this application, but not truly generic for systems with non-UUID subjects, tenant-scoped identities, device identities, or system principals.
  - Recommended fix: Normalize identity routing around a single `RealtimeSubject` or string subject identifier, or make UUID an application adapter concern outside core common-websocket.
- Problem: Spring handshake identity and registered session identity can drift.
  - Exact file/class: `chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/adapter/spring/SpringRealtimeSession.java`, `SpringRealtimeSession`; `SpringRealtimeHandshakeInterceptor`; `SpringRealtimeHandshakeHandler`
  - Why it is still a problem: `SpringRealtimeHandshakeInterceptor` stores the resolved principal in attributes, but `SpringRealtimeSession` ignores that attribute and trusts `WebSocketSession#getPrincipal()`. If a service wires the interceptor without the matching handler, or a custom handler returns a different principal, the common registry records a fallback anonymous principal instead of failing closed on mismatch.
  - Impact: The inbound flow has a registry-owned principal, but the registry-owned value can still be derived from the wrong Spring source.
  - Recommended fix: In `SpringRealtimeSession`, read `ATTR_PRINCIPAL` from the snapshot, compare it with `session.getPrincipal()` when both exist, and fail closed on mismatch. Avoid anonymous fallback for sessions that have a resolved handshake principal.
- Problem: Adapter-owned session attributes leak through the core session abstraction.
  - Exact file/class: `chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/adapter/spring/SpringRealtimeSession.java`, `SpringRealtimeSession`; `chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/session/RealtimeSession.java`, `RealtimeSession`
  - Why it is still a problem: `SpringRealtimeSession` exposes a shallow `Map.copyOf(delegate.getAttributes())` through `RealtimeSession.attributes()`. This can expose Spring handshake keys such as `realtimePrincipal` and mutable attribute values owned by adapters or other interceptors.
  - Impact: Core consumers can observe transport adapter state despite the comment promising no adapter leakage.
  - Recommended fix: Expose only whitelisted, common-owned attributes; deep-copy known immutable-safe values; or remove attributes from the core contract and expose adapter metadata through adapter-specific APIs.
- Problem: Connection-manager comments overstate atomicity across physical and common registries.
  - Exact file/class: `chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/adapter/spring/SpringRealtimeConnectionManager.java`, `SpringRealtimeConnectionManager`; `SpringRealtimeLifecycleAdapter`
  - Why it is still a problem: `connect` writes the Spring session map before running `commonMutation`. If `commonMutation` throws, the physical session remains registered while the common registry may not be updated. `observer.onConnected` is also emitted outside the lock and can be observed after a concurrent replacement.
  - Impact: Default in-memory paths are probably fine, but custom registries or observers can see inconsistent lifecycle state under failure or tight reconnect races.
  - Recommended fix: Roll back `sessions.put` if `commonMutation` fails, or make the failure contract explicit. Consider emitting connected events only if the session is still current after the common registry update.
- Problem: Micrometer/logging observer behavior is manual, not auto-composed.
  - Exact file/class: `chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/config/RealtimeWebSocketAutoConfiguration.java`, `RealtimeWebSocketAutoConfiguration`; `MicrometerRealtimeObserver`; `LoggingRealtimeObserver`; `CompositeRealtimeObserver`
  - Why it is still a problem: The module provides logging and Micrometer observers, but auto-configuration only creates `NoOpRealtimeObserver` when no observer bean exists. `MicrometerRealtimeObserver` documentation says it is only loaded when a `MeterRegistry` is available, but no auto-config path loads it.
  - Impact: Observability is honest as no-op by default, but the provided metrics/logger classes are not standardized by configuration.
  - Recommended fix: Either document these as manual beans only, or add conditional observer auto-configuration and composition when `MeterRegistry` is present.

### Low
- Problem: Mojibake and stale Javadocs remain.
  - Exact file/class: `DefaultRealtimeInboundFrameHandler`, `RealtimeObserver`, `CompositeRealtimeObserver`, `DefaultRealtimeBroadcaster`, `RealtimeFrame`, `RealtimeErrorCode`, several tests under `src/test/java`
  - Why it is still a problem: Comments contain corrupted text such as `â€”` and `Ã¢...`, and some examples are stale, including the cookie resolver example constructing `SpringRealtimeHandshakeInterceptor` with too few constructor arguments.
  - Impact: This does not change runtime behavior, but it fails the cleanup-quality bar for a frozen common module.
  - Recommended fix: Normalize source encoding to UTF-8, replace corrupted punctuation with ASCII or valid UTF-8, and refresh all examples against the current constructors.
- Problem: `AllowAllRealtimeAuthorizationPolicy` remains in production source.
  - Exact file/class: `chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/auth/AllowAllRealtimeAuthorizationPolicy.java`, `AllowAllRealtimeAuthorizationPolicy`
  - Why it is still a problem: It is clearly marked development-only and is not auto-configured, so it is not fail-open by default. Still, keeping it in main source invites accidental import.
  - Impact: Low direct risk because services must explicitly wire it, but it is not freeze-clean.
  - Recommended fix: Move it to test fixtures or rename it even more explicitly, for example `DevelopmentOnlyAllowAllRealtimeAuthorizationPolicy`.
- Problem: The minimal event contract is transport-neutral but still service-worded.
  - Exact file/class: `chatappBE/common/common-event-contract/src/main/java/com/example/common/event/EventMetadata.java`, `EventMetadata`
  - Why it is still a problem: `sourceService` is required metadata. That is acceptable for this system, but it is still service-shaped terminology in the shared contract.
  - Impact: Low risk; it is not domain-heavy and does not reintroduce the old event catalog coupling.
  - Recommended fix: Keep it if service-origin metadata is intentional, or rename to a more generic `source` before freezing if this contract is meant to outlive service-to-service events.

## 4. Regression Check
- The main regression is buildability: `./gradlew :common:common-websocket:test --no-daemon` fails during `compileJava`, so no common-websocket tests run.
- The refactor removed caller-provided inbound principals, but several tests and comments still assume the old `(sessionId, principal, rawPayload)` inbound API.
- The `RealtimeDestination.user(...)` API moved to `String`, while tests and broadcaster expectations still treat UUID as the real supported user identifier.
- Anonymous compatibility is weaker than before: non-`RealtimePrincipal` Spring principals become anonymous, but anonymous subscriptions currently fail as internal errors because the subscription registry rejects null user IDs.
- Direct destination modeling improved, but the subscribe/unsubscribe pipeline still allows direct destinations into the subscription registry, making the cleanup partial.
- API compatibility concern: changing `RealtimeAuthorizationPolicy` from boolean to typed decisions will be a breaking API change, but the current half-refactor leaves the new decision type unused.

## 5. Freeze Readiness
NO

Exact remaining blockers inside `common`:
- Fix `common-websocket` compile errors in `CookieRealtimeHandshakeTokenResolver` and `SpringRealtimeTextWebSocketHandler`.
- Update common-websocket tests to the current inbound, destination, identity, and authorization contracts.
- Enforce `CHANNEL`-only subscription semantics.
- Wire `RealtimeAuthorizationDecision` into the active authorization contract or remove it until the policy contract is actually refactored.
- Resolve anonymous/system principal semantics and the UUID-hardcoded routing model.
- Stop leaking raw Spring session attributes through `RealtimeSession.attributes()`.
- Clean stale comments, mojibake, and compatibility examples before declaring the module frozen.

## 6. Final Recommendation
- `common-websocket` should not be frozen before touching services.
- The next step should be a focused common-only cleanup pass: first restore compilation, then update the stale tests, then fix the destination/auth/identity contract gaps while staying inside `common-websocket` and `common-event-contract`.
- After that pass, rerun `:common:common-websocket:test` and perform one final freeze review before asking services to migrate to the standardized base.
