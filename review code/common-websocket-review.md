# Common WebSocket Review

## 1. Scope Reviewed
- Reviewed `chatappBE/common/common-websocket/build.gradle`.
- Reviewed `chatappBE/common/common-websocket/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.
- Reviewed `com.example.common.websocket.frame`: `RealtimeFrame`, `RealtimeEventFrame`, `RealtimeCommandFrame`, `RealtimeCommandType`, `RealtimeErrorFrame`.
- Reviewed `com.example.common.websocket.codec`: `RealtimeFrameCodec`, `JsonRealtimeFrameCodec`, `RealtimeCodecException`.
- Reviewed `com.example.common.websocket.error`: `RealtimeError`, `RealtimeErrorCode`, `RealtimeSendFailureReason`.
- Reviewed `com.example.common.websocket.identity`: `RealtimePrincipal`, `RealtimeIdentity`.
- Reviewed `com.example.common.websocket.auth`: `RealtimeIdentityResolver`, `RealtimeAuthorizationPolicy`, `AllowAllRealtimeAuthorizationPolicy`.
- Reviewed `com.example.common.websocket.session`: `RealtimeSession`, `RealtimeSessionRegistry`, `InMemoryRealtimeSessionRegistry`.
- Reviewed `com.example.common.websocket.subscription`: `RealtimeDestination`, `RealtimeDestinationType`, `RealtimeSubscription`, `RealtimeSubscriptionRegistry`, `InMemoryRealtimeSubscriptionRegistry`.
- Reviewed `com.example.common.websocket.sender`: `RealtimeMessageSender`, `RealtimeSendResult`, `RealtimeBroadcaster`, `DefaultRealtimeBroadcaster`.
- Reviewed `com.example.common.websocket.inbound`: `RealtimeInboundFrameHandler`, `DefaultRealtimeInboundFrameHandler`.
- Reviewed `com.example.common.websocket.observer`: `RealtimeObserver`, `NoOpRealtimeObserver`, `LoggingRealtimeObserver`, `MicrometerRealtimeObserver`, `CompositeRealtimeObserver`.
- Reviewed `com.example.common.websocket.adapter.spring`: `SpringRealtimeHandshakeInterceptor`, `SpringRealtimeHandshakeHandler`, `SpringRealtimeLifecycleAdapter`, `SpringRealtimeConnectionManager`, `SpringRealtimeMessageSender`, `SpringRealtimeSession`, `RealtimeHandshakeTokenResolver`, `QueryParamRealtimeHandshakeTokenResolver`.
- Reviewed `com.example.common.websocket.config`: `RealtimeWebSocketAutoConfiguration`.
- Reviewed websocket contract/deprecation tests under `chatappBE/common/common-websocket/src/test/java/com/example/common/websocket/**`, especially `WebSocketContractGuardTest`, adapter race tests, inbound handler tests, frame tests, registry tests, and auto-configuration tests.
- Included `chatappBE/common/common-events/src/main/java/com/example/common/event/EventEnvelope.java` because `RealtimeEventFrame` exposes it in the websocket wire contract.
- Included `chatappBE/common/common-events/src/main/java/com/example/common/event/EventMetadata.java`, `EventContractValidator.java`, and `SharedEventCatalog.java` only because `EventEnvelope` transitively depends on metadata validation and the shared event catalog.
- Did not review service modules or gateway/business/domain logic outside `common`.
- Did not review `common-kafka`, `common-redis`, or service event consumers/producers except to confirm `common-websocket` has no direct Kafka/Redis dependency.

## 2. Architecture Summary
- The current common websocket layer contains transport-neutral contracts for frames, codecs, errors, identity, authorization, session registry, subscription registry, inbound command handling, outbound sending, broadcasting, and observation.
- It also contains a Spring adapter layer for handshake, physical session tracking, lifecycle orchestration, and sending through `WebSocketSession`.
- Current handshake flow: `RealtimeHandshakeTokenResolver` extracts a token from a Spring `ServerHttpRequest`, `RealtimeIdentityResolver` resolves a `RealtimePrincipal`, `RealtimeAuthorizationPolicy.allowConnect` approves or denies the connection, `SpringRealtimeHandshakeInterceptor` stores the principal in handshake attributes, and `SpringRealtimeHandshakeHandler` promotes it to a Java `Principal`.
- Current connection flow: service-owned Spring websocket handler code is still expected to call `SpringRealtimeLifecycleAdapter.onConnected`, which wraps the `WebSocketSession` as `SpringRealtimeSession`, registers it in `SpringRealtimeConnectionManager`, and records it in `RealtimeSessionRegistry`.
- Current inbound flow: service-owned transport handler calls `SpringRealtimeLifecycleAdapter.onTextMessage`, which verifies the physical session, reads the principal from `RealtimeSessionRegistry`, then calls `RealtimeInboundFrameHandler.handleRawFrame`; `DefaultRealtimeInboundFrameHandler` decodes JSON, accepts only `COMMAND` frames, handles `SUBSCRIBE`, `UNSUBSCRIBE`, `PING`, and `PONG`, updates the subscription registry, and sends standard error frames on failures.
- Current outbound flow: services construct a `RealtimeFrame` such as `RealtimeEventFrame`, call `RealtimeMessageSender` or `RealtimeBroadcaster`, `DefaultRealtimeBroadcaster` resolves destination semantics through session and subscription registries, and `SpringRealtimeMessageSender` encodes the frame and writes a Spring `TextMessage`.
- The design is partially generic. The core abstractions avoid direct Spring/Kafka/Redis exposure, but the module is still service-shaped through UUID-only user routing, a public dependency on `common-events` domain catalog types, and the absence of a common Spring `TextWebSocketHandler`/registration standard. It is not yet clean enough to freeze as a reusable base for multiple services.

## 3. What Is Good
- Core contracts mostly avoid exposing `WebSocketSession`; Spring details are mostly contained in `adapter.spring`.
- The frame contract is explicit: `EVENT`, `COMMAND`, and `ERROR` are sealed under `RealtimeFrame`.
- The codec and sender contracts accept `RealtimeFrame`, not raw `Object`.
- Session and subscription registries are common-owned and transport-neutral.
- `DefaultRealtimeInboundFrameHandler` centralizes subscribe, unsubscribe, ping, pong, decode failure, authorization denial, and structured error responses.
- `RealtimeSendResult` and `RealtimeSendFailureReason` replace raw string send outcomes.
- Auto-configuration is fail-closed for authorization: no `RealtimeAuthorizationPolicy` means the handshake interceptor and inbound handler are not silently opened.
- Observer callbacks provide useful lifecycle hooks, and observer failures are isolated from transport flow.
- Guard tests explicitly prevent old room-specific websocket contracts and direct Kafka/Redis websocket dependencies from reappearing.

## 4. Problems
### High
- `chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/adapter/spring/SpringRealtimeConnectionManager.java` / `SpringRealtimeConnectionManager`
  - Why: `sessionLocks` is intentionally never cleaned up. Spring websocket session IDs are normally unique per connection, so this map grows for the lifetime of the JVM.
  - Impact: long-running services with reconnects leak one lock object per historical session ID. That is not production-clean for a common base.
  - Recommended fix: replace permanent lock retention with a versioned session state holder, striped locks, or reference-counted keyed locks that preserve race safety without unbounded growth.

- `chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/inbound/RealtimeInboundFrameHandler.java` and `DefaultRealtimeInboundFrameHandler.java`
  - Why: `handleRawFrame` accepts both `sessionId` and `RealtimePrincipal`, while the handler also reads the session from `RealtimeSessionRegistry`. This creates two sources of truth for the authenticated session identity.
  - Impact: a custom adapter can pass a principal that does not match the registered session. Authorization and subscription ownership can then be evaluated against the wrong identity.
  - Recommended fix: remove `RealtimePrincipal` from `RealtimeInboundFrameHandler.handleRawFrame` and derive it from `RealtimeSessionRegistry`, or fail closed when the provided principal does not equal the registered session principal.

- `chatappBE/common/common-websocket/build.gradle`, `RealtimeEventFrame.java`, and `chatappBE/common/common-events/src/main/java/com/example/common/event/**`
  - Why: `common-websocket` exposes `common-events` as an `api` dependency only because `RealtimeEventFrame` uses `EventEnvelope<?>`. `common-events` also contains chat, presence, friendship, account, notification, and user event catalogs and payload contracts.
  - Impact: every consumer of common websocket inherits the application event catalog and domain-shaped payload model. That makes the websocket layer fake-generic even though the core websocket contract should only need a transport-neutral event envelope.
  - Recommended fix: split generic event envelope/metadata into a small common event-contract module or package with no domain catalog, then make `common-websocket` depend only on that. Keep domain event catalogs outside the websocket base.

- `chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/adapter/spring/SpringRealtimeLifecycleAdapter.java` and missing common Spring handler/registration classes
  - Why: common provides lifecycle methods but no reusable `TextWebSocketHandler`, `WebSocketConfigurer`, endpoint registration contract, or endpoint properties.
  - Impact: each service still has to write the Spring transport bridge, which is exactly where connection, text-message, disconnect, and transport-error flow can drift.
  - Recommended fix: add a common Spring handler adapter that delegates to `SpringRealtimeLifecycleAdapter`, plus a minimal endpoint registration abstraction or properties class. Services should configure endpoint path and auth beans, not duplicate lifecycle glue.

### Medium
- `chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/subscription/RealtimeDestination.java` / `RealtimeDestination`
  - Why: one class represents both outbound delivery targets and client subscription topics. `USER`, `SESSION`, and `GLOBAL` are direct send targets, while `CHANNEL` is subscription-routed. `USER` also hardcodes UUID identifiers.
  - Impact: authorization, subscription, and broadcast semantics are mixed. Future services with non-UUID subjects, tenant scopes, or typed topics will either overload `CHANNEL` strings or fork the abstraction.
  - Recommended fix: split outbound target from subscribable topic, or add a typed destination model with namespace, scope, and identifier value objects. Avoid UUID parsing in the generic destination core unless the whole platform has committed to UUID user subjects.

- `chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/identity/RealtimePrincipal.java`, `RealtimeIdentity.java`, and `SpringRealtimeHandshakeHandler.java`
  - Why: `RealtimePrincipal.userId()` says null can mean anonymous or system clients, but `RealtimeIdentity` rejects authorities when `userId` is null, and the Spring handshake handler returns null for non-user principals with authorities.
  - Impact: system clients and authenticated non-user principals cannot be represented consistently. Services may handle the same principal differently depending on whether it is a record or a custom implementation.
  - Recommended fix: define explicit principal kinds, such as `USER`, `ANONYMOUS`, `SYSTEM`, and validate authorities per kind. Keep Spring `Principal` adaptation in the Spring adapter instead of embedding it in the core identity record.

- `chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/adapter/spring/SpringRealtimeSession.java`
  - Why: `attributes()` returns `delegate.getAttributes()` directly from Spring.
  - Impact: the transport-neutral `RealtimeSession` exposes a mutable Spring-owned attribute map. This leaks adapter state and lets callers mutate session attributes outside the lifecycle boundary.
  - Recommended fix: copy the attributes into an immutable map at connection time, or expose only common-owned attributes in `RealtimeSession`.

- `chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/auth/RealtimeAuthorizationPolicy.java`
  - Why: authorization returns only boolean decisions and has no typed denial reason, no exception contract, and no explicit outbound authorization hook.
  - Impact: observers and error frames fall back to generic labels like `AUTHORIZATION_DENIED` and `SUBSCRIBE_DENIED`; services cannot standardize denial reporting without side channels.
  - Recommended fix: introduce a small `RealtimeAuthorizationDecision` with `allowed`, `reasonCode`, and safe client message. Keep boolean helpers only as compatibility shims if needed.

- `chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/adapter/spring/SpringRealtimeConnectionManager.java`
  - Why: `connect` updates the physical session map before running replacement cleanup and common registry mutation. If callback code throws, the transport map and common registries can diverge.
  - Impact: a custom registry failure can leave a live physical session that is not registered in common state, making inbound and outbound flow fail closed unpredictably.
  - Recommended fix: wrap connect mutations in an error boundary, rollback the session map on common mutation failure, and notify observer with a typed lifecycle failure.

- `chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/frame/RealtimeCommandFrame.java`
  - Why: docs say `correlationId` is optional on all command types, but the constructor forbids correlation IDs for `PING` and `PONG`. The command success contract is also hard-coded as silent success with no ACK or result frame.
  - Impact: clients cannot correlate ping/pong latency through the standard envelope, and services cannot opt into acknowledged subscription flows without leaving the common contract.
  - Recommended fix: align docs and validation. Either allow ping/pong correlation IDs and add a standard optional ACK/result frame, or explicitly document this as a deliberate protocol restriction.

- `chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/codec/JsonRealtimeFrameCodec.java` and `RealtimeEventFrame.java`
  - Why: event-frame decoding has no payload type resolution and no call into `SharedEventCatalog.validatePayloadContract`. `EventEnvelope<?>` payloads decode as generic maps.
  - Impact: the common layer claims `EventEnvelope` is canonical, but it cannot validate or round-trip typed event payload contracts in the websocket codec.
  - Recommended fix: either make websocket event frames opaque outbound-only and document that, or inject a generic event payload resolver from the event-contract layer.

- `chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/observer/MicrometerRealtimeObserver.java` and `RealtimeWebSocketAutoConfiguration.java`
  - Why: `MicrometerRealtimeObserver` says it is loaded when Micrometer and a `MeterRegistry` are available, but auto-configuration never creates it.
  - Impact: metrics support looks production-ready but is actually manual-only. Services expecting common metrics get the `NoOpRealtimeObserver`.
  - Recommended fix: either add conditional auto-configuration for Micrometer and composition with logging/no-op, or remove the misleading "loaded" claim.

### Low
- `chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/session/InMemoryRealtimeSessionRegistry.java` and `subscription/InMemoryRealtimeSubscriptionRegistry.java`
  - Why: public registry methods do not consistently validate null or blank arguments before hitting `ConcurrentHashMap` or record constructors.
  - Impact: callers get inconsistent `NullPointerException` vs `IllegalArgumentException` behavior.
  - Recommended fix: add explicit argument validation to all registry APIs.

- `chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/sender/DefaultRealtimeBroadcaster.java`
  - Why: `sendToDestination(USER)` reparses a UUID that `RealtimeDestination` already validates.
  - Impact: redundant defensive logic makes destination ownership unclear and hides invalid destination problems by returning an empty result.
  - Recommended fix: expose typed accessors on destination value objects or trust the constructor invariant and fail consistently.

- `chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/auth/AllowAllRealtimeAuthorizationPolicy.java`
  - Why: an allow-all policy lives in main production code.
  - Impact: it is not auto-configured, but it is easy for services to wire it accidentally and bypass meaningful authorization.
  - Recommended fix: move it to test fixtures/dev support, or rename it to `DevelopmentAllowAllRealtimeAuthorizationPolicy` and mark it clearly as non-production.

- `chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/adapter/spring/QueryParamRealtimeHandshakeTokenResolver.java`
  - Why: the only provided concrete token resolver reads query parameters, which are discouraged for production token transport.
  - Impact: services may copy the only available implementation even though the comment warns against it.
  - Recommended fix: add header and cookie resolvers as first-class options, and keep query-param resolver as compatibility-only.

- `chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/adapter/spring/SpringRealtimeConnectionManager.java`
  - Why: `REASON_DEAD_SESSION` is declared but unused.
  - Impact: stale lifecycle vocabulary remains in the common API surface, even if package-private.
  - Recommended fix: remove it or implement the missing dead-session cleanup path.

- `chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/adapter/spring/SpringRealtimeConnectionManager.java`
  - Why: comments still say the lock entry is removed "last", while the current implementation intentionally never removes it.
  - Impact: maintainers get conflicting guidance around the most delicate race-safety code.
  - Recommended fix: update comments after replacing the lock strategy.

- `chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/subscription/InMemoryRealtimeSubscriptionRegistry.java` and several test files
  - Why: some comments contain mojibake from dash/arrow characters.
  - Impact: low runtime impact, but not freeze-quality source hygiene.
  - Recommended fix: normalize comments to ASCII or correct UTF-8 consistently.

- `chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/subscription/InMemoryRealtimeSubscriptionRegistry.java` and `session/InMemoryRealtimeSessionRegistry.java`
  - Why: classes import types from their own package.
  - Impact: harmless but noisy.
  - Recommended fix: remove redundant imports during cleanup.

## 5. Standardization Gaps
- Naming inconsistencies: module/package says `websocket`, most classes say `Realtime`, config says `RealtimeWebSocket`, and Spring token resolver classes do not consistently carry the `Spring` prefix even though they use Spring request types.
- Package structure inconsistencies: core contracts and Spring adapter implementations live in the same Gradle module. This is acceptable for now, but freeze should either declare this module as Spring-backed or split core vs Spring adapter modules.
- Interface vs implementation inconsistencies: `RealtimeSession` is transport-neutral, but `SpringRealtimeSession.attributes()` exposes Spring attributes directly. `RealtimeIdentity` is core identity but implements Java `Principal` specifically for Spring compatibility.
- Lifecycle inconsistencies: common has lifecycle methods but no common `WebSocketHandler` or endpoint registration, so the most important lifecycle bridge remains service-specific.
- Message flow inconsistencies: `RealtimeDestination` is both a delivery target and a subscription destination; `USER`, `SESSION`, and `GLOBAL` bypass subscriptions, while `CHANNEL` requires subscriptions.
- Authentication/authorization inconsistencies: handshake auth has token extraction, identity resolution, and connect authorization; inbound auth has subscribe authorization only; outbound delivery has no common authorization contract.
- Subscription/session inconsistencies: inbound flow passes both principal and session ID, while the registry already owns the session-principal binding.
- Envelope inconsistencies: command frames carry command metadata directly; error frames carry `RealtimeError`; event frames carry `EventEnvelope<?>` from a domain-heavy common-events module.
- Observability inconsistencies: observer callbacks use string labels for many reasons while send failures use a typed enum.
- Auto-configuration inconsistencies: core registries, codec, sender, and broadcaster are auto-configured, but token resolver, handler registration, Micrometer observer, and logging observer are manual.

## 6. Coupling / Boundary Violations
- `RealtimeEventFrame` is appropriate as a websocket concept, but its direct dependency on `com.example.common.event.EventEnvelope` pulls websocket into the full `common-events` module. The generic envelope should be separated from domain event catalogs before websocket is frozen.
- `EventMetadata`, `EventContractValidator`, and `SharedEventCatalog` are related only because websocket exposes `EventEnvelope`. The catalog imports chat, presence, friendship, account, notification, and user payload contracts, which is too domain-shaped for a generic websocket base.
- `RealtimeIdentity` implements `java.security.Principal` for Spring handshake compatibility. That adapter concern should live in `adapter.spring`, not in the core identity implementation.
- `SpringRealtimeSession.attributes()` leaks mutable Spring session attributes through the common `RealtimeSession` contract.
- `SpringRealtimeConnectionManager`, `SpringRealtimeMessageSender`, `SpringRealtimeLifecycleAdapter`, `SpringRealtimeHandshakeInterceptor`, and `SpringRealtimeHandshakeHandler` are valid adapter classes, but they should be clearly separated as Spring adapter infrastructure, not treated as the generic websocket core.
- `AllowAllRealtimeAuthorizationPolicy` is development/test policy living in production main code.
- `QueryParamRealtimeHandshakeTokenResolver` is a compatibility resolver living beside production handshake abstractions; it should not be the only provided concrete resolver.
- `RealtimeDestination.USER` assumes platform user identity is UUID. That may be acceptable for this application, but it is not a generic realtime routing contract unless the common platform contract explicitly says all user subjects are UUIDs.

## 7. Redundant / Deprecated / Removable Code
- `SpringRealtimeConnectionManager.REASON_DEAD_SESSION`: unused constant; remove or implement the cleanup path.
- `AllowAllRealtimeAuthorizationPolicy`: move to test/dev support or clearly mark as development-only.
- `QueryParamRealtimeHandshakeTokenResolver`: keep only as compatibility support after adding production-safe header/cookie resolvers; otherwise move out of the default adapter package.
- `MicrometerRealtimeObserver`: currently not auto-configured despite its Javadoc. Either wire it in `RealtimeWebSocketAutoConfiguration` or remove the "loaded when available" claim.
- `WebSocketContractGuardTest` obsolete-class assertions: useful as migration guards now, but they are transitional. After freeze, keep only the behavior/contract guards that protect current public APIs.
- `InMemoryRealtimeSubscriptionRegistry` and `InMemoryRealtimeSessionRegistry`: redundant self-package imports are removable.
- Mojibake comments in main/test sources: not functional dead code, but should be cleaned before freeze.

## 8. Freeze Readiness
- NO
- Blocker: `SpringRealtimeConnectionManager.sessionLocks` leaks one lock per historical session ID.
- Blocker: `RealtimeEventFrame` makes the websocket API depend on the domain-heavy `common-events` module instead of a minimal event envelope contract.
- Blocker: common does not provide a reusable Spring websocket handler/endpoint registration standard, so services still own lifecycle glue.
- Blocker: inbound auth/session flow has two identity sources (`sessionId` plus caller-provided `RealtimePrincipal`).
- Blocker: `RealtimeDestination` mixes subscription topics and direct delivery targets, and hardcodes UUID user routing.
- Blocker: identity semantics for anonymous/system/authenticated principals are inconsistent across `RealtimePrincipal`, `RealtimeIdentity`, and `SpringRealtimeHandshakeHandler`.

## 9. Refactor Priority Order
- Fix `SpringRealtimeConnectionManager` lock retention first, because it is the clearest production blocker.
- Collapse inbound identity to one source of truth by deriving the principal from `RealtimeSessionRegistry` or validating it strictly.
- Split the generic event envelope/metadata contract away from the domain event catalog, then narrow `common-websocket`'s `api` dependency.
- Add a common Spring `TextWebSocketHandler` adapter and endpoint registration/property contract so services do not duplicate lifecycle glue.
- Redesign `RealtimeDestination` into separate outbound target and subscription topic semantics, or add namespaced typed destination value objects.
- Standardize auth decisions with typed denial reasons and align observer/error labels with those reasons.
- Move Spring-specific identity adaptation out of core identity, and normalize system/anonymous principal semantics.
- Clean adapter utilities: add production token resolvers, move allow-all policy to dev/test support, and make Micrometer/logging observer behavior match auto-configuration docs.
- Finish low-level cleanup: registry argument validation, stale comments, unused constants, redundant imports, and mojibake comments.
