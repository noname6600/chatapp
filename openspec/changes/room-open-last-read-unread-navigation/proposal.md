## Why

Users need a reliable way to resume conversations from where they last read, especially in active rooms where messages continue arriving. Current behavior does not consistently anchor entry at the read boundary, expose unread volume clearly, or provide predictable navigation between older and newer segments.

## What Changes

- Open a room at the last-read boundary instead of always anchoring at absolute latest or oldest loaded position.
- Render an unread divider (red line) between read and unread messages when the boundary is inside the loaded window.
- Support bidirectional loading from the boundary context:
  - Scroll up to load older history.
  - Scroll down to load newer/unread messages when not yet loaded.
- Provide top-of-viewport newest-jump affordance when user is far above latest messages (for example, latest is 100+ messages away).
- Show incremental top indicator when new messages arrive while user is reading older history (for example, "1 new message") with jump action.
- Display unread count since last visit and keep it synchronized with backend state.
- Define and validate mark-as-read rules (when to mark read, idempotency, and reconciliation behavior).

## Capabilities

### New Capabilities
- `last-read-entry-navigation`: Entering a room at last-read anchor, maintaining boundary-aware viewport positioning, and enabling up/down continuation from that anchor.
- `mark-read-interaction-policy`: Rules for when the client marks messages as read (boundary crossing, jump-to-latest, explicit room focus), duplicate-call protection, and backend/frontend convergence.

### Modified Capabilities
- `unread-boundary-recovery`: Extend boundary handling to include room-open anchor behavior and boundary rendering rules for mixed loaded/unloaded windows.
- `message-unread-indicator`: Add/clarify top-of-list newest-jump and incremental new-message indicator behavior while user is reading older history.
- `jump-to-latest-action`: Clarify distance-aware affordance behavior and interaction contract when latest content is far from viewport.
- `room-unread-realtime-sync`: Ensure unread counters and new-message indicators reconcile with backend state during continuous real-time updates.

## Impact

- Frontend: room/message store logic, message list viewport orchestration, unread banner components, jump controls, and read-boundary calculations.
- Backend/API usage: stronger reliance on authoritative unread/read markers and mark-read endpoint behavior.
- Testing: unit/integration coverage for entry positioning, boundary rendering, bidirectional pagination around unread region, realtime indicator updates, and mark-read idempotency/reconciliation.
