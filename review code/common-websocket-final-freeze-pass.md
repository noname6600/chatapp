## What changed
- Hardened Spring lifecycle ownership in Spring sender:
  - Removed public lifecycle escape hatches by restricting lifecycle mutation methods to internal/package scope in SpringRealtimeMessageSender.
  - Kept guarded disconnect cleanup path requiring expected physical WebSocketSession.
  - Added current physical-session check helper used by lifecycle inbound guard.
- Closed stale inbound mutation path in SpringRealtimeLifecycleAdapter:
  - onTextMessage now verifies the provided physical session is still current before delegating inbound handling.
  - onTextMessage now fails closed when common RealtimeSession is missing (no anonymous fallback construction).
  - Added structured internal observer notifications for stale/missing inbound session paths.
- Enforced replacement subscription safety:
  - Replacement cleanup now clears subscription state for the logical session id in the same guarded replacement path.
  - Replacement therefore requires explicit re-subscribe via the normal inbound authorization flow.
- Closed send-to-stale race in SpringRealtimeMessageSender.send:
  - send now validates arguments fail-fast.
  - send re-reads current mapped physical session under the per-session lock.
  - If mapping changed, send returns structured failure SESSION_REPLACED and does not send to stale socket.
- Added fail-fast validation at key public entry points:
  - SpringRealtimeMessageSender constructor and send.
  - SpringRealtimeLifecycleAdapter lifecycle methods.
  - DefaultRealtimeInboundFrameHandler.handleRawFrame.
- Added/updated tests for all requested blocker paths:
  - SpringRealtimeInboundGuardTest:
    - stale physical session cannot mutate subscriptions through inbound
    - missing/unregistered common session cannot process inbound frames
  - SpringRealtimeMessageSenderTest:
    - stale-send race returns SESSION_REPLACED and does not send to stale socket
    - replacement cleanup behavior and guarded cleanup assertions updated
    - reflection check verifies unguarded lifecycle methods are no longer public API
    - fail-fast argument validation tests
  - SpringRealtimeLifecycleAdapterTest:
    - stale inbound and missing common-session fail-closed behavior
    - fail-fast lifecycle argument validation
  - SpringRealtimeLifecycleAdapterRaceTest:
    - replacement path now asserts subscription state is cleared
  - LoggingSafetyTest updated to internal connect API.

## Remaining risks
- MicrometerRealtimeObserver auto-composition policy is unchanged in this pass. This does not block the requested lifecycle/stale-session/replacement/send-race freeze criteria, but if strict observability auto-wiring parity is required for freeze policy, it should be addressed explicitly.
- RealtimeSession.attributes() mutability semantics remain as-is (delegate map exposure through SpringRealtimeSession). No blocker behavior found in this pass, but explicit contract documentation may still be useful.

## Freeze verdict
READY
