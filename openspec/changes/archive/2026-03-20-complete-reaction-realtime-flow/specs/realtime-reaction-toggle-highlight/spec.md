## ADDED Requirements

### Requirement: Reactions update in realtime for all room participants
The system SHALL propagate reaction add and remove updates in realtime to all connected clients in the same room, so all users observe consistent reaction state without manual refresh.

#### Scenario: Another participant adds a reaction
- **WHEN** user B adds an emoji reaction to a message in room R
- **THEN** user A connected to room R sees the updated reaction count and emoji state immediately

#### Scenario: Another participant removes a reaction
- **WHEN** user B removes an existing emoji reaction from a message in room R
- **THEN** user A connected to room R sees the reaction count decrease immediately

### Requirement: Reaction toggle semantics are deterministic
The system SHALL support toggle behavior for the same emoji by the same user: first action adds reaction, second action removes it.

#### Scenario: User toggles same emoji on and off
- **WHEN** a user clicks emoji E on message M and then clicks emoji E again
- **THEN** reaction E is removed for that user and UI reflects off state

#### Scenario: User toggles rapidly
- **WHEN** a user clicks the same emoji multiple times quickly
- **THEN** final reaction state matches the parity of toggles and does not double-count reactions

### Requirement: User-own reactions are visually highlighted
The system SHALL visually highlight reactions that include the current user so users can identify their own reaction state at a glance.

#### Scenario: Highlight own reaction
- **WHEN** current user has reacted with emoji E on message M
- **THEN** emoji E reaction chip is rendered in highlighted state for that user

#### Scenario: Remove highlight after unreact
- **WHEN** current user toggles off emoji E on message M
- **THEN** highlight is removed immediately and remains removed after realtime reconciliation

### Requirement: Reaction updates preserve other message fields
The system SHALL apply reaction updates as partial message updates keyed by messageId without replacing unrelated message fields.

#### Scenario: Reaction event does not overwrite message metadata
- **WHEN** a reaction update event is processed for message M
- **THEN** message content, sender, createdAt, and attachments remain unchanged while only reaction-related fields are updated
