## 1. Executive Summary
- common-websocket is NOT freeze-ready.
- The refactor closes the obvious stale-session cleanup and send-path holes, but inbound processing is still not atomic with session replacement. A stale physical session can pass the current-session check, be replaced, and then mutate subscription state after replacement cleanup.

## 2. What Was Verified
- Session ownership
  - `SpringRealtimeLifecycleAdapter.onTextMessage(...)` checks `sender.isCurrentSpringSession(sessionId, session)` before dispatch.
  - `onDisconnected(...)` and `onTransportError(...)` route cleanup through `sender.unregisterSpringSession(sessionId, expectedSession, ...)`, so stale physical cleanup cannot remove the current mapped Spring session.
  - Ownership is not held through inbound decode/authorization/subscription mutation, leaving a replacement race.
- Inbound lifecycle
  - Stale physical sessions are rejected when they are already stale before `onTextMessage(...)` starts.
  - Missing common sessions fail closed before common inbound dispatch.
  - No fallback common/current session object is created by `onTextMessage(...)`.
  - `DefaultRealtimeInboundFrameHandler` can still mutate subscriptions after a session becomes stale between the ownership check and `subscribeIfAbsent(...)` / `unsubscribe(...)`.
- Replacement behavior
  - `connectSpringSession(...)` replaces the mapped physical session under a per-session lock and clears old subscription state on replacement.
  - Replacement does not unregister the new common session when an old disconnect/error arrives later.
  - Replacement does not protect against an already-admitted stale inbound frame recreating or removing subscription state after replacement cleanup.
- Send race handling
  - `SpringRealtimeMessageSender.send(...)` captures the mapped session, enters the per-session lock, re-reads the current mapped session, and returns `SESSION_REPLACED` instead of sending when the mapping changed.
  - Send/replacement race tests cover the stale captured-session case and passed.
- Public API hardening
  - `connectSpringSession(...)`, `unregisterSpringSession(...)`, and `isCurrentSpringSession(...)` are package-private, not public API.
  - Public lifecycle cleanup paths are guarded by expected physical session identity.
  - `RealtimeInboundFrameHandler` remains a public mutating entry point that can be called without a registered/current session guard.
- Boundaries and architecture
  - Core public contracts do not expose Spring `WebSocketSession`; Spring usage is contained in the adapter/autoconfiguration package.
  - Public sender and codec contracts remain `RealtimeFrame` based.
  - Inbound decode still goes through `RealtimeFrameCodec.decode(String)` and the JSON codec uses the `frameType` discriminator.
  - `RealtimeEventFrame` still carries `EventEnvelope<?>`.
  - No Kafka/Redis dependency or business/domain logic was found in common-websocket.
- Tests
  - Ran `./gradlew :common:common-websocket:test --rerun-tasks`; result: BUILD SUCCESSFUL, 142 tests, 0 failures.
  - Tests cover stale inbound after replacement, missing common session, stale cleanup, replacement cleanup, stale captured send, contract boundaries, and codec shape.
  - Tests do not cover replacement occurring after inbound ownership check but before subscription mutation.

## 3. Remaining Problems
- High
  - Inbound ownership is checked only before common inbound dispatch, not across the full inbound mutation lifecycle. `SpringRealtimeLifecycleAdapter.onTextMessage(...)` checks current physical ownership, then separately looks up the common session and calls `inboundFrameHandler.handleRawFrame(...)`. After `isCurrentSpringSession(...)` releases the sender lock, a replacement can run and clear subscriptions; the old inbound frame can then continue into `DefaultRealtimeInboundFrameHandler.handleSubscribe(...)` or `handleUnsubscribe(...)` and mutate `RealtimeSubscriptionRegistry` for the now-current logical session ID. This violates the stale-session and replacement invariants.
- Medium
  - `RealtimeInboundFrameHandler` remains a public mutating contract with no session-registry/current-session validation. The Spring lifecycle path guards it, but direct callers can still process inbound frames for arbitrary missing or stale session IDs.
  - Test coverage is still partial for freeze confidence because it proves stale inbound only when the old physical session is stale before `onTextMessage(...)` starts, not when replacement races after the guard.
- Low
  - Some public/core contracts still have weak explicit argument validation compared with the lifecycle/sender paths, but this was not the freeze blocker.

## 4. Freeze Checklist
- FAIL - no stale physical session can affect current logical session state
- FAIL - no inbound processing from stale or missing session
- FAIL - replacement does not leak old subscription authorization/state
- PASS - send cannot target stale physical session after replacement race
- PASS - no public lifecycle escape hatch bypasses guarded cleanup
- PARTIAL - lifecycle behavior is standardized through one guarded path
- PASS - core public contracts do not expose Spring WebSocketSession
- PASS - public send/codec APIs remain RealtimeFrame based
- PASS - no public typed decode bypass
- PASS - single discriminator-based inbound decode path remains intact
- PASS - RealtimeEventFrame still uses EventEnvelope<?>
- PASS - no Kafka/Redis coupling added
- PARTIAL - tests are sufficient for freeze confidence

## 5. Classes Inspected
- `SpringRealtimeLifecycleAdapter`
- `SpringRealtimeMessageSender`
- `SpringRealtimeSession`
- `DefaultRealtimeInboundFrameHandler`
- `RealtimeInboundFrameHandler`
- `InMemoryRealtimeSessionRegistry`
- `RealtimeSessionRegistry`
- `InMemoryRealtimeSubscriptionRegistry`
- `RealtimeSubscriptionRegistry`
- `JsonRealtimeFrameCodec`
- `RealtimeFrameCodec`
- `RealtimeFrame`
- `RealtimeCommandFrame`
- `RealtimeEventFrame`
- `RealtimeErrorFrame`
- `RealtimeMessageSender`
- `RealtimeBroadcaster`
- `DefaultRealtimeBroadcaster`
- `SpringHandshakeInterceptor`
- `SpringRealtimeHandshakeHandler`
- `WebSocketAutoConfiguration`
- `SpringRealtimeInboundGuardTest`
- `SpringRealtimeLifecycleAdapterTest`
- `SpringRealtimeLifecycleAdapterRaceTest`
- `SpringRealtimeMessageSenderTest`
- `WebSocketContractGuardTest`
- `RealtimeFrameContractTest`

## 6. Final Verdict
NOT READY
