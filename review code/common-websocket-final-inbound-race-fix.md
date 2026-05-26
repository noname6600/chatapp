## What changed
- Inbound dispatch in SpringRealtimeLifecycleAdapter is now guarded at mutation time, not only pre-entry.
- Added sender-level guarded execution primitive runIfCurrentSpringSession(sessionId, expectedSession, action) that:
  - acquires the per-session lock,
  - verifies expected physical session is still current,
  - executes inbound mutation action only when ownership still matches.
- onTextMessage now uses a two-step guard:
  - pre-check current physical ownership to fail fast,
  - guarded execution re-check under lock before calling inbound mutation path.
- This closes the race where replacement could happen between pre-check and subscribe/unsubscribe mutation.
- DefaultRealtimeInboundFrameHandler boundary was hardened to require a current common session in RealtimeSessionRegistry before any decode/authz/subscription mutation.
- WebSocketAutoConfiguration now wires RealtimeSessionRegistry into DefaultRealtimeInboundFrameHandler.

## Invariant now enforced
- For a given logical session id, replacement and inbound subscription mutation are serialized by the same per-session lock.
- If replacement wins before guarded inbound mutation starts, stale inbound action is dropped and cannot mutate RealtimeSubscriptionRegistry.
- Missing/unregistered common session fails closed at inbound handler boundary.
- Stale physical session cannot recreate or remove subscription state after replacement cleanup.

## Tests added
- SpringRealtimeInboundGuardTest
  - replacement_after_inbound_entry_cannot_apply_stale_subscribe_mutation
  - replacement_cleanup_wins_over_stale_unsubscribe_attempt
  - existing stale/missing inbound guard tests kept and updated for hardened constructor contract.
- SpringRealtimeLifecycleAdapterTest
  - updated to verify guarded execution callback path through runIfCurrentSpringSession.
- DefaultRealtimeInboundFrameHandlerTest
  - updated for new session-registry dependency.
  - added missing common session fail-closed test.

Validation run:
- .\gradlew.bat :common:common-websocket:test -> BUILD SUCCESSFUL
- .\gradlew.bat :common:common-websocket:compileJava -> BUILD SUCCESSFUL

## Freeze verdict
READY
