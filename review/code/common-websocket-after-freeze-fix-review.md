## 1. Executive Summary

- Freeze common-websocket now: no.
- Biggest remaining blockers: the Spring adapter/auto-config boundary is still too broad and incomplete for a shared standard, and several public common contracts still need freeze-grade hardening before they become difficult to change.
- What improved after the refactor: the core now has typed realtime primitives for principal, session, destination, subscription, frames, errors, codec, sender, broadcaster, inbound command handling, authorization, and observers. Core public contracts do not expose Spring WebSocketSession. Semantic outbound frames use EventEnvelope<?>. Kafka and Redis are not hardwired into the websocket core. The obvious old room-only and duplicate WebSocket DTO surface is gone.
- Services were ignored. This review did not evaluate service modules, service behavior, or service migration.

## 2. Scope

- Reviewed only inside `chatappBE/common`.
- Service modules were ignored.
- Primary target inspected: `common-websocket`.
- Related common modules inspected only for boundary validation:
  - `common-events`: `com.example.common.event`, `com.example.common.event.validation`, and catalog ownership references.
  - `common-security`: `com.example.common.security.jwt`.
  - `common-kafka`: event envelope serialization and package-level boundary search.
  - `common-redis`: event envelope serialization and package-level boundary search.
  - `common-core`: package/file search for realtime overlap.
  - `common-web`: package/file search for realtime overlap.
- Common-only verification run: `:common:common-websocket:test`, 98 tests across 15 suites, all passing.

## 3. Current Structure

`common-websocket` structure:

- `adapter.spring`: Spring WebSocket, handshake, token resolver, JWT identity resolver, Spring sender/session adapters.
- `adapter.spring.config`: Spring Boot auto-configuration import target.
- `auth`: identity resolver and authorization policy extension points.
- `codec`: typed frame codec and JSON implementation.
- `error`: realtime error payload and codes.
- `frame`: sealed frame model for event, command, and error frames.
- `identity`: canonical realtime principal and immutable identity implementation.
- `inbound`: common inbound command handler.
- `observer`: lifecycle and observability hooks.
- `registry`: in-memory session and subscription registries.
- `sender`: sender, broadcaster, send result, and default broadcaster.
- `session`: transport-independent session abstraction and registry contract.
- `subscription`: destination, destination type, subscription, and subscription registry contract.

Related common structure inspected:

- `common-events`: EventEnvelope, EventMetadata, event payload registry, shared event catalog, event contract validator.
- `common-security`: JwtHelper for JWT user and authority extraction.
- `common-kafka`: Kafka producer/consumer/serialization/observability packages use EventEnvelope and do not depend on websocket.
- `common-redis`: Redis publisher/subscriber/dispatcher/serialization/observability packages use EventEnvelope and do not depend on websocket.
- `common-web`: HTTP controller, response, exception, CORS, and filter utilities. No competing realtime owner found.
- `common-core`: exception and pipeline utilities. No competing realtime owner found.

## 4. Responsibility and Boundary Review

common-websocket now owns the right realtime primitives:

- identity/principal
- session/connection
- destination
- subscription
- send/broadcast abstractions
- inbound/outbound frame model
- error frame/model
- codec abstraction
- auth/authz extension points
- lifecycle/observer hooks

Ownership is mostly appropriate. The module no longer reads like a business-specific room helper, and there is no direct domain command vocabulary in the common-websocket API. `RealtimeDestination.channel(...)` is generic enough; it is not a room-only standard.

Business/domain logic did not leak into common-websocket. Domain payload ownership remains in `common-events`, where shared event payloads and the event catalog live.

Transport/framework isolation is good at package level but not freeze-clean at module level. Spring imports are confined to `adapter.spring` and `adapter.spring.config`, but `common-websocket/build.gradle` directly brings Spring WebSocket and OAuth2 resource server dependencies into the common-websocket artifact. That weakens the claim that the module is a reusable realtime foundation rather than a Spring-bearing bundle with a core package inside it.

Kafka and Redis boundaries are clean. common-websocket does not depend on common-kafka or common-redis and uses destination semantics that are explicitly separate from broker topics/channels.

common-security integration is clean in concept: JWT extraction is delegated to `JwtHelper` from the Spring adapter, not reimplemented in websocket core. The build boundary still needs cleanup if the adapter remains public.

common-web no longer overlaps as a realtime owner. It contains HTTP/web utilities only in the reviewed common scope.

## 5. Dependency Direction

- `common-websocket` depends on `common-events` for EventEnvelope<?> in outbound event frames.
- `common-websocket` depends on `common-security` only for the Spring JWT identity adapter.
- `common-websocket` does not depend on `common-kafka`, `common-redis`, `common-web`, or `common-core`.
- No circular dependency was found inside the reviewed common scope.
- Spring coupling is isolated by Java package but not fully isolated by build/module boundary.
- Kafka and Redis depend on `common-events`, not on `common-websocket`, which is the right direction.
- common-web has no competing realtime dependency or abstraction in the reviewed scope.

## 6. Package / API Design Review

### Issue 1: Spring adapter boundary is not freeze-clean

- Severity: High.
- Exact file/class: `common-websocket/build.gradle`; `com.example.common.websocket.adapter.spring.config.WebSocketAutoConfiguration`.
- Why it matters before freeze: the core public contracts are transport-independent, but the module itself directly carries Spring WebSocket and OAuth2 adapter dependencies. The auto-config condition checks for `WebSocketSession`, but this module itself supplies that class through its dependency set, so the guard is too broad. The auto-config also does not create the handshake interceptor/handler boundary even when the required token, identity, and authz beans exist. That leaves the common standard only partially auto-configured.
- Exact recommended fix: split transport-independent core and Spring adapter into separate common modules, or otherwise make the Spring adapter explicitly opt-in at the build boundary. Add `@ConditionalOnWebApplication` or an explicit common websocket property guard. Add conditional beans for `SpringHandshakeInterceptor` and the handshake handler when `HandshakeTokenResolver`, `RealtimeIdentityResolver`, `RealtimeAuthorizationPolicy`, and `RealtimeObserver` are present. Keep fail-closed behavior when authz is missing.

### Issue 2: Subscribe duplicate handling is not atomic in the common inbound path

- Severity: Medium.
- Exact file/class: `com.example.common.websocket.inbound.DefaultRealtimeInboundFrameHandler`.
- Why it matters before freeze: `handleSubscribe` checks `listBySession(...)` and then calls `subscribe(...)`. Concurrent duplicate SUBSCRIBE frames for the same session/destination can both pass the pre-check, both observe success, and both emit `onSubscribed`, while only one registry entry exists. The registry prevents duplicate storage, but the common inbound command contract can still report the wrong semantic outcome.
- Exact recommended fix: make the registry operation return an atomic created/existing result, for example `RealtimeSubscriptionResult(subscription, created)`, or add `subscribeIfAbsent`. The inbound handler should send `SUBSCRIPTION_DUPLICATE` and skip `onSubscribed` based on that atomic result.

### Issue 3: Observer callbacks expose raw inbound payloads

- Severity: Medium.
- Exact file/class: `com.example.common.websocket.observer.RealtimeObserver`; `com.example.common.websocket.inbound.DefaultRealtimeInboundFrameHandler`.
- Why it matters before freeze: common's built-in logging observer avoids raw payload logging, but the frozen observer API passes raw wire payloads to any observer implementation. That makes credential or sensitive payload leakage easy in future common observers or application observers, and changing this later would alter a public API.
- Exact recommended fix: replace raw payload observer parameters with safe metadata such as payload length, parsed frame type, correlation id, and failure reason. If raw payload inspection is still needed, make it a separate explicit debug hook with a redaction contract.

### Issue 4: RealtimeError lacks invariant enforcement

- Severity: Medium.
- Exact file/class: `com.example.common.websocket.error.RealtimeError`.
- Why it matters before freeze: the error frame wrapper requires a non-null `RealtimeError`, but `RealtimeError` itself can carry null code, blank message, mutable details, and ambiguous blank correlation ids. The error model is part of the frozen wire standard.
- Exact recommended fix: add a compact constructor that rejects null code and blank message, normalizes blank correlation id to null or rejects it consistently, and defensively copies `details` with `Map.copyOf(...)` when present.

### Issue 5: EventEnvelope outbound integration is looser than Kafka/Redis

- Severity: Medium.
- Exact file/class: `com.example.common.websocket.frame.RealtimeEventFrame`; `com.example.common.websocket.codec.JsonRealtimeFrameCodec`; related: `com.example.common.event.SharedEventCatalog`.
- Why it matters before freeze: Kafka and Redis serializers validate shared event identity and payload contracts before transport. WebSocket event frames correctly use EventEnvelope<?>, but they currently accept any envelope that passes the EventEnvelope constructor. That can make WebSocket a looser semantic event path than the other common transports.
- Exact recommended fix: add a common-websocket event-envelope validator hook. If WebSocket should only carry shared catalog events, validate against `SharedEventCatalog.isKnownEventType(...)` and `SharedEventCatalog.validatePayloadContract(...)`. If service-local events are intentionally allowed, document that extension point and test it explicitly.

### Issue 6: Spring handshake handler does not faithfully preserve generic RealtimePrincipal

- Severity: Medium.
- Exact file/class: `com.example.common.websocket.adapter.spring.SpringJwtHandshakeHandler`.
- Why it matters before freeze: the handler converts a non-`RealtimeIdentity` `RealtimePrincipal` with `RealtimeIdentity.of(...)`, which requires a non-null user id and drops attributes. The core `RealtimePrincipal` contract says user id may be null for anonymous/system clients and includes attributes as part of identity context.
- Exact recommended fix: rename this handler to a transport-focused name such as `SpringRealtimeHandshakeHandler`, preserve `RealtimeIdentity` as-is, and convert generic principals through a factory that handles null user id and preserves attributes.

### Issue 7: Spring sender cleanup can remove a newer session for the same id

- Severity: Medium.
- Exact file/class: `com.example.common.websocket.adapter.spring.SpringRealtimeMessageSender`.
- Why it matters before freeze: `registerSpringSession`, `unregisterSpringSession`, and dead-session cleanup operate only by session id. If a stale sender/close path races with a replacement using the same id, cleanup can remove the newer session and also unregister common session/subscription state for that id.
- Exact recommended fix: store a small connection entry containing the WebSocketSession and a generation token. Use conditional removal such as `remove(sessionId, expectedEntry)` and only clean registries/subscriptions when the removed entry is still current.

### Issue 8: Registry public input validation is incomplete

- Severity: Medium.
- Exact file/class: `com.example.common.websocket.registry.InMemoryRealtimeSessionRegistry`; `com.example.common.websocket.registry.InMemoryRealtimeSubscriptionRegistry`; related registry interfaces.
- Why it matters before freeze: public registry methods can currently fail through incidental `NullPointerException` from ConcurrentHashMap or accept blank session ids in some paths. Common public contracts should have explicit, stable validation behavior.
- Exact recommended fix: document and enforce input invariants in the interfaces and implementations. Reject null/blank session ids, null sessions, null principals where not allowed, null user ids where lookup requires a user id, and null destinations with `IllegalArgumentException`.

### Issue 9: RealtimeSendResult can represent contradictory states

- Severity: Low.
- Exact file/class: `com.example.common.websocket.sender.RealtimeSendResult`.
- Why it matters before freeze: a public result can be manually constructed as `success=true` with a failure reason/cause, or `success=false` without a failure reason. This weakens sender contract clarity.
- Exact recommended fix: add a compact constructor enforcing nonblank session id, success results with null failure fields, and failure results with nonblank failure reason.

### Issue 10: Micrometer observer lifecycle claim is not implemented by auto-config

- Severity: Low.
- Exact file/class: `com.example.common.websocket.observer.MicrometerRealtimeObserver`; `com.example.common.websocket.adapter.spring.config.WebSocketAutoConfiguration`.
- Why it matters before freeze: the class documentation says it is loaded when Micrometer and a MeterRegistry are available, but auto-config only registers `NoOpRealtimeObserver`.
- Exact recommended fix: either add a conditional `MicrometerRealtimeObserver` bean when `MeterRegistry` exists, or change the documentation to say it is a manual observer implementation.

## 7. Realtime Standard Readiness

- Session model: mostly ready. Core session abstraction is transport-independent and the in-memory registry fixed stale user-index replacement. Needs explicit input validation and adapter generation-safe cleanup.
- Principal/identity model: mostly ready. Core identity is transport-independent and immutable. Spring handshake mapping needs to preserve generic principals and anonymous/system identities correctly.
- Inbound/outbound frame model: mostly ready. Sealed frame types and discriminators are strong. Inbound decode has a single common discriminator-based path. Event, command, and error frames are separated. Error payload invariants still need hardening.
- Subscription model: close, but not freeze-ready. Registry prevents duplicate storage, and common inbound subscribe authz is enforced. Duplicate semantic reporting is race-prone because the handler uses a non-atomic pre-check.
- Destination model: ready. Destination types and invariants are appropriate and broker-independent.
- Error model: not freeze-ready. It needs null/blank and immutable-details enforcement.
- Observability/lifecycle model: useful but too raw. Built-in logging is safe, but the observer API should not expose raw inbound payloads by default.
- Serialization/codec model: mostly ready. Public codec API has no typed decode bypass and no raw Object send/codec contract. The private typed decode helper is not public. EventEnvelope validation needs alignment with common-events policy.
- Auth/authz extension points: mostly ready. Connect and subscribe hooks exist; subscribe authz is enforced in the common inbound command path. Auto-config does not yet wire the full handshake boundary.
- Send/broadcast abstractions: mostly ready. Public send/broadcast APIs are typed to RealtimeFrame. Send result invariants and Spring sender replacement cleanup need hardening.
- Adapter layering: package-clean but not module-clean. Spring code is in adapter packages, but dependency and auto-config behavior still need cleanup.
- Cleanup semantics: core registries have cleanup operations. Spring dead-session cleanup needs a current-entry guard before it mutates common registries.

## 8. Cross-Common Consistency

- EventEnvelope consistency: common-websocket uses EventEnvelope<?> for semantic outbound events, matching the common-events ownership model. It does not yet validate envelopes as strictly as Kafka and Redis.
- Naming consistency: `Realtime*` naming is consistent across core packages. `SpringJwtHandshakeHandler` is too JWT-specific for a handler that promotes any RealtimePrincipal.
- Dependency layering: websocket depends on events and security only; Kafka/Redis do not feed back into websocket. This is clean.
- Duplicate abstractions: no duplicate websocket frame/message standards found in common-websocket or common-web.
- Conflicting realtime concepts: none found in common-web or common-core.
- Destination/event ownership: websocket owns delivery destinations; common-events owns semantic event identity and payload contracts. This boundary is appropriate.
- JWT ownership: common-security owns JWT extraction helpers; websocket adapter delegates to it. This is appropriate.
- Transport policy ownership: common-web remains HTTP-focused; common-websocket owns realtime policy. Overlap appears resolved in the reviewed common scope.

## 9. Freeze Blockers

High: must fix before freeze.

- Spring adapter and auto-config boundary are not freeze-clean enough for a shared realtime standard.

Medium: should fix before freeze.

- Atomic duplicate subscribe outcome in common inbound flow.
- Raw inbound payload exposure in public observer callbacks.
- RealtimeError invariant and immutable details enforcement.
- EventEnvelope validation alignment with common-events.
- Generic RealtimePrincipal preservation in Spring handshake handler.
- Generation-safe cleanup in SpringRealtimeMessageSender.
- Explicit registry input validation.

Low: can fix later.

- RealtimeSendResult invariant constructor.
- Micrometer observer documentation or conditional auto-config.
- Rename `SpringJwtHandshakeHandler` to a more generic realtime handshake name.

## 10. Minimal Remaining Refactor Plan

1. Clean the Spring boundary inside common: either split core and Spring adapter modules, or make Spring adapter activation explicit and add conditional handshake beans.
2. Replace subscribe pre-check with an atomic registry outcome and update the inbound handler.
3. Harden frozen public models: `RealtimeError`, `RealtimeSendResult`, and registry argument validation.
4. Remove raw payloads from default observer callbacks or move them behind an explicit debug hook.
5. Make Spring sender cleanup conditional on the current connection entry.
6. Preserve generic RealtimePrincipal data in the Spring handshake handler.
7. Decide and enforce the WebSocket EventEnvelope validation policy against common-events.
8. Add the missing common-only tests listed below.

## 11. Required Tests Before Freeze

Missing common-websocket tests:

- Handshake log safety with captured logs for missing token, invalid token, and denied connect paths containing query token material.
- Spring auto-config creates handshake interceptor/handler only when token resolver, identity resolver, authz policy, and observer beans exist.
- Spring auto-config stays safe when authz is missing and does not create a half-open inbound/handshake path.
- Spring auto-config does not activate unintentionally in non-web contexts or when explicitly disabled.
- Concurrent duplicate SUBSCRIBE handling emits one subscribed outcome and duplicate errors for the losers.
- Registry methods reject null/blank inputs consistently.
- Spring sender replacement cleanup does not remove a newer session or its common registry/subscription state.
- RealtimeError rejects null/blank invalid values and defensively copies details.
- RealtimeSendResult rejects contradictory success/failure state.
- Public API guard expands to assert no public send/codec method accepts raw Object and no public typed decode method exists.
- Inbound decode path rejects EVENT and ERROR frames through the common command handler with standard error frames.
- Subscribe authz is invoked before registry mutation and exactly once per subscribe attempt.
- Broadcaster behavior covers user, session, destination, global, no-session, and partial-failure cases.
- EventEnvelope outbound validation follows the chosen common-events policy.
- JwtRealtimeIdentityResolver positive path verifies JwtHelper-owned user/authority extraction and still avoids credential logging.
- Spring handshake handler preserves generic RealtimePrincipal attributes and handles anonymous/system principals according to the core identity contract.
- common-web overlap guard confirms no realtime/WebSocket policy types are introduced under common-web.

Existing useful coverage:

- Frame discriminator encode/decode and basic frame invariants.
- Destination and subscription invariants.
- Session registry replacement and concurrency.
- Subscription registry duplicate storage prevention.
- Inbound subscribe authz, duplicate, unsubscribe, ping, and malformed-frame behavior.
- Sender/broadcaster basics.
- Spring sender closed/IO cleanup.
- Handshake missing/invalid token and structural URI logging guard.
- Logging observer credential-safety checks.
- Auto-config baseline beans and inbound handler conditional on authz.
- Core contract guard for no WebSocketSession in core public contracts and removed old WebSocket DTO classes.

## 12. Final Verdict

- Freeze common-websocket now: no.
- Minimum common-only changes before freeze: clean Spring adapter/auto-config boundary, make subscribe outcome atomic, harden public error/result/registry invariants, remove raw payload observer exposure, make Spring sender cleanup generation-safe, preserve generic principal data, and align EventEnvelope validation with common-events.
- Can wait until later: naming polish for the Spring handshake handler, Micrometer observer auto-config convenience, and additional non-critical result-model polish after the main invariant work is done.
- Services were not considered.

Checklist result:

- No raw Spring WebSocketSession in core public contracts: pass.
- No duplicate websocket frame/message standards in reviewed common scope: pass.
- No business-specific room-only standard APIs: pass.
- No token-fragment or credential logging in built-in common logs: pass, but observer raw payload exposure should be fixed.
- No stale session-to-user index bug in core registry: pass.
- No raw Object send/codec contracts in frozen public API: pass.
- No public typed decode bypass: pass.
- Single discriminator-based inbound decode path exists: pass.
- EventEnvelope integrated for semantic events: partial, validation alignment still needed.
- Subscribe authz enforced in common inbound flow: pass, with duplicate race caveat.
- Adapter boundaries clean: partial, package-clean but module/auto-config boundary needs work.
- common-web overlap resolved: pass.
- Tests are real and useful: pass, but not yet sufficient for freeze.
