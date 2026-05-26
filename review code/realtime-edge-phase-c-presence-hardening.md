# Phase C Presence Hardening

## What Was Hardened

- Added controller-level HTTP coverage for `PresenceEdgeCommandController`.
- Hardened `PresenceDomainClient` so presence HTTP failures do not escape into websocket handling.
- Added explicit HTTP timeout settings for edge -> presence calls.
- Reduced log noise for repeated low-priority failures such as typing and heartbeat.
- Added an optional follow-up snapshot pass in `EdgePresenceLifecycleBridge` to reduce the connect snapshot race.
- Kept the old presence websocket path intact for rollback.

## Risks Reduced

- Edge presence command calls now have explicit timeout boundaries.
- High-frequency presence actions no longer emit repeated WARN stack traces on every transient failure.
- Connect/disconnect failures are treated as more important than typing/heartbeat failures.
- The snapshot sent on presence connect has a lightweight convergence rule instead of relying on a single read.

## Test Coverage Added

- `PresenceEdgeCommandControllerIntegrationTest` covers:
  - connect
  - disconnect
  - heartbeat
  - join room
  - leave room
  - typing
  - stop-typing
  - authenticated access
  - unauthenticated access
  - invalid JWT subject handling

## Validation

- `:presence-service:compileJava` - passed
- `:realtime-edge-service:compileJava` - passed
- `:realtime-edge-service:test` - passed
- `PresenceEdgeCommandControllerIntegrationTest` - passed

## Remaining Known Limits

- The follow-up snapshot is a lightweight convergence step, not strong distributed consistency.
- The edge client still logs failures instead of using a circuit breaker.
- Chat and friendship remain on their existing paths and are intentionally out of scope.
- The old direct presence websocket endpoint remains available for rollback and is not removed.
