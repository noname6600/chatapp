## ADDED Requirements

### Requirement: Room opens at last-read anchor
The system SHALL position the message viewport at the first unread boundary when a room has unread messages, and SHALL open at latest when unread count is zero.

#### Scenario: Room with unread opens at first unread boundary
- **WHEN** user opens a room with unread messages from previous session
- **THEN** viewport anchors at the first unread message and keeps nearby context visible above and below the boundary

#### Scenario: Room with no unread opens at latest
- **WHEN** user opens a room with unread count equal to zero
- **THEN** viewport opens at the most recent messages without rendering unread entry anchoring

### Requirement: Boundary-centered navigation supports both directions
From the entry anchor, the system SHALL support loading older messages on upward scroll and loading newer messages on downward scroll when missing ranges exist.

#### Scenario: Scroll up loads older history from anchor context
- **WHEN** user scrolls upward near top threshold while reading around the anchor
- **THEN** older messages are fetched and prepended without losing current read position

#### Scenario: Scroll down loads newer missing range before live tail
- **WHEN** user scrolls downward and newer messages after current window are not yet loaded
- **THEN** newer messages are fetched and appended until live tail is reached

### Requirement: Distance-to-latest affordance appears when far above newest
The system SHALL show a top jump affordance when the user is far above the latest message range.

#### Scenario: User is far from latest message range
- **WHEN** user remains in older context with large gap to latest (for example, around 100 messages)
- **THEN** top indicator appears with newest-jump action and distance context
