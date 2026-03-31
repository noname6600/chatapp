# realtime-reaction-toggle-highlight Specification

## Purpose
Define reliable realtime reaction toggling with deduplication, optimistic reconciliation, and metadata-safe partial updates so clients stay consistent without duplicates.

## Requirements

### Requirement: Duplicate reactions from same user are prevented in toggle operations
The system SHALL ensure that toggle operations on reactions do not create duplicate reactions for the same (messageId, userId, emoji) tuple, by checking for existing reactions before adding and deduplicating during merge.

#### Scenario: Toggle detects existing reaction before add
- **WHEN** current user clicks emoji E on message M1 and reaction E already exists for that user in local state
- **THEN** the system removes the reaction instead of adding a duplicate

#### Scenario: Optimistic and real reaction deduplicate on merge
- **WHEN** an optimistic toggle add is followed by a real reaction event for the same tuple
- **THEN** only one reaction appears in final state (deduplication occurs during merge)

### Requirement: Toggle operations are idempotent and reliable
The system SHALL ensure that a user can toggle the same emoji reliably every time: add and remove work consistently regardless of network speed, click speed, or prior state conflicts.

#### Scenario: Repeated toggle clicks always work
- **WHEN** a user rapidly clicks emoji E on message M1 multiple times
- **THEN** each click correctly toggles the state (off→on, on→off) without skipped clicks or stuck states

#### Scenario: Toggle succeeds after network delay
- **WHEN** user toggles emoji E, waits for confirmation, then toggles again while network is slow
- **THEN** second toggle still works correctly and doesn't duplicate or lose state

### Requirement: Reactions update in realtime for all room participants
The system SHALL propagate reaction add and remove updates in realtime to all connected clients in the same room, so all users observe consistent reaction state without manual refresh. Updates MUST NOT create duplicate reactions for the same (messageId, userId, emoji) tuple.

#### Scenario: Another participant adds a reaction
- **WHEN** user B adds an emoji reaction to a message in room R
- **THEN** user A connected to room R sees the updated reaction count and emoji state immediately without duplicates

#### Scenario: Another participant removes a reaction
- **WHEN** user B removes an existing emoji reaction from a message in room R
- **THEN** user A connected to room R sees the reaction count decrease immediately and removal is final

### Requirement: Reaction toggle semantics are deterministic and reliable
The system SHALL support toggle behavior for the same emoji by the same user: first action adds reaction, second action removes it. Toggle operations MUST complete reliably every time without race conditions or duplicate reactions.

#### Scenario: User toggles same emoji on and off
- **WHEN** a user clicks emoji E on message M and then clicks emoji E again
- **THEN** reaction E is removed for that user and UI reflects off state consistently

#### Scenario: User toggles rapidly without duplicates
- **WHEN** a user clicks the same emoji multiple times quickly (e.g., 3 times)
- **THEN** final reaction state reflects correct parity (off or on) with no duplicate reactions in store

#### Scenario: Toggle completes despite slow network
- **WHEN** user toggles emoji E while network latency is high (>1s)
- **THEN** optimistic state updates immediately and final state matches server without stuck states

### Requirement: Optimistic toggle reconciles cleanly with realtime updates
The system SHALL apply optimistic toggle updates immediately while properly merging incoming realtime updates without creating duplicates or losing the user's toggle intent.

#### Scenario: Optimistic add merges with real add
- **WHEN** user optimistically adds emoji E, and realtime confirmation arrives for the same action
- **THEN** optimistic reaction is replaced by real one (same messageId, userId, emoji deduplicates to single entry)

#### Scenario: Optimistic remove persists through realtime
- **WHEN** user optimistically removes emoji E, and realtime confirmation arrives
- **THEN** reaction stays removed even if other users' reactions arrive for the same emoji

### Requirement: User-own reactions are visually highlighted
The system SHALL visually highlight reactions that include the current user so users can identify their own reaction state at a glance. Highlight MUST persist through optimistic and realtime reconciliation.

#### Scenario: Highlight own reaction
- **WHEN** current user has reacted with emoji E on message M
- **THEN** emoji E reaction chip is rendered in highlighted state for that user

#### Scenario: Remove highlight after unreact
- **WHEN** current user toggles off emoji E on message M
- **THEN** highlight is removed immediately and remains removed after realtime reconciliation

### Requirement: Reaction updates preserve other message fields
The system SHALL apply reaction updates as partial message updates keyed by messageId without replacing unrelated message fields. Deduplication MUST occur without affecting message content or metadata.

#### Scenario: Reaction event does not overwrite message metadata
- **WHEN** a reaction update event is processed for message M
- **THEN** message content, sender, createdAt, and attachments remain unchanged while only reaction-related fields are updated and deduplicated

