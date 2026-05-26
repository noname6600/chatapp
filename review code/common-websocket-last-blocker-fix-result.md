## 1. Changed files/classes

### common-websocket main
- adapter/spring:
  - SpringRealtimeMessageSender
  - SpringJwtHandshakeHandler
- codec:
  - JsonRealtimeFrameCodec
- error:
  - RealtimeError
- inbound:
  - DefaultRealtimeInboundFrameHandler
- registry:
  - InMemoryRealtimeSubscriptionRegistry
- subscription:
  - RealtimeSubscriptionRegistry

### common-websocket test
- adapter/spring:
  - LoggingSafetyTest
  - SpringRealtimeMessageSenderTest
  - SpringJwtHandshakeHandlerTest (new)
- error:
  - RealtimeErrorInvariantTest (new)
- frame:
  - RealtimeFrameContractTest
- inbound:
  - DefaultRealtimeInboundFrameHandlerTest

### common-web
- deleted stale test:
  - src/test/java/com/example/common/realtime/policy/RealtimeFlowClassificationPolicyTest.java

---

## 2. Logging safety fix summary

Fixed unsafe unexpected send logging in SpringRealtimeMessageSender:
- before: logged exception object on unexpected path
- after: logs only structured safe label
  - `[WS] unexpected send error sessionId={} reason=UNEXPECTED_ERROR`

No raw exception message/stack trace is logged in the common default path.
Detailed cause is still retained in:
- RealtimeSendResult.failure(..., cause)
- observer callback onSendFailure(..., cause)

Added regression test:
- codec.encode throws RuntimeException("SECRET_TOKEN_123")
- assert sender logs do not contain `SECRET_TOKEN_123`
- assert behavior still returns `UNEXPECTED_ERROR` and observer callback is invoked

---

## 3. RealtimeError invariant fix summary

RealtimeError now enforces constructor invariants:
- reject null `code`
- reject null/blank `message`
- defensively copy `details` with `Map.copyOf` when non-null

Added tests:
- null code rejected
- blank message rejected
- mutable input details map cannot mutate stored details
- stored details map is immutable

---

## 4. SpringJwtHandshakeHandler fix summary

SpringJwtHandshakeHandler now handles custom RealtimePrincipal safely:
- if attribute principal is already RealtimeIdentity: returns as-is
- if custom RealtimePrincipal with non-null userId: adapts to authenticated RealtimeIdentity preserving name/authorities/attributes
- if custom RealtimePrincipal with null userId and empty authorities: adapts to anonymous RealtimeIdentity preserving name/attributes
- if custom anonymous principal has non-empty authorities: safe rejection (`null`), no throw

Added tests:
- custom authenticated principal adaptation
- custom anonymous principal adaptation
- authorities/attributes/name preservation
- invalid anonymous shape rejection without exception

---

## 5. common-web stale test cleanup summary

Removed stale test referencing deleted realtime policy classes:
- common-web/src/test/java/com/example/common/realtime/policy/RealtimeFlowClassificationPolicyTest.java

No removed policy classes were recreated.
common-web no longer carries that stale owner surface through tests.

---

## 6. Codec null/blank guard summary

JsonRealtimeFrameCodec hardened with explicit guards:
- encode(null) -> RealtimeCodecException("Realtime frame must not be null")
- decode(null) -> RealtimeCodecException("Realtime frame payload must not be null")
- decode(blank) -> RealtimeCodecException("Realtime frame payload must not be blank")

Added tests for these three guard cases.

---

## 7. Duplicate subscribe atomicity summary

Implemented atomic subscribe-if-absent flow:
- added `subscribeIfAbsent(...)` to RealtimeSubscriptionRegistry
- added `SubscriptionUpsertResult(subscription, created)` result type
- InMemoryRealtimeSubscriptionRegistry uses `putIfAbsent` atomically
- DefaultRealtimeInboundFrameHandler now uses atomic result:
  - `created=true` -> success + observer.onSubscribed
  - `created=false` -> standard SUBSCRIPTION_DUPLICATE error frame

Also improved registry cleanup on unsubscribe:
- removes empty per-session bucket after last subscription removal

Added concurrent test:
- two concurrent SUBSCRIBE frames for same session+destination
- only one subscription created
- duplicate path emits standard duplicate error

---

## 8. Tests added/updated

Added:
- common-websocket/src/test/java/com/example/common/websocket/adapter/spring/SpringJwtHandshakeHandlerTest.java
- common-websocket/src/test/java/com/example/common/websocket/error/RealtimeErrorInvariantTest.java

Updated:
- common-websocket/src/test/java/com/example/common/websocket/adapter/spring/LoggingSafetyTest.java
- common-websocket/src/test/java/com/example/common/websocket/adapter/spring/SpringRealtimeMessageSenderTest.java
- common-websocket/src/test/java/com/example/common/websocket/frame/RealtimeFrameContractTest.java
- common-websocket/src/test/java/com/example/common/websocket/inbound/DefaultRealtimeInboundFrameHandlerTest.java

Deleted:
- common-web/src/test/java/com/example/common/realtime/policy/RealtimeFlowClassificationPolicyTest.java

---

## 9. Validation results

Required commands executed:

1. `./gradlew :common:common-websocket:test`
- BUILD SUCCESSFUL

2. `./gradlew :common:common-web:test`
- BUILD SUCCESSFUL

3. `./gradlew :common:common-websocket:compileJava :common:common-web:compileJava`
- BUILD SUCCESSFUL

Note:
- One run printed JVM warning text in PowerShell stream, but Gradle result was still BUILD SUCCESSFUL.

---

## 10. Remaining blockers, if any

No remaining High blockers from the requested list.

Residual medium design concerns (not blocking this requested pass):
- single artifact still bundles core + Spring adapter dependencies (artifact boundary concern)
- sender lock/session replacement model can be further hardened with a single holder abstraction if desired

---

## 11. Freeze verdict for common-websocket

Freeze verdict for this blocker list: YES.

All requested remaining blockers are fixed:
- unsafe exception logging: fixed
- stale common-web realtime policy test: removed
- RealtimeError invariants: enforced
- custom anonymous principal handling in SpringJwtHandshakeHandler: fixed
- codec null/blank safety: enforced
- duplicate subscribe atomicity: tightened with atomic subscribe-if-absent and concurrent test coverage
