## Why

Room join code behavior appears wrong in the frontend: user-entered case can be altered before submit, and a code fetched for one room can be shown in other rooms due to shared FE state. This causes failed joins and confusing room-code display, even when backend behavior is unchanged.

## What Changes

- Preserve exact user-entered join code string in frontend submit flow (no forced upper/lower casing).
- Isolate room-code display state by roomId so code from room A cannot appear in room B UI.
- Guard against stale async responses so late code-fetch responses cannot overwrite currently selected room code.
- Add frontend regression tests for case preservation and per-room code isolation.

## Capabilities

### New Capabilities
- `room-join-code-integrity`: Defines frontend room-code input and display integrity (case preservation and room-scoped state isolation).

### Modified Capabilities
- None.

## Impact

- Frontend room join form/input handling in chatappFE.
- Frontend room code retrieval/display state management in chatappFE stores/hooks/components.
- Frontend test coverage for join-code payload integrity and multi-room state isolation.
- No backend API, persistence, or service logic changes.
