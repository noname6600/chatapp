## 1. Executive Summary

- common-websocket is much closer, but it is not freeze-ready yet as the shared realtime standard.
- Biggest remaining blockers:
  - common logging can still include exception text from an unexpected send failure.
  - the common-web realtime owner cleanup is incomplete because a stale test still references removed policy classes and fails common-web test compilation.
  - the realtime error model does not enforce its own invariants.
  - the Spring handshake handler can fail for a valid custom anonymous realtime principal shape that the core model allows.
- What improved after refactor:
  - Core public contracts no longer expose Spring `WebSocketSession`.
  - Send and codec contracts now use `RealtimeFrame`, not raw `Object`.
  - Inbound decode now enters through a discriminator-based `RealtimeFrameCodec.decode(String)` path.
  - Semantic outbound events use `EventEnvelope<?>` through `RealtimeEventFrame`.
  - Identity, session, destination, subscription, sender, broadcaster, frame, error, codec, authz, and observer primitives now exist in common-websocket.
  - Kafka and Redis are not hardwired into websocket core.
  - Old room-only and duplicate websocket DTO/protocol classes are absent from current common-websocket main sources.
  - Session replacement index behavior was improved and has regression tests.
- Services were ignored. No service modules were reviewed.

## 2. Scope

- Reviewed only under `chatappBE/common`.
- Primary review target: `chatappBE/common/common-websocket`.
- Service modules were ignored completely.
- Common modules/packages inspected:
  - `common-websocket`: all main packages and tests.
  - `common-events`: `com.example.common.event`, `com.example.common.event.validation`, and referenced `com.example.common.integration.*` event payload/type packages.
  - `common-security`: `com.example.common.security.jwt`.
  - `common-kafka`: build file and main packages for dependency/boundary validation.
  - `common-redis`: build file and main packages for dependency/boundary validation.
  - `common-web`: build file, main web packages, empty realtime main package directories, and stale realtime test package.
  - `common-core`: build dependency boundary only.

## 3. Current Structure

common-websocket main structure:

- `adapter.spring`: Spring handshake, token resolver, identity resolver, session adapter, message sender.
- `adapter.spring.config`: Spring Boot auto-configuration import target.
- `auth`: identity resolver and authorization policy extension points.
- `codec`: frame codec abstraction and JSON implementation.
- `error`: realtime error code and payload model.
- `frame`: sealed realtime frame model: event, command, error.
- `identity`: transport-neutral principal and identity implementation.
- `inbound`: common raw inbound command handling path.
- `observer`: lifecycle/observability hooks and no-op/logging/Micrometer implementations.
- `registry`: in-memory session and subscription registries.
- `sender`: sender, broadcaster, send result, default broadcaster.
- `session`: transport-neutral session and registry contracts.
- `subscription`: destination, destination type, subscription, subscription registry.
- `resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`: imports `WebSocketAutoConfiguration`.

Directly related common structures inspected:

- `common-events`: canonical `EventEnvelope`, metadata, payload registry, shared catalog.
- `common-security`: JWT helper extraction ownership.
- `common-kafka`: EventEnvelope-based Kafka producer/consumer/serialization abstractions.
- `common-redis`: EventEnvelope-based Redis pub/sub abstractions.
- `common-web`: no realtime main classes remain, but `src/test/java/com/example/common/realtime/policy/RealtimeFlowClassificationPolicyTest.java` remains.

## 4. Responsibility and Boundary Review

common-websocket now owns appropriate reusable realtime primitives: identity/principal, session/connection registry, destination, subscription registry, send/broadcast abstractions, inbound/outbound frames, errors, codec, auth/authz extension points, lifecycle observers, and a Spring adapter boundary.

Ownership is mostly appropriate. The module is no longer just a Spring helper module at the source API level: core contracts are transport-neutral and Spring types are concentrated under `adapter.spring`.

Business/domain logic did not materially leak into common-websocket. There are no room-only public interfaces left in current main sources, and destination semantics are generic (`USER`, `SESSION`, `CHANNEL_GROUP`, `GLOBAL`).

Transport/framework concerns are mostly isolated. Spring source references are under `adapter.spring`, but the single `common-websocket` artifact still declares Spring WebSocket and OAuth2 resource-server dependencies in `common-websocket/build.gradle`. That keeps the source boundary clean but still makes the artifact Spring-shaped.

Kafka/Redis boundaries are clean. common-websocket does not import common-kafka or common-redis, and Kafka/Redis modules do not import common-websocket. EventEnvelope is the shared semantic event boundary.

common-web runtime overlap is mostly removed because no main realtime policy files remain. The overlap is not fully clean because the stale common-web realtime test still exists and fails compilation.

## 5. Dependency Direction

- `common-websocket` depends on `common-events` for `EventEnvelope<?>`.
- `common-websocket` depends on `common-security` only for the Spring JWT identity adapter.
- `common-security` depends on `common-core`.
- `common-kafka` and `common-redis` depend on `common-events`.
- `common-web` depends on `common-core`.
- No reviewed common module depends back on `common-websocket`.
- No circular dependency was found in the reviewed common scope.
- Spring coupling is isolated in source packages, but not fully isolated at artifact dependency level because the Spring adapter and core primitives are shipped together.

## 6. Package / API Design Review

### Issue 1

- Severity: High
- Exact file/class: `common-websocket/src/main/java/com/example/common/websocket/adapter/spring/SpringRealtimeMessageSender.java`, `SpringRealtimeMessageSender`, line 98.
- Why it matters before freeze: the generic catch block logs the exception object: `log.warn("[WS] Unexpected send error sessionId={}", sessionId, e);`. A custom codec or unexpected adapter failure can put credential or token text in the exception message or stack trace, violating the common logging safety rule.
- Exact recommended fix: change the log to a structured label without the exception object, for example `log.warn("[WS] unexpected send error sessionId={} reason=UNEXPECTED_ERROR", sessionId);`. Keep the cause in `RealtimeSendResult` and observer callback if needed, but do not log it in common. Add a test where `codec.encode` throws with a secret string and assert logs do not contain it.

### Issue 2

- Severity: High
- Exact file/class: `common-web/src/test/java/com/example/common/realtime/policy/RealtimeFlowClassificationPolicyTest.java`, line 14 and related references.
- Why it matters before freeze: common-web main realtime policy classes are gone, but this stale test still references them. `./gradlew.bat :common:common-web:test` fails at `compileTestJava` with 60 missing-symbol errors. That means common-web overlap removal is incomplete and the common-only test surface is not green.
- Exact recommended fix: delete the stale test and empty realtime policy package directories, or replace it with a guard test proving common-web no longer owns realtime policy. Do not recreate the removed policy in common-web.

### Issue 3

- Severity: High
- Exact file/class: `common-websocket/src/main/java/com/example/common/websocket/error/RealtimeError.java`, `RealtimeError`, lines 13-28.
- Why it matters before freeze: the standard error payload can be constructed with null `code`, blank/null `message`, and mutable `details`. `RealtimeErrorFrame` only checks that the error object is non-null, so invalid error frames can become part of the frozen wire model.
- Exact recommended fix: add a compact constructor that rejects null `code`, blank/null `message`, and defensively copies `details` with `Map.copyOf` when non-null. Add invariant tests for null code, blank message, and mutable details.

### Issue 4

- Severity: High
- Exact file/class: `common-websocket/src/main/java/com/example/common/websocket/adapter/spring/SpringJwtHandshakeHandler.java`, `SpringJwtHandshakeHandler`, lines 31-32.
- Why it matters before freeze: core identity allows anonymous principals, and `RealtimeAuthorizationPolicy.allowConnect` explicitly accepts principals that may be anonymous. If a custom `RealtimePrincipal` with `userId() == null` reaches this handler, `RealtimeIdentity.of(...)` throws. That makes the adapter narrower than the core contract.
- Exact recommended fix: when the stored principal is a `RealtimePrincipal` but not `RealtimeIdentity`, preserve authenticated principals with `RealtimeIdentity.of(...)`; for null user IDs, return `RealtimeIdentity.anonymous(rp.principalName())` when authorities are empty, or reject with a controlled safe failure if authorities are present. Add direct tests for custom authenticated and custom anonymous principals.

### Issue 5

- Severity: Medium
- Exact file/class: `common-websocket/build.gradle`, lines 35-36 and Spring adapter source package.
- Why it matters before freeze: source packages isolate Spring, but the module artifact still carries Spring WebSocket and OAuth2 resource-server dependencies. That weakens the claim that common-websocket is a reusable realtime foundation independent of Spring.
- Exact recommended fix: either split core and Spring adapter into separate common modules, or explicitly document and test that `common-websocket` is the Spring-enabled distribution while the core public contracts remain Spring-free. The cleaner freeze boundary is `common-websocket-core` plus `common-websocket-spring-adapter`.

### Issue 6

- Severity: Medium
- Exact file/class: `common-websocket/src/main/java/com/example/common/websocket/codec/JsonRealtimeFrameCodec.java`, `JsonRealtimeFrameCodec`, lines 25 and 42.
- Why it matters before freeze: `encode(null)` and `decode(null)` are not explicitly rejected through the common codec exception contract. `decode(null)` can escape as a non-codec exception, and `encode(null)` can produce `"null"` instead of rejecting an invalid frame.
- Exact recommended fix: add explicit null/blank guards in `encode` and `decode`, throwing `RealtimeCodecException` with safe messages. Add tests for null frame, null raw payload, and blank raw payload.

### Issue 7

- Severity: Medium
- Exact file/class: `common-websocket/src/main/java/com/example/common/websocket/inbound/DefaultRealtimeInboundFrameHandler.java`, `DefaultRealtimeInboundFrameHandler`, lines 86-96.
- Why it matters before freeze: duplicate subscribe detection is a read-before-write check. Under concurrent duplicate subscribe frames, both handlers can observe no existing subscription, and both can report success even though the registry stores one subscription.
- Exact recommended fix: move duplicate detection into an atomic registry operation, such as `subscribeIfAbsent` returning `{created, subscription}`, or return a richer result from `subscribe`. Then base observer/error behavior on that atomic result. Add a concurrent duplicate subscribe test.

### Issue 8

- Severity: Medium
- Exact file/class: `common-websocket/src/main/java/com/example/common/websocket/adapter/spring/SpringRealtimeMessageSender.java`, `SpringRealtimeMessageSender`, lines 53-61 and 75-102.
- Why it matters before freeze: session registration/removal and per-session locks are separate maps. Replacement or unregister during send can remove the lock while a send is still in flight, allowing a second send to create another lock for the same session ID.
- Exact recommended fix: make register, unregister, dead-session cleanup, and send coordinate on the same per-session lock lifecycle, or use one holder object containing both session and lock. Add concurrency tests for unregister during send and replacement during send.

### Issue 9

- Severity: Medium
- Exact file/class: `common-websocket/src/main/java/com/example/common/websocket/registry/InMemoryRealtimeSessionRegistry.java`, `InMemoryRealtimeSessionRegistry`, lines 20-37.
- Why it matters before freeze: the registry assumes `session`, `session.sessionId()`, and `session.principal()` are valid. A custom `RealtimeSession` can cause null-pointer failures instead of contract-level rejection.
- Exact recommended fix: validate `session` non-null, non-blank `sessionId`, non-null principal, and non-null `connectedAt` if the registry depends on them. Add registry invariant tests.

### Issue 10

- Severity: Low
- Exact file/class: `common-websocket/src/main/java/com/example/common/websocket/registry/InMemoryRealtimeSubscriptionRegistry.java`, `InMemoryRealtimeSubscriptionRegistry`, lines 37-42.
- Why it matters before freeze: after the last unsubscribe for a session, the empty per-session map remains in `bySession`. Public behavior is correct, but long-running in-memory usage can accumulate empty entries.
- Exact recommended fix: after removal, remove the session key if its map is empty. Add a small cleanup assertion, preferably through a package-visible count helper or behavior that proves no stale session bucket remains.

### Issue 11

- Severity: Low
- Exact file/class: `common-websocket/src/main/java/com/example/common/websocket/observer/LoggingRealtimeObserver.java`, lines 68-79.
- Why it matters before freeze: the default observer logs caller-provided `reason` and `context` strings. Current common callers pass structured labels, but the public observer can be called with unsafe free-form values.
- Exact recommended fix: either document these parameters as structured labels only and add guard tests for common callers, or sanitize/truncate free-form values before logging.

### Issue 12

- Severity: Low
- Exact file/class: `common-websocket/src/main/java/com/example/common/websocket/frame/RealtimeCommandFrame.java`, class comment line 7.
- Why it matters before freeze: the comment mentions an ack command, but `RealtimeCommandType` has only `SUBSCRIBE`, `UNSUBSCRIBE`, `PING`, and `PONG`. That is stale API documentation.
- Exact recommended fix: remove the ack reference or add an explicit command only if the standard is meant to support it now.

## 7. Realtime Standard Readiness

- Session model: mostly ready. It has a transport-neutral `RealtimeSession` and registry, and replacement index behavior is much improved. Missing hard validation and adapter replacement concurrency tests remain.
- Principal/identity model: mostly ready. It supports authenticated and anonymous identities, immutable authorities, and attributes. The Spring handshake handler must be fixed to honor the core anonymous principal shape.
- Inbound/outbound frame model: mostly ready. Sealed frame types and discriminator decode are strong. Null codec handling and stale command documentation remain.
- Subscription model: close. The registry prevents duplicate storage, but the inbound handler needs atomic duplicate outcome semantics.
- Destination model: mostly ready. It is broker-independent and generic. USER identifiers are validated as UUIDs; GLOBAL shape is enforced.
- Error model: not freeze-ready. `RealtimeError` needs constructor invariants and immutable details.
- Observability/lifecycle model: usable, with no-op, logging, and Micrometer observers. Logging safety still has a send-side exception leak and some free-form label risks.
- Serialization/codec model: mostly ready. Public codec has no typed decode bypass, and decode is discriminator-based. Null/blank handling needs explicit guards.
- Auth/authz extension points: mostly ready. Subscribe authorization is enforced through the common inbound command path, and connect authorization is enforced in the Spring handshake interceptor. Adapter handling of custom anonymous principals needs correction.
- Send/broadcast abstractions: mostly ready. Sender/broadcaster contracts are typed and transport-neutral. Spring sender concurrency and unexpected-error logging need fixes.
- Adapter layering: source layering is clean; artifact-level Spring coupling remains a medium design concern.
- Cleanup semantics: session registry cleanup is improved; subscription registry and Spring sender cleanup need minor hardening.

## 8. Cross-Common Consistency

- EventEnvelope consistency: good. `RealtimeEventFrame` carries `EventEnvelope<?>`, and Kafka/Redis also use `EventEnvelope<?>` as their semantic event boundary.
- Naming consistency: mostly good. `Realtime*` naming is consistent in common-websocket. The `RealtimeDestination.channel(...)` factory is acceptable but should be treated as a semantic channel/group, not a Redis channel.
- Dependency layering: clean inside reviewed common scope. common-websocket points to common-events and common-security; Kafka/Redis point to common-events; common-security/common-web point to common-core.
- Duplicate abstractions: no duplicate websocket frame/message standards remain in current common-websocket main sources.
- Conflicting realtime concepts: common-web main runtime overlap is removed, but the stale common-web realtime test must be removed or converted to a guard.
- Destination/event ownership: common-websocket owns realtime destinations; common-events owns semantic event envelopes and payload registry/catalog.
- JWT ownership: common-security owns JWT extraction via `JwtHelper`; common-websocket only adapts that into realtime identity in the Spring adapter.
- Transport policy ownership: Kafka/Redis own broker-specific routing contexts; common-websocket owns websocket/realtime delivery primitives. No Kafka/Redis hardwire exists in websocket core.

## 9. Freeze Blockers

High: must fix before freeze

- `SpringRealtimeMessageSender` logs unexpected exception objects.
- `RealtimeError` lacks core invariants and immutable details.
- `SpringJwtHandshakeHandler` mishandles custom anonymous realtime principals.
- `common-web` has a stale realtime policy test that fails common-web test compilation.

Medium: should fix before freeze

- Source boundary is Spring-clean, but artifact dependencies still bundle Spring adapter dependencies with core.
- `JsonRealtimeFrameCodec` lacks explicit null/blank guards.
- Subscribe duplicate outcome is not atomic in the common inbound handler.
- Spring sender replacement/unregister/send locking is not proven safe.
- Session registry should validate custom session inputs before indexing.

Low: can fix later

- Subscription registry leaves empty per-session maps after last unsubscribe.
- Default logging observer accepts free-form reason/context labels.
- Command frame comment mentions an unimplemented ack command.

## 10. Minimal Remaining Refactor Plan

1. Remove or replace the stale `common-web` realtime policy test and remove empty realtime policy source directories if they are intentionally dead.
2. Sanitize `SpringRealtimeMessageSender` unexpected-error logging and add a credential-safety regression test for codec/send exceptions.
3. Add `RealtimeError` constructor validation and immutable `details`; add invariant tests.
4. Fix `SpringJwtHandshakeHandler` for custom authenticated and anonymous `RealtimePrincipal` implementations; add direct adapter tests.
5. Add codec null/blank guards and tests.
6. Make subscribe duplicate detection atomic at the registry/handler boundary; add a concurrent duplicate subscribe test.
7. Add Spring sender concurrency tests around unregister/replacement during send, then adjust locking if the tests expose stale sends.
8. Decide whether to split core and Spring adapter artifacts before freeze; if not splitting now, document the artifact boundary clearly and keep guard tests around core public contracts.

## 11. Required Tests Before Freeze

Existing common-websocket tests are real and useful. `./gradlew.bat :common:common-websocket:test` passed with 98 tests covering frame contracts, discriminator decode, guard tests, identity/destination/subscription invariants, registries, inbound command authz, broadcaster behavior, logging safety, Spring handshake, Spring sender cleanup, and auto-config.

Related common tests:

- `./gradlew.bat :common:common-events:test` passed.
- `./gradlew.bat :common:common-kafka:test` passed.
- `./gradlew.bat :common:common-redis:test` passed.
- `./gradlew.bat :common:common-security:test` passed.
- `./gradlew.bat :common:common-web:test` failed at test compilation because `RealtimeFlowClassificationPolicyTest` references removed common-web realtime policy classes.

Still required before freeze:

- Handshake log safety: add an explicit test for `SpringRealtimeMessageSender` unexpected exception logging, not only JWT/observer paths.
- Session replacement correctness and concurrency: add Spring sender unregister/replacement during send tests; keep the registry replacement tests.
- Typed frame/send/codec contracts: add null rejection tests for `RealtimeFrameCodec.encode`, `RealtimeFrameCodec.decode`, and sender/broadcaster inputs.
- No public typed decode bypass: keep the guard test that codec exposes only `encode#1` and `decode#1`.
- Discriminator-based decode: keep existing tests for EVENT, COMMAND, ERROR, missing, null, non-text, and unknown discriminator.
- Frame/destination/subscription/identity invariants: add `RealtimeError` invariants and session registry input invariants.
- Inbound command handling + subscribe authz: add concurrent duplicate subscribe and malformed-but-non-codec-exception tests.
- Broadcaster/sender behavior: add invalid/null destination/frame/session input tests and Spring sender concurrency tests.
- Adapter auto-config behavior: add a test proving no inbound handler is created without a `RealtimeAuthorizationPolicy`, and keep the override tests.
- EventEnvelope outbound integration: add a test that `RealtimeEventFrame.of(envelope)` preserves the envelope object and rejects null; existing JSON tests are a good base.
- common-security JWT extraction ownership: add/keep a test that `JwtRealtimeIdentityResolver` delegates user and authority extraction to `JwtHelper` without logging token material.
- common-web overlap removal/guard tests: delete the stale policy test or replace it with a guard that asserts common-web has no realtime policy owner.

## 12. Final Verdict

- Freeze common-websocket now? No.
- Minimum common-only changes before freeze:
  - Fix unsafe exception logging in `SpringRealtimeMessageSender`.
  - Remove/replace the stale common-web realtime policy test.
  - Enforce `RealtimeError` invariants.
  - Fix `SpringJwtHandshakeHandler` for custom anonymous realtime principals.
  - Add codec null/blank guards and the missing safety tests above.
- What can wait until later:
  - Empty subscription bucket cleanup.
  - Logging observer free-form label hardening if all common callers stay label-only.
  - Command frame comment cleanup.
  - Artifact split, only if the team accepts the current single-artifact Spring adapter boundary for the freeze.
- Services were not considered. No service-level blockers or service duplication were reviewed.
