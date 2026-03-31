## MODIFIED Requirements

### Requirement: Display unread message count banner
The system SHALL display a sticky banner above the message list showing unread count since the user's last read state in the current room when `unreadCount > 0`.

#### Scenario: Banner shown on room entry with unread messages
- **WHEN** user enters a room that has `unreadCount > 0`
- **THEN** a sticky banner appears above the message list displaying unread count since last read

#### Scenario: Banner hidden when no unread messages
- **WHEN** user enters a room that has `unreadCount === 0`
- **THEN** the banner does not appear

#### Scenario: Banner updates when new messages arrive
- **WHEN** a new message arrives via WebSocket while banner is visible
- **THEN** unread count increments and banner text updates immediately

#### Scenario: Banner auto-hides when all messages read
- **WHEN** `unreadCount` transitions to 0 (for example after mark-read reconciliation)
- **THEN** the banner fades or immediately disappears

### Requirement: Banner styling and accessibility
The banner SHALL be visually distinct from the message list and accessible to keyboard and screen reader users.

#### Scenario: Banner has high contrast and distinguishable color
- **WHEN** banner is displayed
- **THEN** it uses a distinct background color that contrasts with the message list background

#### Scenario: Banner is keyboard focusable
- **WHEN** user navigates with Tab key through the message area
- **THEN** the jump action inside the banner receives focus and is operable via keyboard

#### Scenario: Banner has descriptive ARIA labels
- **WHEN** screen reader user encounters the banner
- **THEN** it announces unread-message label text and current count

### Requirement: New incoming indicator appears while viewing older context
The system SHALL show incremental top indicator text when new messages arrive and user is not at live tail.

#### Scenario: Incremental indicator shows new arrivals
- **WHEN** user is reading older messages and one new message arrives
- **THEN** top indicator displays "1 new message" with jump action to newest

#### Scenario: Incremental indicator accumulates burst arrivals
- **WHEN** multiple new messages arrive while user remains away from latest
- **THEN** indicator count increments to reflect unseen incoming messages
