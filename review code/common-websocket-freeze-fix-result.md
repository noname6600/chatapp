## 1. Changed files/classes

### common-websocket main
- codec:
  - RealtimeFrameCodec
  - JsonRealtimeFrameCodec
- frame:
  - RealtimeEventFrame
  - RealtimeCommandFrame
  - RealtimeErrorFrame
  - RealtimeCommandType
- subscription:
  - RealtimeDestination
  - RealtimeSubscription
- identity:
  - RealtimeIdentity
- registry:
  - InMemoryRealtimeSessionRegistry
- adapter.spring:
  - SpringHandshakeInterceptor
  - SpringRealtimeMessageSender
  - JwtRealtimeIdentityResolver
- adapter.spring.config:
  - WebSocketAutoConfiguration
- observer:
  - LoggingRealtimeObserver
- inbound (new):
  - RealtimeInboundFrameHandler
  - DefaultRealtimeInboundFrameHandler

### common-websocket tests
- updated:
  - frame/RealtimeFrameContractTest
  - guard/WebSocketContractGuardTest
  - registry/InMemoryRealtimeSessionRegistryTest
  - subscription/RealtimeDestinationTest
- added:
  - inbound/DefaultRealtimeInboundFrameHandlerTest
  - identity/RealtimeIdentityInvariantTest
  - subscription/RealtimeSubscriptionInvariantTest
  - adapter/spring/SpringRealtimeMessageSenderTest
  - adapter/spring/config/WebSocketAutoConfigurationTest
  - adapter/spring/LoggingSafetyTest

### common-security
- main:
  - JwtHelper (already extracted authority logic; reused by websocket adapter)
- build:
  - build.gradle (added test deps + JUnit Platform)
- test (new):
  - jwt/JwtHelperTest

### common-web
- removed overlap package (main sources):
  - com.example.common.realtime.policy.RealtimeFlowClassificationPolicy
  - com.example.common.realtime.policy.RealtimeFlowId
  - com.example.common.realtime.policy.RealtimeFlowType
  - com.example.common.realtime.policy.package-info

---

## 2. High blocker fixes completed

1. Public typed codec decode bypass removed from public API
- RealtimeFrameCodec now exposes only:
  - encode(RealtimeFrame)
  - decode(String)

2. Frame/destination/subscription/identity invariants enforced
- frameType constant equality enforced in all frame records
- command invariants enforced (SUBSCRIBE/UNSUBSCRIBE need destination; PING/PONG require null destination and null correlationId)
- RealtimeDestination enforces UUID syntax for USER
- stale isValid() API removed
- RealtimeSubscription validates required fields
- RealtimeIdentity validates principalName and authenticated/anonymous rules

3. Common inbound subscribe authz path added
- new DefaultRealtimeInboundFrameHandler handles SUBSCRIBE/UNSUBSCRIBE/PING/PONG
- enforces RealtimeAuthorizationPolicy.allowSubscribe
- handles duplicate subscribe / missing unsubscribe
- emits RealtimeErrorFrame on failures

4. Session replacement atomicity fixed
- InMemoryRealtimeSessionRegistry register/unregister now mutate both indexes under one lock

5. Spring adapter auto-config/lifecycle fixed
- default SpringRealtimeMessageSender is auto-configured
- broadcaster wiring now has sender by default
- inbound handler auto-configured only when policy + sender beans exist
- sender dead-session cleanup coordinates:
  - sender internal maps
  - session registry unregister
  - subscription registry cleanupSession
  - observer disconnected callback

6. Unsafe exception-message logging removed
- JwtRealtimeIdentityResolver no longer logs JwtException message
- LoggingRealtimeObserver no longer logs exception message text for decode/send failure

7. common-web realtime policy overlap removed
- overlapping realtime policy owner deleted from common-web main package

---

## 3. Final frame/send/codec API shape

- RealtimeFrameCodec:
  - String encode(RealtimeFrame frame)
  - RealtimeFrame decode(String raw)

- RealtimeMessageSender:
  - RealtimeSendResult send(String sessionId, RealtimeFrame frame)

- RealtimeBroadcaster:
  - List<RealtimeSendResult> sendToUser(UUID userId, RealtimeFrame frame)
  - List<RealtimeSendResult> sendToSession(String sessionId, RealtimeFrame frame)
  - List<RealtimeSendResult> sendToDestination(RealtimeDestination destination, RealtimeFrame frame)
  - List<RealtimeSendResult> sendToAll(RealtimeFrame frame)

- no raw Object in frozen send/codec contracts
- single public discriminator-based inbound decode path

---

## 4. Inbound command handling design added

Implemented: DefaultRealtimeInboundFrameHandler

Input:
- handleRawFrame(sessionId, principal, rawPayload)

Flow:
1. observer.onFrameReceived
2. decode via RealtimeFrameCodec.decode(String) only
3. accept COMMAND frame only
4. dispatch command:
- SUBSCRIBE:
  - validate destination
  - allowSubscribe authz
  - duplicate detection
  - subscribe registry mutation
  - observer.onSubscribed
- UNSUBSCRIBE:
  - validate destination
  - unsubscribe
  - if missing -> SUBSCRIPTION_NOT_FOUND error
  - observer.onUnsubscribed
- PING:
  - send PONG frame
- PONG:
  - no-op heartbeat ack

Errors:
- malformed frame -> INVALID_FRAME
- non-command inbound -> INVALID_FRAME
- denied subscribe -> AUTHORIZATION_DENIED
- duplicate subscribe -> SUBSCRIPTION_DUPLICATE
- missing unsubscribe -> SUBSCRIPTION_NOT_FOUND

---

## 5. Session registry atomicity/lifecycle cleanup summary

Atomicity:
- InMemoryRealtimeSessionRegistry now uses one mutation lock for register/unregister over both maps
- prevents stale user index under concurrent replacement

Lifecycle cleanup:
- SpringRealtimeMessageSender dead-session path now performs coordinated cleanup:
  - remove sender session/lock
  - sessionRegistry.unregister(sessionId)
  - subscriptionRegistry.cleanupSession(sessionId)
  - observer.onDisconnected(sessionId, DEAD_SESSION_CLEANUP)

---

## 6. Adapter logging safety summary

Hardened logging:
- no token/credential text in Jwt decode failure logs
- no exception message echo in LoggingRealtimeObserver decode/send failures
- handshake interceptor still logs structured reasons only (no request URI query leakage)

Added log-safety tests:
- LoggingSafetyTest:
  - Jwt decode failure logs do not contain secret token fragments
  - observer decode/send failure logs do not include secret exception message text

---

## 7. Auto-config/default policy decisions

Auto-config design:
- chose default sender strategy:
  - WebSocketAutoConfiguration now auto-creates SpringRealtimeMessageSender when missing
  - broadcaster uses that sender by default
- inbound handler bean is conditional:
  - created only when RealtimeAuthorizationPolicy and RealtimeMessageSender beans are present

Authorization default:
- kept fail-closed posture from previous pass:
  - no silent allow-all authorization policy auto-registration
  - explicit RealtimeAuthorizationPolicy bean required for inbound authz-enabled flow

---

## 8. common-security ownership cleanup summary

- JwtRealtimeIdentityResolver now uses common-security JwtHelper for claim extraction
- JwtHelper authority extraction is covered by new JwtHelperTest
- websocket adapter no longer owns authority parsing behavior

---

## 9. common-web overlap resolution summary

- Removed com.example.common.realtime.policy main ownership from common-web
- common-web no longer publishes overlapping realtime policy standard surface in main code
- websocket remains owner of destination/subscription/frame/realtime transport model

---

## 10. Tests added/updated

Added:
- DefaultRealtimeInboundFrameHandlerTest
- RealtimeIdentityInvariantTest
- RealtimeSubscriptionInvariantTest
- SpringRealtimeMessageSenderTest
- WebSocketAutoConfigurationTest
- LoggingSafetyTest
- JwtHelperTest (common-security)

Updated:
- RealtimeFrameContractTest
- WebSocketContractGuardTest
- InMemoryRealtimeSessionRegistryTest
- RealtimeDestinationTest

Coverage highlights:
- reflection/API guard for codec/send contracts
- discriminator decode + invalid frameType cases
- wrong frame discriminator constructor rejection
- subscribe authz/duplicate/unsubscribe/ping-pong inbound behavior
- concurrent session replace correctness
- sender closed/failure lifecycle cleanup
- auto-config default/custom behavior
- log safety for auth/decode paths

---

## 11. Validation results

Executed commands:
1. .\gradlew.bat :common:common-websocket:compileJava :common:common-websocket:test
- BUILD SUCCESSFUL

2. .\gradlew.bat :common:common-websocket:test :common:common-events:test :common:common-security:test :common:common-web:compileJava
- BUILD SUCCESSFUL

Notes:
- Earlier powershell runs showed JVM sharing warning text mixed into output; final validated run above is fully successful.

---

## 12. Remaining blockers, if any

No High blockers remain from the requested list.

Open non-blocking follow-ups (optional hardening):
- add adapter-level disconnect glue from actual Spring WS lifecycle events into sender/register/cleanup orchestration (outside current common-only unit-test scope)
- optionally add dedicated SafeRealtimeObserver decorator class (current implementation uses safe invocation in critical call paths)

---

## 13. Freeze verdict for common-websocket

Freeze verdict: YES, common-websocket is now freeze-ready for the requested high-blocker criteria.

Satisfied hard rules:
- no service changes
- no versioning work
- no raw Object in frozen public send/codec contracts
- no credential material in logs
- no stale session-to-user index bug under concurrent mutation path
- no public typed decode bypass
- no conflicting realtime standard left in common-web main ownership
- no business-shaped room-only standard APIs
