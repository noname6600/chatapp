# room-switch-resilient-history-pagination Specification

## Purpose
Defines resilient older-message pagination behavior so rooms continue loading history when overflow is insufficient and after room transitions.

## Requirements

### Requirement: Pagination SHALL auto-prefetch when no overflow prevents scrolling
The chat client SHALL request older messages automatically when the message container does not overflow and cannot produce an upward scroll event.

#### Scenario: Initial history exactly fills viewport
- **WHEN** initial room messages render and the container scrollHeight is less than or equal to clientHeight
- **THEN** the client requests an older history page without waiting for user scroll

#### Scenario: Auto-prefetch stops when no further progress is possible
- **WHEN** repeated no-overflow fetch attempts produce no message growth or backend reports no additional history
- **THEN** the client stops auto-prefetch attempts to avoid infinite fetch loops

### Requirement: Room transitions SHALL preserve future pagination eligibility
Switching to a different room and returning SHALL not leave stale local guard state that prevents valid older-message requests, and active-room initialization SHALL remain idempotent under live message churn.

#### Scenario: Return to previously visited room
- **WHEN** user leaves room A, visits room B, then returns to room A
- **THEN** room A can continue loading older history when near-top or no-overflow conditions are satisfied

#### Scenario: Independent room pagination state
- **WHEN** one room reaches terminal no-more-history state
- **THEN** other rooms remain unaffected and continue using their own pagination eligibility

#### Scenario: Stable room activation under incoming events
- **WHEN** incoming messages update room state while the active room remains unchanged
- **THEN** room initialization effect does not restart in a way that drops websocket or hydration reconciliation coverage
