## MODIFIED Requirements

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
