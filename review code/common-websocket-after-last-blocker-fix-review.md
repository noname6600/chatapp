## 1. Executive Summary
- Is common-websocket freeze-ready as the shared realtime standard? No.
- Biggest remaining blockers:
  - Spring adapter connection cleanup is not complete for normal disconnect and replacement paths, so common registries and subscriptions can remain stale unless callers compose several APIs perfectly.
  - The module still lacks one common Spring lifecycle adapter that binds connect, raw inbound text, disconnect, sender registration, session registry registration, subscription cleanup, and observer callbacks into one standard path.
  - A public adapter class is still named as JWT-specific even though its actual responsibility is generic realtime principal adaptation.
- What improved after the latest blocker-fix pass:
  - Core send and codec contracts now use `RealtimeFrame`, not raw payload objects.
  - Public codec decode is a single discriminator-based `decode(String)` path; typed decode is private implementation detail.
  - Semantic outbound frames now carry `EventEnvelope<?>` through `RealtimeEventFrame`.
  - Subscribe authorization is enforced by `DefaultRealtimeInboundFrameHandler` before registry mutation.
  - Session registry replacement now removes stale user-index entries and has concurrency coverage.
  - Spring `WebSocketSession` is absent from core public contracts and appears only under the Spring adapter package.
  - Credential-sensitive logging has direct regression coverage.
- Service modules were ignored. No service module was opened, assessed, or used for compatibility judgment.

## 2. Scope
- Reviewed only `chatappBE/common`.
- Service modules were ignored completely.
- Inspected common modules/packages:
  - `common-websocket`: all main and test packages under `com.example.common.websocket`.
  - `common-events`: `com.example.common.event`, `com.example.common.event.validation`, `com.example.common.integration.*` by source search for envelope and semantic-event ownership.
  - `common-security`: `com.example.common.security.jwt`.
  - `common-web`: `com.example.common.web.response`, `com.example.common.web.exception`, `com.example.common.web.cors`, `com.example.common.web.filter`, `com.example.common.web.controller` by source search for realtime overlap.
  - `common-kafka`: `com.example.common.kafka.*` by source search for EventEnvelope and transport boundaries.
  - `common-redis`: `com.example.common.redis.*`, with direct inspection of `RedisChannels`.
  - `common-core`: build dependency surface only; no realtime package was present.

## 3. Current Structure
`common-websocket`
- `adapter.spring`: `HandshakeTokenResolver`, `JwtRealtimeIdentityResolver`, `QueryParamTokenResolver`, `SpringHandshakeInterceptor`, `SpringJwtHandshakeHandler`, `SpringRealtimeMessageSender`, `SpringRealtimeSession`, `config.WebSocketAutoConfiguration`.
- `auth`: `RealtimeIdentityResolver`, `RealtimeAuthorizationPolicy`, `NoOpRealtimeAuthorizationPolicy`.
- `codec`: `RealtimeFrameCodec`, `JsonRealtimeFrameCodec`, `RealtimeCodecException`.
- `error`: `RealtimeError`, `RealtimeErrorCode`.
- `frame`: `RealtimeFrame`, `RealtimeEventFrame`, `RealtimeCommandFrame`, `RealtimeCommandType`, `RealtimeErrorFrame`.
- `identity`: `RealtimePrincipal`, `RealtimeIdentity`.
- `inbound`: `RealtimeInboundFrameHandler`, `DefaultRealtimeInboundFrameHandler`.
- `observer`: `RealtimeObserver`, `NoOpRealtimeObserver`, `LoggingRealtimeObserver`, `MicrometerRealtimeObserver`.
- `registry`: `InMemoryRealtimeSessionRegistry`, `InMemoryRealtimeSubscriptionRegistry`.
- `sender`: `RealtimeMessageSender`, `RealtimeBroadcaster`, `DefaultRealtimeBroadcaster`, `RealtimeSendResult`.
- `session`: `RealtimeSession`, `RealtimeSessionRegistry`.
- `subscription`: `RealtimeDestination`, `RealtimeDestinationType`, `RealtimeSubscription`, `RealtimeSubscriptionRegistry`.
- Spring auto-configuration import: `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.
- Tests cover frame contracts, guards, identity, destination, subscription, registries, sender, broadcaster, inbound handler, Spring adapters, logging safety, and auto-config.

Directly related common modules inspected for boundary checks:
- `common-events`: canonical `EventEnvelope` and event metadata ownership.
- `common-security`: JWT extraction helper ownership.
- `common-web`: HTTP-only policy and response helpers; no realtime owner found.
- `common-kafka`: EventEnvelope transport producer/consumer/serialization.
- `common-redis`: EventEnvelope pub/sub and Redis channel constants.
- `common-core`: dependency baseline only.

## 4. Responsibility and Boundary Review
- `common-websocket` now owns the right realtime primitives: identity, session, destination, subscription, sender/broadcaster, inbound/outbound frames, error frames, codec, auth/authz extension points, observer hooks, and in-memory registries.
- Ownership is mostly appropriate. The core model is no longer a room-only helper and no longer forces Spring session types into core contracts.
- Business/domain logic did not leak into `common-websocket` main code. Test names and sample channel strings use room-like examples, but the public model uses `CHANNEL_GROUP`, `USER`, `SESSION`, and `GLOBAL`.
- Transport/framework concerns are mostly isolated by package: Spring classes are under `adapter.spring`.
- Kafka and Redis are not hardwired into websocket core. `common-websocket` does not depend on `common-kafka` or `common-redis`.
- `common-events` is the right owner of semantic event envelopes; websocket wraps events rather than inventing another event wrapper.
- `common-security` remains the JWT extraction owner. Websocket has a JWT adapter, but core auth contracts stay strategy-neutral.
- `common-web` no longer appears to compete as a realtime owner. It contains HTTP response, exception, CORS, trace, and base controller utilities only.
- Remaining boundary concern: the single `common-websocket` artifact still carries Spring adapter dependencies at module level, so the class/package boundary is cleaner than the artifact boundary.

## 5. Dependency Direction
- `common-websocket` project dependencies are:
  - API dependency on `common-events`, justified by `RealtimeEventFrame` using `EventEnvelope<?>`.
  - Implementation dependency on `common-security`, used only by `JwtRealtimeIdentityResolver`.
  - No project dependency on `common-kafka`, `common-redis`, `common-web`, or `common-core`.
- Reviewed common project refs show no circular common dependency involving `common-websocket`.
- Spring coupling is isolated at class/package level to `adapter.spring` and auto-config.
- Spring coupling is not isolated at artifact level because Spring WebSocket and OAuth resource server dependencies are in `common-websocket/build.gradle`. That does not break current Spring usage, but it is a standard-quality concern if this artifact is meant to be transport-core first and adapter second.

## 6. Package / API Design Review
Issue 1
- Severity: High.
- File/class: `common-websocket/src/main/java/com/example/common/websocket/adapter/spring/SpringRealtimeMessageSender.java`.
- Problem: `unregisterSpringSession(String)` removes only the Spring session map and lock. It does not unregister from `RealtimeSessionRegistry`, does not call `RealtimeSubscriptionRegistry.cleanupSession`, and does not notify `RealtimeObserver`. The complete cleanup path exists only in `handleDeadSession`, which runs on failed send or closed send.
- Why it matters before freeze: normal disconnect is the expected lifecycle path. A shared standard cannot rely on callers remembering separate cleanup calls; stale sessions/subscriptions can keep receiving broadcasts or keep old authorization context alive.
- Exact recommended fix: centralize cleanup in one idempotent method used by `unregisterSpringSession`, `handleDeadSession`, and any future lifecycle adapter. It should remove the Spring session, release the lock, unregister the common session, cleanup subscriptions, emit observer disconnect with a structured reason, and best-effort close the old Spring session where appropriate.

Issue 2
- Severity: High.
- File/class: `common-websocket/src/main/java/com/example/common/websocket/adapter/spring/SpringRealtimeMessageSender.java`, `SpringRealtimeSession.java`, `DefaultRealtimeInboundFrameHandler.java`, `WebSocketAutoConfiguration.java`.
- Problem: the module exposes pieces, but no common Spring lifecycle adapter owns the full path: connect, create `SpringRealtimeSession`, register common session, register sender session, notify connected, dispatch raw text to `RealtimeInboundFrameHandler`, cleanup on close, and cleanup on transport error.
- Why it matters before freeze: this is the remaining reason the standard can still be used as a set of helpers rather than a reusable realtime foundation. Services would need to compose the foundation manually and can easily bypass cleanup or inbound authz.
- Exact recommended fix: add a common `SpringRealtimeWebSocketHandler` or `RealtimeConnectionLifecycle` component in `adapter.spring` that wires these primitives together. Auto-config it only when safe prerequisites exist, and keep endpoint/path registration outside common if that remains service-owned.

Issue 3
- Severity: Medium.
- File/class: `common-websocket/src/main/java/com/example/common/websocket/adapter/spring/SpringJwtHandshakeHandler.java`.
- Problem: the class name is JWT-specific, but the implementation only adapts a `RealtimePrincipal` stored by `SpringHandshakeInterceptor` to a Java `Principal`. It does not decode or validate JWT.
- Why it matters before freeze: public names become part of the standard. A JWT-named generic handshake handler encourages the wrong ownership boundary and makes non-JWT identity strategies look second-class.
- Exact recommended fix: rename or replace it with `SpringRealtimeHandshakeHandler`. Keep JWT-specific behavior in `JwtRealtimeIdentityResolver` only. Update tests to target the neutral handler name.

Issue 4
- Severity: Medium.
- File/class: `common-websocket/src/main/java/com/example/common/websocket/identity/RealtimeIdentity.java`.
- Problem: core `RealtimeIdentity` implements `java.security.Principal` and its Javadoc says that is for Spring handshake compatibility.
- Why it matters before freeze: `Principal` is a JDK type, not Spring, so this is not a direct Spring import leak. Still, the stated reason is adapter-specific and weakens the purity of the core identity model.
- Exact recommended fix: either remove `Principal` from `RealtimeIdentity` and adapt in the Spring handler, or explicitly document `Principal` as a transport-neutral JVM identity bridge. Decide before freeze.

Issue 5
- Severity: Medium.
- File/class: `common-websocket/build.gradle`.
- Problem: Spring WebSocket and OAuth resource server dependencies are module-level implementation dependencies, even though Spring usage is only in adapter classes.
- Why it matters before freeze: non-adapter consumers still receive a Spring-shaped artifact. That is acceptable for a Spring-only platform, but it is weaker than a reusable realtime foundation with optional adapters.
- Exact recommended fix: either split core and Spring adapter artifacts, or make a clear decision that `common-websocket` is the combined standard artifact and document that boundary. If split is chosen, do it before freeze.

Issue 6
- Severity: Medium.
- File/class: `common-websocket/src/main/java/com/example/common/websocket/sender/RealtimeSendResult.java`.
- Problem: the public record has no canonical constructor invariants. Callers can create `success=true` with failure reason/cause, or `success=false` without a reason.
- Why it matters before freeze: send/broadcast result semantics are part of the shared contract. Invalid results make metrics, retry, and cleanup behavior ambiguous.
- Exact recommended fix: add constructor checks: `sessionId` nonblank, success requires null failure fields, failure requires nonblank reason. Keep static factories as the normal path.

Issue 7
- Severity: Medium.
- File/class: `common-websocket/src/main/java/com/example/common/websocket/error/RealtimeError.java`.
- Problem: `correlationId` can be blank when present, and `details` remains an open `Map<String, Object>` extension bag without a documented safe content rule.
- Why it matters before freeze: error frames are public wire contracts. Loose optional fields make client handling and logging policy inconsistent.
- Exact recommended fix: reject blank `correlationId` when non-null. Either restrict `details` to safe scalar values or document and test a no-sensitive-material rule for details.

Issue 8
- Severity: Low.
- File/class: `common-websocket/src/main/java/com/example/common/websocket/frame/RealtimeCommandFrame.java`.
- Problem: Javadoc says commands such as subscribe, unsubscribe, ping, pong, ack, but `RealtimeCommandType` has no ACK.
- Why it matters before freeze: small stale doc drift in a frozen standard is still confusing.
- Exact recommended fix: remove the ACK mention or add a real ACK command with invariants and tests.

## 7. Realtime Standard Readiness
- Session model: good primitives and fixed user-index replacement in `InMemoryRealtimeSessionRegistry`; not freeze-ready until lifecycle cleanup across sender/session/subscription is standardized.
- Principal/identity model: good common interface and immutable default implementation; needs a decision on JDK `Principal` in core.
- Inbound/outbound frame model: strong. Sealed `RealtimeFrame`, typed event/command/error frames, discriminator decode, and `EventEnvelope<?>` outbound events are aligned.
- Subscription model: good registry abstraction and atomic duplicate handling; cleanup on disconnect/replacement is not standardized enough yet.
- Destination model: domain-neutral and broker-independent. `USER`, `SESSION`, `CHANNEL_GROUP`, and `GLOBAL` cover reusable routing semantics.
- Error model: usable but should harden optional field invariants before freeze.
- Observability/lifecycle model: observer hooks are present and built-in logging is safe; lifecycle ownership is incomplete without a common adapter or manager.
- Serialization/codec model: strong. Public API has `encode(RealtimeFrame)` and `decode(String)` only; typed decode bypass is private.
- Auth/authz extension points: good. `RealtimeAuthorizationPolicy` covers connect and subscribe; common inbound flow enforces subscribe authz.
- Send/broadcast abstractions: typed and transport-independent at core; adapter cleanup and send result invariants still need work.
- Adapter layering: package boundary is clean; artifact boundary and stale handler naming need cleanup.
- Cleanup semantics: not freeze-ready.

## 8. Cross-Common Consistency
- `common-events`: `RealtimeEventFrame` correctly uses `EventEnvelope<?>`; websocket does not define a competing semantic event wrapper.
- `common-kafka`: EventEnvelope-based transport is separate. No websocket dependency or Kafka hardwire was found.
- `common-redis`: EventEnvelope-based pub/sub is separate. `RedisChannels` owns Redis channel strings, including realtime channel constants, but websocket core does not import or depend on it. Do not let those broker strings become `RealtimeDestination` identifiers by convention.
- `common-core`: no realtime ownership found in the inspected dependency surface.
- `common-security`: JWT subject/authority extraction is owned by `JwtHelper`; websocket adapter delegates there. Core websocket auth remains strategy-neutral.
- `common-web`: no competing realtime policy found. It is HTTP/CORS/response/trace focused.
- Naming consistency: mostly improved, except `SpringJwtHandshakeHandler`.
- Dependency layering: clean among common projects; adapter dependencies are still module-level.

## 9. Remaining Freeze Blockers
High: must fix before freeze
- Fix normal disconnect and replacement cleanup across `SpringRealtimeMessageSender`, `RealtimeSessionRegistry`, `RealtimeSubscriptionRegistry`, and observer callbacks.
- Add one common lifecycle adapter/manager so the shared standard owns the safe connect, inbound, disconnect, and cleanup path.

Medium: should fix before freeze
- Rename `SpringJwtHandshakeHandler` to a neutral realtime handshake handler.
- Decide whether `RealtimeIdentity` should implement `Principal` in core or only be adapted in Spring boundary code.
- Decide whether Spring adapter dependencies remain in the same artifact or are split/optional.
- Add invariants to `RealtimeSendResult`.
- Harden `RealtimeError` optional fields and safe-details policy.

Low: can fix later
- Remove stale ACK wording from `RealtimeCommandFrame` Javadoc.
- Add guard tests proving `common-web` stays free of realtime ownership.
- Add optional Micrometer auto-config only if desired; current class is usable manually.

## 10. Minimal Remaining Refactor Plan
1. Add an idempotent common cleanup path for Spring sessions and use it from normal unregister, failed send cleanup, and replacement.
2. Add a Spring lifecycle adapter or common connection manager that wires sender registration, `RealtimeSessionRegistry`, `RealtimeSubscriptionRegistry`, observer hooks, and `RealtimeInboundFrameHandler`.
3. Rename the generic handshake handler away from JWT wording and update tests.
4. Add constructor invariants for `RealtimeSendResult` and `RealtimeError`.
5. Add the missing common-only tests listed below.

No service migration steps are included.

## 11. Required Tests Before Freeze
Existing common-websocket tests already cover:
- no credential-sensitive material in built-in logs for JWT decode failure, observer failure, and unexpected send errors;
- session registry replacement and concurrency;
- RealtimeError baseline invariants;
- custom anonymous `RealtimePrincipal` handling in the Spring handshake handler;
- typed frame/send/codec contracts;
- no public typed decode bypass;
- discriminator-based decode;
- frame, destination, subscription, and identity invariants;
- inbound command handling and subscribe authz;
- broadcaster/sender behavior;
- adapter auto-config basics;
- EventEnvelope outbound integration.

Missing or insufficient common-only tests:
- normal `unregisterSpringSession` cleans `RealtimeSessionRegistry`, `RealtimeSubscriptionRegistry`, lock state, observer disconnect, and optional close behavior;
- replacing a Spring session with the same session ID closes or cleans the previous session and subscriptions according to the documented lifecycle contract;
- lifecycle adapter connect/text/disconnect path, once that adapter exists;
- `RealtimeSendResult` success/failure invariant checks;
- `RealtimeError` blank correlation rejection and safe details rules;
- `JwtRealtimeIdentityResolver` success path proving decoded JWT data becomes `RealtimePrincipal` through `JwtHelper`;
- auto-config negative guard that no default allow-all authorization policy is registered;
- auto-config coverage for lifecycle adapter creation once added;
- common-web overlap guard proving no websocket/realtime package or public class is introduced there;
- cross-common guard that websocket core has no project dependency on Kafka or Redis.

Test execution performed:
- `./gradlew.bat :common:common-websocket:test --rerun-tasks`
- Result: 113 tests executed, 0 failures, 0 errors.

## 12. Final Verdict
- Freeze common-websocket now? No.
- Minimum common-only changes before freeze:
  - standardize normal disconnect and replacement cleanup;
  - add a common lifecycle adapter/manager for Spring connection flow;
  - rename the generic handshake handler away from JWT-specific wording;
  - harden public result/error invariants;
  - add the missing common-only tests above.
- What can wait until later:
  - richer destination identifier policy;
  - optional Micrometer auto-config;
  - broker mapping helpers between Redis channels and realtime destinations.
- Services were not considered, reviewed, or used in this verdict.
