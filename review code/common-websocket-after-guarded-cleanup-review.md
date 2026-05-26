## 1. Executive Summary
- common-websocket is not freeze-ready yet as the shared realtime standard.
- Biggest remaining blockers are all inside common-websocket: public unguarded Spring sender lifecycle methods can bypass the guarded cleanup contract, inbound Spring lifecycle handling does not prove the physical session is still current before mutating subscriptions, replacement does not clear or reauthorize subscription state for the replaced session id, and send can race against replacement because it does not re-read the current physical session under the per-session lock.
- What improved after the guarded-cleanup refactor: core contracts no longer expose Spring WebSocketSession, old room-only/message-wrapper APIs are removed, EventEnvelope<?> is the semantic outbound event frame payload, send/codec APIs take RealtimeFrame rather than raw Object, decode is discriminator-based through RealtimeFrameCodec.decode(String), normal disconnect/transport-error cleanup is compare-guarded when using SpringRealtimeLifecycleAdapter, and tests now cover many registry, frame, handshake, logging, and adapter races.
- Services were ignored. No service-module review was performed.

## 2. Scope
- Reviewed only under chatappBE/common.
- Service modules were ignored.
- Inspected common modules/packages:
  - common-websocket: com.example.common.websocket.adapter.spring, adapter.spring.config, auth, codec, error, frame, identity, inbound, observer, registry, sender, session, subscription; Spring auto-configuration imports; module build file; all common-websocket tests.
  - common-events: com.example.common.event and com.example.common.integration.* only to validate EventEnvelope and domain-payload boundaries.
  - common-kafka: com.example.common.kafka.config, consumer, exception, flow, observability, producer, retry, serialization, topic only for dependency/boundary checks.
  - common-redis: com.example.common.redis.channel, config, dispatcher, exception, flow, observability, publisher, serialization, subscriber only for dependency/boundary checks.
  - common-security: com.example.common.security.jwt only for JWT extraction ownership.
  - common-web: com.example.common.web.* only for realtime-policy overlap and transport-policy boundary checks.
  - common-core: com.example.common.core.* only for dependency layering checks.

## 3. Current Structure
- common-websocket:
  - adapter.spring: Spring WebSocket adapter classes for physical sessions, sender, lifecycle, handshake, token resolution, and JWT identity resolution.
  - adapter.spring.config: WebSocketAutoConfiguration.
  - auth: RealtimeIdentityResolver, RealtimeAuthorizationPolicy, NoOpRealtimeAuthorizationPolicy.
  - codec: RealtimeFrameCodec, JsonRealtimeFrameCodec, RealtimeCodecException.
  - error: RealtimeError, RealtimeErrorCode.
  - frame: RealtimeFrame, RealtimeEventFrame, RealtimeCommandFrame, RealtimeCommandType, RealtimeErrorFrame.
  - identity: RealtimePrincipal, RealtimeIdentity.
  - inbound: RealtimeInboundFrameHandler, DefaultRealtimeInboundFrameHandler.
  - observer: RealtimeObserver, NoOpRealtimeObserver, LoggingRealtimeObserver, MicrometerRealtimeObserver.
  - registry: InMemoryRealtimeSessionRegistry, InMemoryRealtimeSubscriptionRegistry.
  - sender: RealtimeMessageSender, RealtimeBroadcaster, DefaultRealtimeBroadcaster, RealtimeSendResult.
  - session: RealtimeSession, RealtimeSessionRegistry.
  - subscription: RealtimeDestination, RealtimeDestinationType, RealtimeSubscription, RealtimeSubscriptionRegistry.
- Directly related common modules inspected:
  - common-events owns EventEnvelope, EventMetadata, SharedEventCatalog, and domain integration payloads.
  - common-kafka owns Kafka EventEnvelope producer/consumer/serialization/routing abstractions.
  - common-redis owns Redis EventEnvelope pub/sub/serialization/routing abstractions and Redis channel constants.
  - common-security owns JwtHelper extraction from Spring Jwt.
  - common-web owns HTTP/CORS/response/exception utilities; the prior common-web realtime policy package is removed.
  - common-core owns shared exception and pipeline utilities.

## 4. Responsibility and Boundary Review
- common-websocket now owns the right reusable realtime primitives: identity/principal, session, destination, subscription, frame, error, codec, auth/authz hooks, sender/broadcaster, observer, registries, and Spring adapter composition.
- Ownership is mostly appropriate. The module is no longer a room-only Spring helper and no business-specific room-only standard API remains.
- No business/domain logic leaked into common-websocket. Domain-specific chat/presence payloads remain in common-events.
- Spring WebSocketSession is confined to adapter.spring and adapter.spring.config source packages. Core public contracts do not expose WebSocketSession.
- Kafka and Redis are not hardwired into websocket core source. common-websocket depends on common-events for EventEnvelope<?> and common-security only through the Spring JWT identity adapter.
- common-events is used correctly for semantic outbound events: RealtimeEventFrame wraps EventEnvelope<?> rather than defining a separate websocket event wrapper.
- common-web no longer appears to own a competing realtime flow policy. The previous common-web realtime policy package is deleted.
- Remaining boundary problem: SpringRealtimeMessageSender exposes public lifecycle methods that let callers bypass the common lifecycle adapter and its expected physical-session guard. That weakens the "one guarded lifecycle path" standard before freeze.

## 5. Dependency Direction
- common-websocket depends on common-events and common-security. It does not depend on common-kafka, common-redis, common-web, or service modules.
- common-events does not depend on common-websocket.
- common-kafka and common-redis depend on common-events; websocket does not depend back on them.
- common-security depends on common-core; websocket depends on common-security for the JWT adapter boundary.
- No circular dependency was found inside the reviewed common scope.
- Source-level Spring coupling is isolated to adapter packages. Artifact-level coupling is still broad because the same common-websocket module carries both core APIs and Spring adapters; acceptable only if this module is intentionally the combined realtime starter. If the intended foundation must be usable without Spring on the classpath, split core and Spring adapter artifacts before freeze.

## 6. Package / API Design Review
### High: Public unguarded Spring lifecycle methods remain
- File/class: common-websocket/src/main/java/com/example/common/websocket/adapter/spring/SpringRealtimeMessageSender.java, methods registerSpringSession(String, WebSocketSession), unregisterSpringSession(String), and unregisterSpringSession(String, String, boolean).
- Why it matters before freeze: these methods are public adapter APIs and can remove or replace the current physical session by session id alone. A stale physical callback that uses these APIs can still remove the current replacement session, bypass common registry connect mutation, or skip guarded cleanup. That contradicts the documented standard that lifecycle cleanup is centralized, guarded, and reusable.
- Exact recommended fix: remove these public no-expected-session lifecycle methods from the frozen API, make them package-private/internal to the adapter, or replace them with explicitly named current-session administrative methods that cannot be confused with physical-session callbacks. Keep the guarded overload that takes the expected WebSocketSession for disconnect/error cleanup, and route connect/inbound/disconnect through SpringRealtimeLifecycleAdapter.

### High: Inbound Spring lifecycle is not guarded against stale physical sessions
- File/class: common-websocket/src/main/java/com/example/common/websocket/adapter/spring/SpringRealtimeLifecycleAdapter.java, method onTextMessage(WebSocketSession, String); related missing guard in SpringRealtimeMessageSender.java.
- Why it matters before freeze: onTextMessage accepts a WebSocketSession, uses only session.getId(), then reads the current registry principal or falls back to a new SpringRealtimeSession principal. A stale old physical session with the same id can still submit SUBSCRIBE or UNSUBSCRIBE frames after replacement and mutate the current replacement's subscription state. The fallback also lets inbound frames proceed when the common registry has no current session.
- Exact recommended fix: add a current-physical-session check owned by the Spring lifecycle/sender layer, for example isCurrentSpringSession(sessionId, expectedSession) under the same per-session lock. Drop or standard-error stale inbound before decode. Remove the anonymous fallback for missing registry sessions; inbound should require a current registered RealtimeSession or fail closed without mutating subscriptions.

### High: Replacement does not clear or reauthorize subscription state
- File/class: common-websocket/src/main/java/com/example/common/websocket/adapter/spring/SpringRealtimeMessageSender.java, connectSpringSession and cleanupReplacedPhysicalSession; SpringRealtimeLifecycleAdapter.java, onConnected.
- Why it matters before freeze: replacement closes the old physical session and unregisters/re-registers the common session, but it does not clear RealtimeSubscriptionRegistry entries for the replaced session id. If the old session subscribed before replacement, the new physical/principal can inherit those subscriptions without passing the common subscribe authorization path.
- Exact recommended fix: make replacement cleanup clear old subscriptions under the same guarded connect/replace path before registering the new common session, or explicitly model retained logical subscriptions with a reauthorization step. The safer freeze default is to clear subscriptions and require the replacement to resubscribe through DefaultRealtimeInboundFrameHandler.

### High: Send can race and deliver to a stale physical session
- File/class: common-websocket/src/main/java/com/example/common/websocket/adapter/spring/SpringRealtimeMessageSender.java, method send(String, RealtimeFrame).
- Why it matters before freeze: send reads sessions.get(sessionId) before taking the per-session lock and never re-checks that the captured WebSocketSession is still the map's current value after the lock is acquired. If replacement happens between the read and the lock, and the old physical session still reports open, a frame can be sent to the stale connection.
- Exact recommended fix: after taking the session lock, re-read sessions.get(sessionId), compare it to the captured session, and send only to the current value. Either retry with the current session or return a structured failure such as SESSION_REPLACED. Add a race test where the old session remains open after replacement and must not receive the frame.

### Medium: Micrometer observer is stale or unwired
- File/class: common-websocket/src/main/java/com/example/common/websocket/observer/MicrometerRealtimeObserver.java; common-websocket/src/main/java/com/example/common/websocket/adapter/spring/config/WebSocketAutoConfiguration.java.
- Why it matters before freeze: MicrometerRealtimeObserver says it is loaded when MeterRegistry is available, but WebSocketAutoConfiguration always provides NoOpRealtimeObserver when no observer bean exists and never conditionally creates MicrometerRealtimeObserver. This leaves a public common class whose documented runtime composition is false.
- Exact recommended fix: either auto-configure MicrometerRealtimeObserver when MeterRegistry is present and no RealtimeObserver exists, with NoOp as fallback, or remove the class and micrometer dependency from common-websocket until it is intentionally supported.

### Medium: Public API validation is inconsistent
- File/class: InMemoryRealtimeSessionRegistry, InMemoryRealtimeSubscriptionRegistry, DefaultRealtimeBroadcaster, SpringRealtimeMessageSender.
- Why it matters before freeze: core records enforce invariants well, but public registry/broadcaster/sender methods rely on incidental NullPointerException or downstream behavior for null/blank session ids, destinations, frames, and principals. A frozen common standard should define fail-fast behavior at public entry points.
- Exact recommended fix: add explicit null/blank validation to public methods and focused tests for RealtimeSessionRegistry, RealtimeSubscriptionRegistry, RealtimeBroadcaster, RealtimeMessageSender, and Spring lifecycle adapter entry points.

### Passes
- Core public contracts do not expose Spring WebSocketSession.
- Public send and codec contracts take RealtimeFrame, not raw Object.
- Public codec API exposes only encode(RealtimeFrame) and decode(String); typed decode is private.
- Inbound decode goes through the discriminator-based RealtimeFrameCodec.decode(String) path.
- RealtimeEventFrame aligns semantic outbound events with EventEnvelope<?>.
- Duplicate old websocket wrappers and room-only APIs are absent from source.
- Default observer APIs do not expose raw inbound payload strings.
- No token or credential material is logged in reviewed common-websocket code.

## 7. Realtime Standard Readiness
- Session model: mostly ready at the core contract/registry level, but not freeze-ready because Spring adapter public lifecycle APIs and send/inbound races still allow stale physical-session effects.
- Principal/identity model: ready with RealtimePrincipal and immutable RealtimeIdentity. Custom anonymous principal handling in SpringRealtimeHandshakeHandler is covered and sensible.
- Inbound/outbound frame model: ready. RealtimeFrame is sealed, command/event/error frames have discriminators, EventEnvelope<?> is used for semantic outbound events, and raw Object send/codec contracts are gone.
- Subscription model: not freeze-ready because replacement can inherit previous subscriptions without reauthorization and stale inbound can mutate replacement subscriptions.
- Destination model: ready at record invariant level. USER/SESSION/CHANNEL_GROUP/GLOBAL semantics are transport-independent.
- Error model: mostly ready. RealtimeError invariants are tested and separated from HTTP response DTOs.
- Observability/lifecycle model: improved but not freeze-ready. Observers avoid raw payload strings, but Micrometer composition is stale/unwired and lifecycle guard coverage is incomplete for inbound.
- Serialization/codec model: ready. Single discriminator-based decode path exists and typed decode bypass is not public.
- Auth/authz extension points: mostly ready. allowConnect and allowSubscribe exist and subscribe authorization is enforced in DefaultRealtimeInboundFrameHandler. Stale inbound and replacement subscription retention still create paths around the intended subscribe authorization flow.
- Send/broadcast abstractions: mostly ready at core API level, but SpringRealtimeMessageSender needs current-session recheck under lock and public lifecycle method cleanup.
- Adapter layering: source packages are clean, but frozen public adapter surface still exposes unsafe lower-level lifecycle methods.
- Cleanup semantics: normal disconnect/transport-error cleanup is guarded through SpringRealtimeLifecycleAdapter, but replacement cleanup and inbound handling are not yet fully standardized through one guarded path.
- Auto-config/runtime composition: mostly valid and fail-closed for authz-dependent inbound/handshake beans; Micrometer observer composition and lifecycle subscription cleanup wiring need fixes.

## 8. Cross-Common Consistency
- common-events: EventEnvelope is the shared semantic event container. common-websocket uses it in RealtimeEventFrame and does not define a competing event wrapper.
- common-kafka: Kafka owns Kafka topics, producer/consumer, serialization, and routing. common-websocket does not import Kafka types.
- common-redis: Redis owns Redis channels/pub-sub/serialization/routing. common-websocket does not import Redis types and RealtimeDestination docs correctly reject broker channel/topic names as websocket destinations.
- common-core: dependency direction remains clean through common-security; websocket core does not depend on core directly.
- common-security: JwtHelper owns extraction from Spring Jwt. JwtRealtimeIdentityResolver delegates to it instead of owning JWT claim rules itself.
- common-web: no remaining common-web realtime policy owner was found. HTTP/CORS/response utilities are cleanly bounded from websocket realtime policy.
- Naming consistency is good: Realtime* for websocket primitives, EventEnvelope for semantic events, Kafka* and Redis* for broker-specific concerns.
- No conflicting common-layer realtime concept remains outside common-websocket in the inspected common scope.

## 9. Remaining Freeze Blockers
- High: remove or restrict public unguarded Spring sender lifecycle methods that bypass expected physical-session cleanup.
- High: guard SpringRealtimeLifecycleAdapter.onTextMessage against stale physical sessions and unknown/unregistered sessions.
- High: make replacement cleanup clear or reauthorize RealtimeSubscriptionRegistry state for the replaced session id.
- High: re-read/verify the current physical session under lock in SpringRealtimeMessageSender.send before delivery.
- Medium: wire or remove MicrometerRealtimeObserver and its dependency.
- Medium: add explicit validation for public registry, broadcaster, sender, and lifecycle entry points.
- Low: document whether RealtimeSession.attributes() is intentionally mutable connection-scoped state or should be a defensive copy. SpringRealtimeSession currently returns the delegate attribute map.

## 10. Minimal Remaining Refactor Plan
1. Introduce a guarded current-physical-session check in the Spring adapter layer and use it for inbound, disconnect, transport-error, and send.
2. Remove, narrow, or clearly internalize public no-expected-session lifecycle methods on SpringRealtimeMessageSender.
3. Make replacement cleanup handle subscription state under the same guarded connect/replace operation; default to clearing subscriptions and requiring re-subscribe through the common inbound path.
4. Update WebSocketAutoConfiguration and SpringRealtimeLifecycleAdapter wiring if subscription cleanup or current-session guard requires an extra dependency.
5. Add fail-fast validation at public common-websocket entry points.
6. Either auto-configure MicrometerRealtimeObserver when MeterRegistry exists or remove it from common-websocket for now.

## 11. Required Tests Before Freeze
- Add stale physical inbound tests: old physical session after replacement cannot SUBSCRIBE, UNSUBSCRIBE, PING, or emit errors through the current replacement session.
- Add unknown/unregistered inbound tests: onTextMessage must not fall back to anonymous principal and mutate subscriptions when the registry has no current session.
- Add replacement subscription tests: old subscriptions are cleared or reauthorized on replacement; broadcaster must not deliver to a new principal through an old subscription.
- Add send race tests: if replacement occurs after send reads the old session but before it acquires the lock, the old session must not receive the frame.
- Add guard tests that public adapter lifecycle APIs cannot remove current replacement state without the expected physical session.
- Add connect/replace atomicity tests covering concurrent old send failure plus replacement, with session registry, subscription registry, sender map, and observer events all asserted.
- Add auto-config tests for any new lifecycle/subscription wiring and for MicrometerRealtimeObserver if kept.
- Add public API validation tests for null/blank session ids, null frames, null destinations, null sessions, and invalid principals.
- Existing tests already cover: no credential/token material in key logs, RealtimeSendResult invariants, RealtimeError invariants, custom anonymous RealtimePrincipal handling in SpringRealtimeHandshakeHandler, typed frame/send/codec contract shape, no public typed decode bypass, discriminator-based decode, frame/destination/subscription/identity invariants, subscribe authz in the common inbound path, sender/broadcaster behavior, adapter auto-config basics, EventEnvelope outbound integration, common-security JWT extraction ownership, and common-web overlap removal.
- Verification run: .\gradlew.bat :common:common-websocket:test --rerun-tasks passed.

## 12. Final Verdict
- Freeze common-websocket now? No.
- Minimum common-only changes before freeze: close the public unguarded lifecycle escape hatches, guard inbound with current physical-session ownership, clear or reauthorize subscriptions on replacement, re-check current physical session under lock before send, and add tests for those paths.
- What can wait until later: optional Micrometer auto-configuration if the class is removed or clearly made opt-in, broader artifact split between core and Spring adapter if the combined module is acceptable, and documentation polish around mutable session attributes.
- Services were not considered.

Strict checklist result:
- no raw Spring WebSocketSession in core public contracts: pass.
- no duplicate websocket frame/message standards: pass.
- no business-specific room-only standard APIs: pass.
- no token-fragment or credential logging: pass in reviewed code and tests.
- no stale session-to-user index bug: pass in InMemoryRealtimeSessionRegistry tests.
- no stale physical session may remove a current replacement session: fail as a frozen standard because public unguarded lifecycle methods remain, even though the lifecycle adapter's guarded disconnect path is improved.
- no raw Object send/codec contracts in frozen public API: pass.
- no public typed decode bypass: pass.
- single discriminator-based inbound decode path exists: pass.
- EventEnvelope integrated properly for semantic events: pass.
- subscribe authz is enforced in common inbound flow: pass for normal current-session inbound; incomplete under stale inbound/replacement subscription paths.
- lifecycle cleanup is standardized, guarded, and reusable: partial; normal disconnect/error are improved, replacement and inbound remain incomplete.
- connect/replace behavior is atomic enough for documented contract: partial; session index is improved, but subscription and send/inbound race semantics are not freeze-ready.
- adapter boundaries are clean: source-package pass, public adapter API surface still needs tightening.
- common-web overlap is resolved: pass.
- tests are real and substantial but not sufficient for freeze: fail until stale inbound, replacement subscription, send-currentness, and public lifecycle escape-hatch tests are added.
