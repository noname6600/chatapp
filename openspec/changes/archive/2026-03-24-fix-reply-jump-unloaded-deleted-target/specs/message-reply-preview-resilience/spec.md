## MODIFIED Requirements

### Requirement: Reply preview SHALL support click-to-jump with context loading
The system SHALL allow users to click the reply snippet and jump to the original message, fetching surrounding context from the backend when the original is not in the current loaded window, then performing bounded fallback history search before reporting unavailable state.

#### Scenario: Click reply snippet jumps to in-memory original
- **WHEN** user clicks the reply snippet and the original message is present in the current DOM
- **THEN** viewport scrolls to the original message and applies temporary jump-target emphasis state

#### Scenario: Click reply snippet loads context when original is outside window
- **WHEN** user clicks the reply snippet and the original message is NOT present in the current DOM
- **THEN** the system calls `getMessagesAround(roomId, messageId)` to fetch surrounding messages
- **AND** the room's message window is replaced with the returned context
- **AND** viewport scrolls to the original message and applies temporary jump-target emphasis state

#### Scenario: Click reply snippet retries with bounded older-history backfill
- **WHEN** user clicks the reply snippet and `getMessagesAround` does not return the target message
- **THEN** the system performs bounded older-history backfill attempts
- **AND** each attempt re-checks for target message id before continuing
- **AND** search terminates early if `hasOlder=false`

#### Scenario: Click reply snippet when original permanently unavailable
- **WHEN** user clicks the reply snippet and all lookup paths are exhausted without target resolution
- **THEN** the UI does not crash and the reply snippet shows an unavailable/deleted fallback state
- **AND** no jump is performed and viewport position is preserved
- **AND** the system stops issuing additional fetches for that click action

#### Scenario: Post-jump context supports continuation scroll
- **WHEN** user clicks a reply snippet to an unloaded target and resolver successfully lands on target context
- **THEN** user can continue scrolling upward to load older messages
- **AND** user can continue scrolling downward to load newer messages toward latest
