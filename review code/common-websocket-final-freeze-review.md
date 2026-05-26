## 1. Executive Summary

- Freeze-ready as the shared realtime standard: no.
- Biggest remaining blockers:
  - Public codec still exposes a typed decode bypass in addition to the discriminator-based decode path.
  - Frame, destination, subscription, and identity invariants are not fully enforced at construction boundaries.
  - Subscription authorization is defined but not enforced by any common inbound command pipeline.
  - Session replacement/index maintenance is sequentially improved, but not atomic under concurrent replacement.
  - Spring adapter auto-configuration is incomplete and can fail when no `RealtimeMessageSender` bean exists.
  - Credential log safety is not strict enough because adapter logging includes decoder exception messages.
  - `common-web` still owns a domain-heavy realtime flow policy package that overlaps with the common realtime standard.
- What improved after the refactor:
  - Core public send APIs now use `RealtimeFrame`, not raw payload objects.
  - Core public contracts do not expose Spring `WebSocketSession`.
  - Old room-only broadcaster/session APIs and old websocket message wrapper classes are removed from source.
  - Semantic outbound events now use `EventEnvelope<?>` through `RealtimeEventFrame`.
  - Kafka and Redis are not hardwired into the websocket core.
  - Spring-specific classes are under `adapter.spring`.
- Services were ignored. No service modules were inspected, reviewed, or used for compatibility judgment.

## 2. Scope

- Reviewed only `chatappBE/common`.
- Service modules were ignored.
- Common modules/packages inspected:
  - `common-websocket`: all main and test packages under `com.example.common.websocket`.
  - `common-events`: `com.example.common.event` and shared catalog/payload package references needed to validate `EventEnvelope<?>`.
  - `common-security`: `com.example.common.security.jwt`.
  - `common-core`: build dependency boundary only.
  - `common-web`: `com.example.common.realtime.policy` and build dependency boundary.
  - `common-kafka`: package search across `com.example.common.kafka.*`; direct reads of `flow`, `topic`, and build dependency boundary.
  - `common-redis`: package search across `com.example.common.redis.*`; direct reads of `flow`, `channel`, and build dependency boundary.

## 3. Current Structure

`common-websocket`:

- `adapter.spring`: `HandshakeTokenResolver`, `JwtRealtimeIdentityResolver`, `QueryParamTokenResolver`, `SpringHandshakeInterceptor`, `SpringJwtHandshakeHandler`, `SpringRealtimeMessageSender`, `SpringRealtimeSession`.
- `adapter.spring.config`: `WebSocketAutoConfiguration`.
- `auth`: `RealtimeIdentityResolver`, `RealtimeAuthorizationPolicy`, `NoOpRealtimeAuthorizationPolicy`.
- `codec`: `RealtimeFrameCodec`, `JsonRealtimeFrameCodec`, `RealtimeCodecException`.
- `error`: `RealtimeError`, `RealtimeErrorCode`.
- `frame`: `RealtimeFrame`, `RealtimeEventFrame`, `RealtimeCommandFrame`, `RealtimeCommandType`, `RealtimeErrorFrame`.
- `identity`: `RealtimePrincipal`, `RealtimeIdentity`.
- `observer`: `RealtimeObserver`, `NoOpRealtimeObserver`, `LoggingRealtimeObserver`, `MicrometerRealtimeObserver`.
- `registry`: `InMemoryRealtimeSessionRegistry`, `InMemoryRealtimeSubscriptionRegistry`.
- `sender`: `RealtimeMessageSender`, `RealtimeBroadcaster`, `RealtimeSendResult`, `DefaultRealtimeBroadcaster`.
- `session`: `RealtimeSession`, `RealtimeSessionRegistry`.
- `subscription`: `RealtimeDestination`, `RealtimeDestinationType`, `RealtimeSubscription`, `RealtimeSubscriptionRegistry`.
- Resources: Spring auto-configuration import.
- Tests: adapter handshake, auth policy, frame contract, guard tests, observer callbacks, registries, broadcaster, destination.

Directly related common modules inspected for boundaries:

- `common-events`: canonical `EventEnvelope<T>`, `EventMetadata`, payload registry, shared event catalog.
- `common-security`: JWT helper used only by the Spring JWT identity adapter.
- `common-kafka`: transport-specific event producer/consumer/serializer/routing/topic primitives built around `EventEnvelope<?>`.
- `common-redis`: transport-specific pub/sub publisher/subscriber/serializer/routing/channel primitives built around `EventEnvelope<?>`.
- `common-web`: realtime flow policy package.
- `common-core`: dependency root for common-security/common-web.

## 4. Responsibility and Boundary Review

`common-websocket` now owns the right primitive categories in broad shape: identity, session, destination, subscription, send/broadcast, frame/error model, codec, auth/authz hooks, lifecycle observer hooks, and Spring adapters.

Ownership is not yet strong enough for a frozen shared realtime standard:

- It owns frame and codec types, but the public codec still permits bypassing the shared discriminator path.
- It owns authz hooks, but subscribe authorization is not connected to any common inbound command path.
- It owns registries, but registry invariants and concurrency behavior are not strict enough.
- It owns adapter pieces, but no complete common Spring adapter lifecycle is auto-configured.

Business/domain logic inside `common-websocket` is mostly absent. The old room-only APIs are gone. The only domain-ish residue is in examples and stale wording, not in the main public API shape.

Transport/framework concerns are mostly isolated by package:

- Spring classes are under `adapter.spring`.
- Core contracts do not expose Spring `WebSocketSession`.
- Kafka and Redis are not referenced by websocket core.

The remaining boundary weakness is artifact-level, not package-level: the module carries Spring and common-security dependencies because adapter classes live in the same artifact as the core primitives. That is acceptable only if the project intentionally freezes a combined core-plus-Spring-adapter module. If the standard must be transport-neutral as an artifact, split core and adapter packages/modules before freeze.

Kafka/Redis/common-events/common-security integration boundaries:

- `common-events` is correctly used as the semantic event owner through `EventEnvelope<?>`.
- Kafka and Redis remain transport layers and do not define websocket frames.
- `common-security` is only used by `JwtRealtimeIdentityResolver`, which is a Spring/JWT adapter concern.
- `common-web` is not cleanly bounded because it still contains a domain-heavy realtime policy catalog outside `common-websocket`.

## 5. Dependency Direction

- `common-websocket` depends on `common-events` for `EventEnvelope<?>`; this direction is appropriate.
- `common-websocket` depends on `common-security` only for the JWT identity adapter; this is package-bounded but not artifact-bounded.
- `common-kafka` and `common-redis` depend on `common-events`, not on `common-websocket`; this direction is clean.
- `common-web` depends on `common-core`, not on `common-websocket`, but it still owns realtime policy concepts that conflict with the new standard.
- No circular dependency was found inside the reviewed common scope.
- Spring coupling is isolated to `adapter.spring` classes, but the Gradle module still carries Spring dependencies for the whole artifact.

## 6. Package / API Design Review

### Issue 1

- Severity: High
- File/class: `common-websocket/src/main/java/com/example/common/websocket/codec/RealtimeFrameCodec.java`, `RealtimeFrameCodec`; `JsonRealtimeFrameCodec.java`, `JsonRealtimeFrameCodec`
- Finding: `RealtimeFrameCodec` still exposes `<T> T decode(String raw, Class<T> frameType)` at line 28, while `decode(String raw)` exists at line 38. `JsonRealtimeFrameCodec` implements the bypass at lines 34-39 and uses it internally at lines 56-58.
- Why it matters before freeze: this violates the requirement for a single discriminator-based inbound decode path. It also lets callers decode arbitrary target classes outside the frozen frame standard.
- Recommended fix: remove the public typed decode method from `RealtimeFrameCodec`. Keep any concrete-class decode helper private inside `JsonRealtimeFrameCodec`, constrained to the sealed `RealtimeFrame` subtypes.

### Issue 2

- Severity: High
- File/class: `RealtimeEventFrame.java`, `RealtimeCommandFrame.java`, `RealtimeErrorFrame.java`
- Finding: the records expose `frameType` as a public constructor component but do not enforce that it equals the subtype constant. For example, `RealtimeEventFrame` validates only `payload`; `RealtimeErrorFrame` validates only `error`; `RealtimeCommandFrame` validates only command/destination combinations.
- Why it matters before freeze: callers and JSON binding can create inconsistent frames such as an event frame with `frameType="COMMAND"`. The codec trusts `frameType` as the discriminator, so this breaks the wire contract.
- Recommended fix: remove `frameType` from public constructors if possible and return constants from `frameType()`, or enforce exact constant equality in compact constructors. Add tests for null, wrong, and mismatched discriminator values.

### Issue 3

- Severity: High
- File/class: `RealtimeAuthorizationPolicy.java`, `RealtimeAuthorizationPolicy`; `SpringHandshakeInterceptor.java`, `SpringHandshakeInterceptor`
- Finding: `allowSubscribe(...)` exists at line 27, but production code only uses connect authorization in `SpringHandshakeInterceptor`. There is no common inbound frame handler that decodes `SUBSCRIBE` and applies subscription authz before registry mutation.
- Why it matters before freeze: services would have to duplicate subscribe/unsubscribe handling, authz enforcement, error frames, observer calls, and registry lifecycle. That means the shared standard is a model library, not a complete reusable realtime foundation.
- Recommended fix: add a common inbound command processor, for example `RealtimeInboundFrameHandler`, that accepts raw text or `RealtimeFrame`, uses the single codec entry path, handles `SUBSCRIBE`, `UNSUBSCRIBE`, `PING`, `PONG`, and `ACK`, calls `allowSubscribe`, mutates `RealtimeSubscriptionRegistry`, emits `RealtimeErrorFrame` through `RealtimeMessageSender`, and reports observer hooks. Then provide a Spring adapter that delegates to it.

### Issue 4

- Severity: High
- File/class: `InMemoryRealtimeSessionRegistry.java`, `InMemoryRealtimeSessionRegistry`
- Finding: sequential replacement is fixed, but `register(...)` at lines 19-35 is not atomic across `bySessionId` and `byUserId`.
- Why it matters before freeze: concurrent registration of the same session ID for two principals can leave the old user index pointing at a session now owned by a different principal. The class claims thread safety, so this stale index case must be closed.
- Recommended fix: make replacement atomic with a single lock around both maps or a purpose-built internal state object. Add concurrent replacement tests and unregister-after-replace tests.

### Issue 5

- Severity: High
- File/class: `RealtimeDestination.java`, `RealtimeDestination`; `RealtimeSubscription.java`, `RealtimeSubscription`
- Finding: `RealtimeDestination` validates null/blank shape but allows `new RealtimeDestination(USER, "not-a-uuid")`. `RealtimeSubscription` has no compact constructor and does not validate `subscriptionId`, `sessionId`, `destination`, or `subscribedAt`. `RealtimeDestination.isValid()` always returns true at line 55 and is explicitly retained as stale API.
- Why it matters before freeze: the standard requires frame/destination/subscription invariants to be enforced. A frozen public record constructor that allows malformed destinations or subscriptions becomes hard to correct later.
- Recommended fix: enforce per-type destination invariants in the constructor, including UUID syntax for `USER`; remove `isValid()`; add a compact constructor to `RealtimeSubscription`; add null/blank tests for subscription and invalid identifier tests for destinations.

### Issue 6

- Severity: High
- File/class: `WebSocketAutoConfiguration.java`, `WebSocketAutoConfiguration`
- Finding: auto-config creates `RealtimeBroadcaster` at lines 62-69 and requires a `RealtimeMessageSender`, but the same auto-config does not create `SpringRealtimeMessageSender` and is not conditional on a sender bean.
- Why it matters before freeze: a consuming Spring context can fail simply by having this module on the classpath without a sender bean. The adapter is not a reliable reusable boundary yet.
- Recommended fix: either auto-configure `SpringRealtimeMessageSender` and the needed lifecycle pieces, or guard the broadcaster bean with a sender-bean condition. Add `ApplicationContextRunner` tests for no-sender, custom-sender, and default-adapter scenarios.

### Issue 7

- Severity: High
- File/class: `JwtRealtimeIdentityResolver.java`, `JwtRealtimeIdentityResolver`; `LoggingRealtimeObserver.java`, `LoggingRealtimeObserver`
- Finding: `JwtRealtimeIdentityResolver` logs `JwtException.getMessage()` at line 38. `LoggingRealtimeObserver` logs arbitrary exception messages for decode/send failures.
- Why it matters before freeze: exception messages are not a safe boundary for credential material. Some decoders or parsers can include sensitive input fragments in exception text.
- Recommended fix: log structured failure labels only. Keep raw exception objects out of routine auth/frame logs unless a sanitizer is applied. Add log-capture tests that prove sample credential strings do not appear.

### Issue 8

- Severity: High
- File/class: `common-web/src/main/java/com/example/common/realtime/policy/*`
- Finding: `common-web` still owns `RealtimeFlowId`, `RealtimeFlowType`, and `RealtimeFlowClassificationPolicy` with chat, notification, friendship, and presence flow constants plus Kafka/Redis/WebSocket delivery semantics.
- Why it matters before freeze: this is a conflicting common-layer owner for realtime semantics outside `common-websocket` and outside `common-events`. It also keeps domain-specific realtime policy in `common-web`, which should remain HTTP/web utility territory.
- Recommended fix: remove common-web as the owner of realtime flow policy. If common still needs a shared semantic catalog, place transport-neutral event semantics under `common-events` and keep transport routing under Kafka/Redis adapters.

### Issue 9

- Severity: Medium
- File/class: `RealtimeObserver.java`, `SpringHandshakeInterceptor.java`, `SpringRealtimeMessageSender.java`
- Finding: `RealtimeObserver` says observer errors should not disrupt transport, but callers invoke observers directly without a safe wrapper.
- Why it matters before freeze: a custom observer can break handshake acceptance or send execution.
- Recommended fix: add a `SafeRealtimeObserver` decorator or central observer invoker that catches observer exceptions and optionally reports internal errors without recursion.

### Issue 10

- Severity: Medium
- File/class: `RealtimeIdentity.java`, `RealtimeIdentity`; `SpringJwtHandshakeHandler.java`, `SpringJwtHandshakeHandler`
- Finding: core `RealtimeIdentity` implements `java.security.Principal` for Spring handshake use and lacks constructor validation for `principalName` and authenticated `userId`. `SpringJwtHandshakeHandler` also drops custom principal attributes when converting a `RealtimePrincipal`.
- Why it matters before freeze: Spring adaptation is leaking into the core identity implementation, and invalid principals can enter registries or logs.
- Recommended fix: validate `principalName`, authorities, and attributes in `RealtimeIdentity`; require authenticated factory calls to receive a non-null user ID; move `Principal` adaptation fully into `adapter.spring` if artifact neutrality is desired; preserve attributes when adapting custom principals.

### Issue 11

- Severity: Medium
- File/class: `RealtimeCommandFrame.java`, `RealtimeCommandFrame`
- Finding: `ACK` is in `RealtimeCommandType`, but its required/forbidden fields are not defined. Current invariants allow either null or non-null destination and do not require a correlation ID.
- Why it matters before freeze: command semantics become ambiguous for clients and future adapters.
- Recommended fix: define command-specific invariants for `ACK`, then enforce them in the compact constructor and tests.

### Issue 12

- Severity: Medium
- File/class: `JsonRealtimeFrameCodec.java`, `JsonRealtimeFrameCodec`; `RealtimeEventFrame.java`, `RealtimeEventFrame`
- Finding: `RealtimeEventFrame` correctly wraps `EventEnvelope<?>`, but decode of event frames does not use `EventPayloadRegistry`, so object payloads can round-trip as untyped maps.
- Why it matters before freeze: if event frames are ever accepted inbound or looped through the codec, the semantic payload contract can be weakened.
- Recommended fix: either make event frames explicitly outbound-only and reject inbound `EVENT` frames in the common inbound handler, or integrate common-events payload resolution for typed event-frame decode.

### Issue 13

- Severity: Low
- File/class: `SpringJwtHandshakeHandler.java`, `SpringJwtHandshakeHandler`
- Finding: the class name says JWT, but the class only promotes a resolved realtime principal from handshake attributes.
- Why it matters before freeze: naming suggests JWT coupling where the behavior is actually generic Spring principal adaptation.
- Recommended fix: rename to `SpringRealtimeHandshakeHandler` or similar.

### Issue 14

- Severity: Low
- File/class: `SpringHandshakeInterceptor.java`, `SpringHandshakeInterceptor`; `RealtimeDestination.java`, `RealtimeDestination`
- Finding: stale wording remains: `SpringHandshakeInterceptor` references an old interceptor class in documentation, and `RealtimeDestination.isValid()` is retained as stale API.
- Why it matters before freeze: stale API/documentation makes the new standard look provisional.
- Recommended fix: remove stale references and remove the `isValid()` method.

## 7. Realtime Standard Readiness

- Session model: good abstraction shape, but registry replacement must become atomic and lifecycle cleanup needs stronger adapter support.
- Principal/identity model: useful shape, but constructor validation and Spring principal adaptation need cleanup.
- Inbound/outbound frame model: sealed frame model is a strong improvement, but discriminator invariants and command semantics are not frozen-safe yet.
- Subscription model: registry exists and duplicate prevention exists, but subscription invariants and authz enforcement are incomplete.
- Destination model: reusable destination abstraction exists, but per-type validation and stale `isValid()` must be fixed.
- Error model: structured error frame exists and is independent from HTTP response DTOs; details remain loosely typed but not a primary blocker.
- Observability/lifecycle model: observer hooks exist, but safe invocation is not enforced.
- Serialization/codec model: JSON codec exists and has discriminator decode, but public typed decode must be removed.
- Auth/authz extension points: connect authz is used; subscribe authz is not enforced in common production code.
- Send/broadcast abstractions: sender/broadcaster use `RealtimeFrame`; broadcaster behavior is reusable but needs more edge-case tests.
- Adapter layering: Spring classes are package-isolated, but auto-config and lifecycle composition are incomplete.

Overall readiness: close in shape, not ready to freeze.

## 8. Cross-Common Consistency

- `common-events`: consistent with websocket semantic outbound events. `RealtimeEventFrame` uses `EventEnvelope<?>`, and `common-events` remains the semantic event owner.
- `common-kafka`: clean direction. Kafka owns transport topics/routing and uses `EventEnvelope<?>`; it does not define websocket frames.
- `common-redis`: mostly clean direction. Redis owns pub/sub channel routing and uses `EventEnvelope<?>`; it does not define websocket frames. There is no shared mapper from `RealtimeDestination` to Redis channels yet, which is acceptable until a Redis websocket adapter exists.
- `common-core`: no realtime overlap found in reviewed scope.
- `common-security`: clean package-level use through `JwtRealtimeIdentityResolver`, but module-level coupling should be considered if a transport-neutral artifact is required.
- `common-web`: not clean. `com.example.common.realtime.policy` overlaps with realtime ownership and contains business-specific flow IDs and Kafka/Redis/WebSocket semantics.

EventEnvelope consistency: good for outbound semantic events, with one caveat around typed event-frame decode.

Naming consistency: improved inside websocket core. The main naming issues are `SpringJwtHandshakeHandler` and the external `common-web` realtime policy package.

Duplicate abstractions: no duplicate websocket frame/message standard remains in `common-websocket` source. `common-web` still contains a separate realtime policy abstraction that conflicts with standard ownership.

Destination/event ownership: `common-websocket` owns realtime destinations; `common-events` owns semantic events; Redis/Kafka own transport routes. `common-web` should not own realtime flow policy.

## 9. Freeze Blockers

### High: must fix before freeze

- Remove public typed codec decode bypass and keep only the discriminator-based public decode path.
- Enforce frame discriminator constants in every frame subtype.
- Add common inbound command handling that enforces subscribe authz, registry mutation, observer hooks, and error frames.
- Make session registry replacement/index updates atomic under concurrent registration.
- Enforce destination and subscription invariants; remove `RealtimeDestination.isValid()`.
- Fix Spring adapter auto-config so it does not fail without a sender bean and has tested default/custom paths.
- Stop logging decoder/parser exception messages that could contain credential fragments.
- Remove or relocate `common-web` realtime flow policy ownership from the common-web boundary.

### Medium: should fix before freeze

- Add safe observer invocation.
- Validate `RealtimeIdentity` construction and move Spring principal adaptation fully into the Spring adapter if artifact neutrality is required.
- Define `ACK` command invariants.
- Decide whether event frames are outbound-only or integrate typed event payload resolution on decode.
- Add lifecycle cleanup integration for Spring sender/session/subscription registries.

### Low: can fix later

- Rename `SpringJwtHandshakeHandler`.
- Remove stale refactor wording from Javadocs and tests.
- Keep `QueryParamTokenResolver` explicitly opt-in and add stronger warning tests/docs around query token risk.
- Consider immutable copies for session/principal attribute maps.

## 10. Minimal Remaining Refactor Plan

1. Tighten the frozen wire contract:
   - Remove public typed decode.
   - Enforce `frameType` constants.
   - Define `ACK` invariants.
   - Add missing frame invariant tests.

2. Complete the common inbound path:
   - Add a common inbound command handler.
   - Route all inbound JSON through `RealtimeFrameCodec.decode(String)`.
   - Enforce `allowSubscribe`.
   - Emit standard error frames on invalid frame, unknown destination, duplicate subscription, missing subscription, and denied authz.

3. Harden registries and models:
   - Make session index replacement atomic.
   - Validate `RealtimeSubscription`.
   - Strengthen `RealtimeDestination`.
   - Validate `RealtimeIdentity`.

4. Fix adapter safety:
   - Correct auto-config conditions/default beans.
   - Add safe observer invocation.
   - Remove unsafe exception-message logging.
   - Add lifecycle glue for Spring session register/unregister and subscription cleanup.

5. Resolve cross-common ownership:
   - Remove `common-web` as a realtime policy owner.
   - Keep semantic events in `common-events`.
   - Keep transport routes in `common-kafka` and `common-redis`.

No service module steps are included.

## 11. Required Tests Before Freeze

- Handshake log safety:
  - Capture logs from `SpringHandshakeInterceptor`, `JwtRealtimeIdentityResolver`, and `LoggingRealtimeObserver`.
  - Assert sample credential strings never appear, including decoder exception messages.

- Session replacement correctness:
  - Sequential same-session replacement between different users.
  - Concurrent same-session replacement between different users.
  - Unregister after replacement does not corrupt the current user index.

- Typed frame/send/codec contracts:
  - Reflection guard that `RealtimeFrameCodec` exposes only `encode(RealtimeFrame)` and `decode(String)`.
  - Guard that `RealtimeMessageSender` and `RealtimeBroadcaster` accept only `RealtimeFrame`.

- Discriminator-based decode:
  - Unknown, missing, null, and non-text `frameType`.
  - Public constructors reject wrong discriminator constants.
  - Decoded subtype discriminator matches its subtype.

- Frame/destination/subscription invariants:
  - `USER` destination rejects non-UUID identifiers.
  - `SESSION` and `CHANNEL_GROUP` reject null/blank identifiers.
  - `GLOBAL` rejects identifiers.
  - `RealtimeSubscription` rejects null/blank required fields.
  - `ACK` command fields follow the frozen contract.

- Broadcaster/sender behavior:
  - Unknown session returns failure through sender.
  - Closed Spring session cleanup.
  - Per-session send lock behavior.
  - Mixed success/failure result collection.
  - No payload or credential logging on send failure.

- Adapter auto-config behavior:
  - Context starts with no sender bean if broadcaster is conditional.
  - Context starts with a default sender if sender is auto-configured.
  - Custom sender, codec, registries, broadcaster, observer override defaults.
  - Spring handshake handler/interceptor beans are covered if auto-configured.

- Auth/authz behavior:
  - Connect denied rejects handshake.
  - Subscribe denied emits `RealtimeErrorFrame` and does not create subscription.
  - Subscribe allowed creates one subscription and reports observer hook.
  - Unsubscribe missing emits standard error.

- Cross-common EventEnvelope integration:
  - `RealtimeEventFrame.of(EventEnvelope<?>)` serializes canonical metadata and payload field.
  - Object payload behavior is tested either as outbound-only or through payload registry resolution.
  - Kafka/Redis route constants do not leak into websocket destinations.
  - `common-web` realtime policy overlap is removed or no longer part of public common realtime ownership.

Current executed verification:

- `.\gradlew.bat :common:common-websocket:test --rerun-tasks` passed.

## 12. Final Verdict

- Freeze common-websocket now: no.
- Minimum common-only changes before freeze:
  - Remove public typed decode bypass.
  - Enforce frame/destination/subscription/identity invariants.
  - Add common inbound command handling with subscribe authz.
  - Make session replacement/index updates atomic.
  - Fix Spring adapter auto-config and lifecycle cleanup.
  - Remove unsafe exception-message logging.
  - Resolve `common-web` realtime policy overlap.
- What can wait until later:
  - Artifact split, if package-level adapter isolation is acceptable for now.
  - Handler naming cleanup.
  - Query-param token resolver ergonomics, as long as it remains opt-in and is never auto-selected.
  - Attribute-map typing/immutability polish.
- Services were not considered. No service-level blockers or service module steps are included.

