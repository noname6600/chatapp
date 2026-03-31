# message-unread-indicator Specification

## Purpose
Define unread-message banner behavior for room message views, including visibility rules, live count updates, accessibility, and persistence across refresh.
## Requirements
### Requirement: Display unread message count banner
The system SHALL display a sticky banner above the message list showing the number of unread messages in the current room when unread state indicates unread messages, and SHALL keep the count consistent with last-read sequence and latest sequence.

#### Scenario: Banner shown on room entry with unread messages
- **WHEN** user enters a room that has `unreadCount > 0`
- **THEN** a sticky banner appears above the message list displaying "N unread messages" where N is the unread count

#### Scenario: Banner hidden when no unread messages
- **WHEN** user enters a room that has `unreadCount === 0`
- **THEN** the banner does not appear

#### Scenario: Banner updates when new messages arrive
- **WHEN** a new message arrives via WebSocket while banner is visible
- **THEN** the unread count increments by 1 and banner text updates immediately

#### Scenario: Banner auto-hides when all messages read
- **WHEN** `unreadCount` transitions to 0 (e.g., after `markRoomRead` API call completes)
- **THEN** the banner fades or immediately disappears

#### Scenario: Behind-latest display uses valid bounded values
- **WHEN** newest-jump indicator is displayed
- **THEN** the behind-latest count is clamped to valid non-negative sequence-derived values and MUST NOT display overflow-like numbers

### Requirement: Banner styling and accessibility
The banner SHALL be visually distinct from the message list and accessible to keyboard and screen reader users.

#### Scenario: Banner has high contrast and distinguishable color
- **WHEN** banner is displayed
- **THEN** it uses a distinct background color (e.g., light blue or gray) that contrasts with the message list background

#### Scenario: Banner is keyboard focusable
- **WHEN** user navigates with Tab key through the message area
- **THEN** the "Jump to Latest" button inside the banner receives focus and is operable via Enter key

#### Scenario: Banner has descriptive ARIA labels
- **WHEN** screen reader user encounters the banner
- **THEN** it announces "Unread messages" label and the count

### Requirement: Unread count persists across page reloads
The unread count displayed in the banner SHALL be consistent with the backend state even after page reload.

#### Scenario: Unread count preserved after refresh
- **WHEN** user has unread messages, page is reloaded
- **THEN** `lastReadSeq` is fetched from backend via room API, unreadCount is recalculated, and banner shows the correct count

