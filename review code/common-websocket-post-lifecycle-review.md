## 1. Executive Summary

- Is `common-websocket` now freeze-ready as the shared realtime standard? No.
- Biggest remaining blocker: Spring adapter cleanup is still keyed only by `sessionId`, so a stale physical `WebSocketSession` can remove the current replacement session, unregister its common session view, and wipe subscriptions.
- What improved after the latest lifecycle/cleanup refactor:
  - A common `SpringRealtimeLifecycleAdapter` now centralizes connect, inbound text, disconnect, and transport-error orchestration.
  - `SpringRealtimeMessageSender` now owns one cleanup path for normal disconnect, replacement, and dead-session cleanup.
  - `InMemoryRealtimeSessionRegistry` now repairs stale user indexes when the same session ID is re-registered under a different user.
  - Inbound subscribe authorization is enforced through `DefaultRealtimeInboundFrameHandler`.
  - Public send and codec contracts are typed to `RealtimeFrame`; no raw `Object` send/codec API remains.
  - Inbound decode uses the discriminator-based `RealtimeFrameCodec.decode(String)` entry point.
  - Semantic outbound events are represented by `RealtimeEventFrame` carrying `EventEnvelope<?>`.
- Services were ignored. No service module compatibility or migration analysis is included.

Tests run:

- `.\gradlew.bat :common:common-websocket:test` - passed.
- `.\gradlew.bat :common:common-events:test :common:common-kafka:test :common:common-redis:test :common:common-security:test :common:common-web:test :common:common-core:test` - passed or no-source where applicable.

## 2. Scope

- Reviewed code was restricted to `chatappBE/common`.
- Service modules were ignored.
- Reviewed primary module:
  - `common-websocket`
- Related common modules/packages inspected only for boundary validation:
  - `common-events`: `com.example.common.event`, `com.example.common.integration.*`
  - `common-security`: `com.example.common.security.jwt`
  - `common-web`: `com.example.common.web.response`, `filter`, `cors`, `controller`, `exception`
  - `common-kafka`: `config`, `consumer`, `exception`, `flow`, `observability`, `producer`, `retry`, `serialization`, `topic`
  - `common-redis`: `channel`, `config`, `dispatcher`, `exception`, `flow`, `observability`, `publisher`, `serialization`, `subscriber`
  - `common-core`: `exception`, `pipeline`

## 3. Current Structure

`common-websocket` final discovered structure:

- `auth`
  - `RealtimeIdentityResolver`
  - `RealtimeAuthorizationPolicy`
  - `NoOpRealtimeAuthorizationPolicy`
- `identity`
  - `RealtimePrincipal`
  - `RealtimeIdentity`
- `session`
  - `RealtimeSession`
  - `RealtimeSessionRegistry`
- `registry`
  - `InMemoryRealtimeSessionRegistry`
  - `InMemoryRealtimeSubscriptionRegistry`
- `subscription`
  - `RealtimeDestination`
  - `RealtimeDestinationType`
  - `RealtimeSubscription`
  - `RealtimeSubscriptionRegistry`
- `sender`
  - `RealtimeMessageSender`
  - `RealtimeBroadcaster`
  - `DefaultRealtimeBroadcaster`
  - `RealtimeSendResult`
- `frame`
  - `RealtimeFrame`
  - `RealtimeEventFrame`
  - `RealtimeCommandFrame`
  - `RealtimeCommandType`
  - `RealtimeErrorFrame`
- `error`
  - `RealtimeError`
  - `RealtimeErrorCode`
- `codec`
  - `RealtimeFrameCodec`
  - `JsonRealtimeFrameCodec`
  - `RealtimeCodecException`
- `inbound`
  - `RealtimeInboundFrameHandler`
  - `DefaultRealtimeInboundFrameHandler`
- `observer`
  - `RealtimeObserver`
  - `NoOpRealtimeObserver`
  - `LoggingRealtimeObserver`
  - `MicrometerRealtimeObserver`
- `adapter.spring`
  - `SpringRealtimeSession`
  - `SpringRealtimeMessageSender`
  - `SpringRealtimeLifecycleAdapter`
  - `SpringRealtimeHandshakeHandler`
  - `SpringHandshakeInterceptor`
  - `HandshakeTokenResolver`
  - `QueryParamTokenResolver`
  - `JwtRealtimeIdentityResolver`
- `adapter.spring.config`
  - `WebSocketAutoConfiguration`
- resources:
  - `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- tests:
  - Contract, guard, frame, codec, registry, sender, broadcaster, inbound, auth, observer, Spring adapter, logging safety, auto-config tests.

Directly related common module structure inspected:

- `common-events`
  - Canonical `EventEnvelope`, `EventMetadata`, event payload registry/catalog, shared integration payload packages.
- `common-security`
  - JWT helper extraction only.
- `common-kafka`
  - Event envelope producer/consumer/serialization/dispatch abstractions and Kafka-specific topic/routing ownership.
- `common-redis`
  - Event envelope publisher/subscriber/serialization/dispatch abstractions and Redis-specific channel ownership.
- `common-web`
  - HTTP response, exception, CORS, trace filter, base controller utilities.
- `common-core`
  - Base exception and pipeline utilities.

## 4. Responsibility and Boundary Review

What `common-websocket` now owns:

- Realtime identity and principal model.
- Realtime session abstraction and in-memory registry.
- Realtime destination and subscription model.
- Typed send and broadcast abstractions.
- Inbound/outbound frame model.
- Realtime error frame and error payload model.
- Codec abstraction and JSON codec.
- Auth/authz extension points.
- Common inbound command handling for subscribe, unsubscribe, ping, and pong.
- Observer hooks and built-in logging/meter observers.
- Spring adapter boundary for handshake, sessions, sender, lifecycle, and auto-config.

Ownership is broadly appropriate. The module is no longer merely a Spring helper: the core packages define reusable realtime primitives, and the Spring-specific types are source-contained under `adapter.spring`.

Business/domain logic did not leak into `common-websocket`. The old room-only shape is gone from the main contracts; destinations are generalized as `USER`, `SESSION`, `CHANNEL_GROUP`, and `GLOBAL`.

Transport/framework concerns are mostly isolated:

- Core public contracts do not expose `WebSocketSession`.
- Spring classes live under `adapter.spring` and `adapter.spring.config`.
- Kafka and Redis are not imported by `common-websocket` source.

Remaining boundary concern:

- The module artifact still directly pulls Spring WebSocket and OAuth resource-server dependencies in `common-websocket/build.gradle:35-36`. Source boundaries are clean, but artifact boundaries are still Spring-heavy for a foundation module.

Kafka/Redis/common-events/common-security boundaries:

- `RealtimeEventFrame` uses `EventEnvelope<?>` from `common-events`, which is the right semantic-event boundary.
- Kafka and Redis own their own topic/channel naming. `common-websocket` does not consume `KafkaTopics`, `RedisChannels`, Kafka producers, or Redis publishers.
- JWT claim extraction is delegated to `common-security` via `JwtHelper`, which is the right ownership direction.

`common-web` overlap:

- No competing realtime owner was found in `common-web`.
- `common-web` remains HTTP/controller utility territory.

## 5. Dependency Direction

Observed common-layer dependency direction:

- `common-websocket` -> `common-events`
- `common-websocket` -> `common-security`
- `common-security` -> `common-core`
- `common-kafka` -> `common-events`
- `common-redis` -> `common-events`
- `common-web` -> `common-core`
- `common-core` -> no reviewed common module dependency

No circular dependency was found in the reviewed common scope.

Spring coupling:

- Core `common-websocket` packages do not import Spring WebSocket or Spring Security.
- Spring imports are isolated to `adapter.spring`, `adapter.spring.config`, and the JWT Spring adapter.
- Build dependencies still make the single `common-websocket` artifact Spring-capable by default. If the freeze target is a truly transport-neutral artifact, split the Spring adapter from core or make the artifact boundary explicit.

## 6. Package / API Design Review

### Issue 1: High - replacement cleanup can remove the current session

- File/class:
  - `common-websocket/src/main/java/com/example/common/websocket/adapter/spring/SpringRealtimeMessageSender.java`
  - `SpringRealtimeMessageSender.send`, lines 81-117
  - `SpringRealtimeMessageSender.cleanupSession`, lines 121-130
  - `SpringRealtimeMessageSender.performCommonCleanup`, lines 137-160
  - `SpringRealtimeLifecycleAdapter.onDisconnected`, lines 46-48
  - `SpringRealtimeLifecycleAdapter.onTransportError`, lines 51-53
- Why it matters before freeze:
  - `send` captures a `WebSocketSession` at line 82, but cleanup removes by `sessionId` only at line 124.
  - A stale/old physical session can fail a send or emit a late disconnect after a replacement has registered under the same session ID. The cleanup path can then remove the replacement from `sessions`, unregister the replacement from `RealtimeSessionRegistry`, clean the replacement subscriptions, and emit a misleading disconnect observer event.
  - This violates the documented registry replacement contract and the requested lifecycle cleanup standard.
- Exact recommended fix:
  - Make cleanup expected-session guarded.
  - Add cleanup APIs that take the physical `WebSocketSession` being cleaned:
    - `cleanupSession(String sessionId, WebSocketSession expectedSession, String reason, boolean closeSession)`
    - `unregisterSpringSession(String sessionId, WebSocketSession expectedSession, String reason, boolean closeSession)`
  - Use `sessions.remove(sessionId, expectedSession)` or an equivalent holder/generation check.
  - In `send`, call cleanup using the captured `session`, not just `sessionId`.
  - In `SpringRealtimeLifecycleAdapter.onDisconnected/onTransportError`, pass the actual Spring session to the guarded cleanup path.
  - Keep replacement cleanup closing only the replaced physical session, without removing the new current mapping.

### Issue 2: High - connect registration is split across sender and common registry

- File/class:
  - `common-websocket/src/main/java/com/example/common/websocket/adapter/spring/SpringRealtimeLifecycleAdapter.java`
  - `onConnected`, lines 30-35
  - `common-websocket/src/main/java/com/example/common/websocket/adapter/spring/SpringRealtimeMessageSender.java`
  - `registerSpringSession`, lines 53-62
- Why it matters before freeze:
  - `onConnected` first registers the Spring session in the sender, then separately registers the common `RealtimeSession`.
  - A second connection/replacement can interleave between those two operations. The earlier `onConnected` call can then register a stale common session after the replacement path already ran.
  - The latest lifecycle manager exists, but it does not yet make connect/replace atomic enough across sender state, common session registry, subscription cleanup, and observer hooks.
- Exact recommended fix:
  - Move Spring-session registration and common-session registry registration behind one per-session critical section owned by the lifecycle adapter/manager.
  - Prefer a single method such as `connect(WebSocketSession springSession, RealtimeSession realtimeSession)` that atomically:
    - registers the physical session,
    - cleans any replaced physical session,
    - unregisters stale common registry state,
    - registers the new common session,
    - emits the correct observer events in deterministic order.
  - Add concurrency tests that force interleaving between physical registration and common registry registration.

### Issue 3: Medium - Spring auto-config does not compose the handshake boundary

- File/class:
  - `common-websocket/src/main/java/com/example/common/websocket/adapter/spring/config/WebSocketAutoConfiguration.java`
  - Beans at lines 44-118
  - `SpringHandshakeInterceptor`
  - `SpringRealtimeHandshakeHandler`
  - `HandshakeTokenResolver`
  - `RealtimeIdentityResolver`
- Why it matters before freeze:
  - Auto-config creates sender, broadcaster, registries, codec, inbound handler, and lifecycle adapter.
  - It does not create the common `SpringHandshakeInterceptor` or `SpringRealtimeHandshakeHandler`.
  - This leaves the authentication and principal-promotion boundary manually composed, even though it is part of the common Spring adapter standard.
  - The Javadoc says the interceptor will fail to autowire without an authz policy, but the interceptor is not actually auto-configured.
- Exact recommended fix:
  - Add conditional beans for:
    - `SpringRealtimeHandshakeHandler`
    - `SpringHandshakeInterceptor` when `HandshakeTokenResolver`, `RealtimeIdentityResolver`, `RealtimeAuthorizationPolicy`, and `RealtimeObserver` are present.
  - Keep token and identity strategy explicit. Do not silently default to query-param token extraction.
  - Add auto-config tests for:
    - no authz policy means no inbound handler/lifecycle/interceptor,
    - explicit authz, token resolver, and identity resolver creates interceptor and handshake handler,
    - custom beans override common defaults.

### Issue 4: Medium - observer API exposes raw inbound payloads

- File/class:
  - `common-websocket/src/main/java/com/example/common/websocket/observer/RealtimeObserver.java`
  - `onFrameReceived`, lines 29-30
  - `onFrameDecodeFailed`, lines 32-33
  - `common-websocket/src/main/java/com/example/common/websocket/inbound/DefaultRealtimeInboundFrameHandler.java`
  - raw payload passed at lines 38 and 44
- Why it matters before freeze:
  - Built-in logging avoids payload logging, which is good.
  - The public observer API still hands raw client payloads to every observer implementation.
  - If a client sends credential material in a malformed frame, custom observers can easily log it. A frozen common standard should make safe observability the easy path.
- Exact recommended fix:
  - Replace raw-payload observer callbacks with safe metadata:
    - payload length,
    - frame type after decode where available,
    - structured failure reason.
  - If raw access is truly needed, make it an explicitly named advanced hook with strong warning and no default use in common logging/metering.
  - Add logging safety tests proving malformed raw payloads containing secret-looking values do not appear through built-in observers.

### Issue 5: Medium - artifact-level Spring dependency weakens the foundation boundary

- File/class:
  - `common-websocket/build.gradle`
  - Spring dependencies at lines 35-36
  - Micrometer dependency at line 39
- Why it matters before freeze:
  - Source packages isolate Spring classes under `adapter.spring`, but the artifact still brings Spring WebSocket/OAuth dependencies to every consumer.
  - For a reusable realtime foundation, this should be an explicit adapter boundary, not an implicit dependency of the core primitives.
- Exact recommended fix:
  - Preferred: split core realtime contracts from Spring adapter code.
  - Acceptable if a split is intentionally deferred: document that the current artifact is the Spring-capable standard and keep all core public contracts framework-free.

### Issue 6: Low - Micrometer observer says it is loaded conditionally, but no bean wires it

- File/class:
  - `common-websocket/src/main/java/com/example/common/websocket/observer/MicrometerRealtimeObserver.java`, lines 10-17
  - `common-websocket/src/main/java/com/example/common/websocket/adapter/spring/config/WebSocketAutoConfiguration.java`, lines 44-48
- Why it matters before freeze:
  - The class comment says it is loaded when Micrometer and `MeterRegistry` are available.
  - Auto-config always defaults to `NoOpRealtimeObserver` and never creates `MicrometerRealtimeObserver`.
  - This is small, but it is stale standard documentation.
- Exact recommended fix:
  - Either wire `MicrometerRealtimeObserver` conditionally when `MeterRegistry` exists, or revise the class comment to say applications may register it manually.

## 7. Realtime Standard Readiness

- Session model: Mostly ready, but blocked by Spring adapter replacement cleanup and connect registration atomicity.
- Principal/identity model: Ready. It is transport-independent, supports authenticated and anonymous identities, and Spring principal adaptation is covered.
- Inbound/outbound frame model: Ready. `RealtimeFrame` is sealed; command, event, and error frames are distinct.
- Subscription model: Mostly ready. Duplicate prevention exists through `subscribeIfAbsent`; cleanup is standardized but currently vulnerable to wrong-session cleanup from the Spring adapter.
- Destination model: Ready. It is broker-neutral and not room-only.
- Error model: Ready. `RealtimeError` and `RealtimeErrorFrame` enforce basic invariants.
- Observability/lifecycle model: Not fully ready. Lifecycle has a common adapter now, but observer raw payload exposure and wrong-session cleanup remain.
- Serialization/codec model: Ready. Public codec API exposes only `encode(RealtimeFrame)` and `decode(String)`; typed decode is private.
- Auth/authz extension points: Mostly ready. `allowConnect` and `allowSubscribe` exist; subscribe authz is enforced in the common inbound path. Auto-config composition for handshake is incomplete.
- Send/broadcast abstractions: Mostly ready. Public APIs are typed to `RealtimeFrame`; broadcaster routing is generalized.
- Adapter layering: Source layering is mostly clean. Artifact-level Spring dependency remains a boundary concern.
- Cleanup semantics: Not freeze-ready. The common cleanup path exists, but it is not expected-session guarded.
- Auto-config/runtime composition: Partially ready. Core realtime beans compose; handshake boundary is still manual.

## 8. Cross-Common Consistency

- `common-websocket` and `common-events`
  - Good: outbound semantic frames use `EventEnvelope<?>`.
  - Good: websocket does not define a competing semantic event wrapper.
- `common-websocket` and `common-kafka`
  - Good: Kafka owns Kafka topics, producer/consumer contracts, and Kafka envelope serialization.
  - Good: websocket does not depend on Kafka classes.
- `common-websocket` and `common-redis`
  - Good: Redis owns Redis channels and pub/sub contracts.
  - Good: websocket does not depend on Redis classes.
- `common-websocket` and `common-core`
  - Clean: no direct core dependency was found from websocket. Shared exception/pipeline utilities are not mixed into realtime.
- `common-websocket` and `common-security`
  - Good: JWT extraction logic is delegated to `JwtHelper`.
  - Boundary note: JWT resolver sits in the Spring adapter and uses `JwtDecoder`, which is appropriate for the adapter.
- `common-websocket` and `common-web`
  - Good: no competing realtime abstractions were found in `common-web`.
  - Good: HTTP response/error DTOs are not reused as websocket error frames.

No duplicate websocket frame/message standard remains in the reviewed common scope.

## 9. Remaining Freeze Blockers

High - must fix before freeze:

- Expected-session guarded cleanup is missing. Stale physical sessions can clean current replacement state.
- Connect registration is not atomic across sender state and common session registry state.
- Tests do not cover late disconnect/transport error/send failure from an old physical session after replacement.

Medium - should fix before freeze:

- Auto-config does not compose the common handshake interceptor/handler boundary.
- Observer API exposes raw inbound payloads to arbitrary observers.
- The single artifact still has hard Spring/OAuth/Micrometer dependencies despite transport-neutral core contracts.

Low - can fix later:

- `MicrometerRealtimeObserver` documentation does not match auto-config behavior.
- Minor formatting polish in `WebSocketAutoConfiguration` around the `realtimeMessageSender` method indentation.

## 10. Minimal Remaining Refactor Plan

1. Guard cleanup by expected physical session.
   - Change cleanup/removal to compare the current `WebSocketSession` with the one being cleaned.
   - Use `sessions.remove(sessionId, expectedSession)` or a holder/generation model.

2. Make connect/replace atomic at the lifecycle adapter/manager boundary.
   - Register physical session and common `RealtimeSession` under one per-session critical section.
   - Keep replaced physical-session closure separate from removal of the current mapping.

3. Complete Spring adapter auto-config.
   - Add conditional handshake handler/interceptor beans.
   - Require explicit token resolver, identity resolver, and authz policy for the interceptor.

4. Tighten observer safety.
   - Remove raw payload from default observer contract or isolate it behind an explicitly unsafe advanced hook.

5. Add missing common-only tests.
   - Especially replacement/disconnect races and auto-config handshake composition.

No service migration steps are part of this plan.

## 11. Required Tests Before Freeze

Already covered by current tests:

- No raw Spring `WebSocketSession` in core public contracts.
- No obsolete websocket DTO/room-only classes detected by guard tests.
- Public send API uses `RealtimeFrame`, not raw `Object`.
- Public codec API exposes only `encode` and `decode`.
- Discriminator-based decode for event, command, and error frames.
- Frame, destination, subscription, identity, error, and send-result invariants.
- Subscribe authz in common inbound path.
- Duplicate subscription handling.
- Basic sender cleanup on closed/IO-failed sessions.
- Basic replacement during send stability.
- Basic lifecycle adapter connect/inbound/disconnect delegation.
- Custom anonymous `RealtimePrincipal` handling in `SpringRealtimeHandshakeHandler`.
- Built-in logging does not include JWT decode failure credential material or exception messages.
- EventEnvelope outbound encoding through `RealtimeEventFrame`.
- JWT extraction ownership through `JwtRealtimeIdentityResolver` and `JwtHelper`.
- Basic auto-config bean composition.

Still required/missing before freeze:

- Session replacement correctness:
  - old physical session late `onDisconnected` must not remove new replacement.
  - old physical session late `onTransportError` must not remove new replacement.
  - old physical session send failure must not remove new replacement.
  - replacement must not wipe subscriptions belonging to the replacement.
  - replacement cleanup observer events must distinguish old cleanup from current disconnect.
- Connect atomicity:
  - force interleaving between `registerSpringSession` and `sessionRegistry.register`.
  - prove final registry and sender state point to the same current connection.
- Standardized cleanup:
  - normal disconnect, replacement cleanup, dead-session cleanup, and transport-error cleanup should all use one expected-session guarded path.
- Observer/logging safety:
  - malformed raw payload containing secret-looking strings must not appear in built-in logs.
  - observer callback failures must not leak exception messages.
  - consider a guard test that default observer contracts do not expose raw payload strings.
- Typed frame/send/codec contracts:
  - reflection guard should assert no public `decode(String, Class<?>)` or typed decode overload exists.
  - reflection guard should assert no sender/broadcaster method accepts raw `Object`.
- Auto-config:
  - no authz policy means no inbound handler, lifecycle adapter, or handshake interceptor.
  - explicit authz, token resolver, and identity resolver creates the common interceptor and handshake handler.
  - custom handshake/interceptor beans override defaults.
- Common-web overlap:
  - structural guard that `common-web` has no websocket/realtime owner classes.
- Cross-common broker boundaries:
  - guard that `common-websocket` main sources do not import `com.example.common.kafka` or `com.example.common.redis`.

## 12. Final Verdict

- Freeze `common-websocket` now? No.
- Minimum common-only changes before freeze:
  - Fix expected-session guarded cleanup.
  - Make connect/replace atomic across Spring sender state and common session registry state.
  - Add the missing replacement/concurrency tests proving stale physical sessions cannot clean current replacements.
  - Complete or explicitly bound Spring handshake auto-config.
- What can wait until later:
  - Artifact split between core and Spring adapter, if the team accepts the current artifact as Spring-capable for now.
  - Micrometer observer auto-config/doc cleanup.
  - Minor formatting polish.
- Services were not considered.

Strict checklist result:

- no raw Spring `WebSocketSession` in core public contracts: pass.
- no duplicate websocket frame/message standards: pass in reviewed common scope.
- no business-specific room-only standard APIs: pass.
- no token-fragment or credential logging by built-in code: mostly pass; raw observer payload API remains a safety risk.
- no stale session-to-user index bug: pass in the registry itself.
- no raw `Object` send/codec contracts in frozen public API: pass.
- no public typed decode bypass: pass.
- single discriminator-based inbound decode path exists: pass.
- `EventEnvelope<?>` integrated properly for semantic events: pass.
- subscribe authz is enforced in common inbound flow: pass.
- lifecycle cleanup is standardized and reusable: partial, but blocked by wrong-session cleanup risk.
- adapter boundaries are clean: mostly pass at source level; artifact-level Spring dependency remains.
- common-web overlap is resolved: pass.
- tests are real and broad, but not yet sufficient for freeze because the critical replacement/cleanup race is untested.
