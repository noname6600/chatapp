## MODIFIED Requirements

### Requirement: Scroll-to-top triggers older message fetch
The system SHALL detect when user scrolls to the top of the message list and automatically fetch older messages from the backend, with trigger and pagination orchestration owned by list layer.

#### Scenario: Scroll near top triggers fetch
- **WHEN** user scrolls to within 60px of the top of the message container
- **THEN** list-layer logic initiates an async request to fetch older messages via `getMessagesBefore(roomId, oldestSeq)`

#### Scenario: Fetch only happens once during scroll session
- **WHEN** user is scrolling near top and a fetch is already in progress
- **THEN** list-layer logic does not initiate a duplicate fetch request; only one fetch occurs until it completes

#### Scenario: Fetch stops when no more messages exist
- **WHEN** backend returns an empty message page for an older scroll request
- **THEN** list-layer logic sets `hasMore` flag to false and stops attempting further fetches for this room

#### Scenario: Older messages are prepended to the list
- **WHEN** a fetch for older messages completes successfully with N messages
- **THEN** those N messages are inserted at the beginning of the message array, before the current oldest messages

### Requirement: Scroll-load respects pagination boundaries
The system SHALL correctly maintain pagination state (`oldestSeqByRoom`) when fetching older messages, and these boundaries SHALL not be delegated to single-item components.

#### Scenario: Pagination boundary updates after fetch
- **WHEN** older message fetch completes and prepends message with seq 10
- **THEN** `oldestSeqByRoom[roomId]` is updated to 10 (the new minimum sequence number)

#### Scenario: Duplicate messages are not loaded
- **WHEN** a new fetch request includes messages already present in the message array
- **THEN** duplicate messages are filtered out before prepending, maintaining list uniqueness
