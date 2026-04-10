## MODIFIED Requirements

### Requirement: Behind-latest indicator excludes self messages
The system SHALL exclude the current user's messages when calculating the "behind-latest" count (messages newer than last-read position), preventing false "X new messages" indicators for the user's own outgoing messages. Pending optimistic placeholders for the current user's sends MUST be excluded from authoritative behind-latest sequence math until server confirmation is correlated.

#### Scenario: User sends message while at bottom, no behind-latest spike
- **WHEN** user is at latest message position and sends a message
- **THEN** the behind-latest count remains 0; the user's sent message does not create an unread indicator

#### Scenario: User reads up, other sends message, behind-latest increments correctly
- **WHEN** user scrolls up, then another user sends a message
- **THEN** behind-latest count increments to 1 (reflecting the new message from other user)

#### Scenario: User reads up, user sends message, behind-latest unchanged
- **WHEN** user scrolls up to read old messages, then sends a reply
- **THEN** behind-latest count does not increment for the user's own message

#### Scenario: Pending self-sent image does not count as messages behind latest
- **WHEN** user sends an image while reading older context and the optimistic placeholder is still pending
- **THEN** the behind-latest indicator excludes that pending self-sent image from the displayed count
- **AND** no overflow-like or synthetic-sequence-derived count is shown
