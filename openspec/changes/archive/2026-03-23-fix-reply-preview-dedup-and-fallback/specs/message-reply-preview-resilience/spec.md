## ADDED Requirements

### Requirement: Reply snippet SHALL render as a block above message content
The system SHALL render the reply snippet as a separate block element placed above the message text/content, not inline within the text flow.

#### Scenario: Block layout in ungrouped message
- **WHEN** a reply message is rendered in the timeline
- **THEN** the reply snippet appears above the message content as a distinct visual block (not inline in the text stream)

#### Scenario: Block layout in grouped message
- **WHEN** a reply message is part of a grouped message row
- **THEN** the reply snippet still renders above the message content without disrupting grouped row spacing

### Requirement: Reply snippet SHALL NOT display a border or ring
The system SHALL render the reply preview widget without a border or ring style.

#### Scenario: No border on rendered reply snippet
- **WHEN** a reply message renders its inline reply snippet
- **THEN** the rendered snippet element has no border or ring class applied

### Requirement: Reply snippet SHALL display a clear "Replying to [name]" label
The system SHALL include an explicit "Replying to [name]" label (or equivalent with icon) in the reply snippet so the reply relationship is immediately readable.

#### Scenario: Label shows sender name
- **WHEN** a reply message is rendered and the original sender is known
- **THEN** the reply snippet displays "Replying to [displayName]" or "↩ [displayName]"

#### Scenario: Label shows fallback when sender unknown
- **WHEN** a reply message is rendered and the original sender name cannot be resolved
- **THEN** the reply snippet still renders a label using a fallback name (e.g., "Unknown")

### Requirement: Only the reply message row SHALL be highlighted
The system SHALL apply a highlight state only to the reply message row, not to the original (replied-to) message row.

#### Scenario: Reply message row highlighted when it references current user's message
- **WHEN** message B replies to message A and message A's sender is the current user
- **THEN** message B's row renders with highlight state
- **AND** message A's row does NOT render with any linked highlight state

#### Scenario: No highlight for non-current-user original
- **WHEN** message B replies to message A and message A's sender is NOT the current user
- **THEN** neither row renders with highlight state

### Requirement: Reply preview SHALL support click-to-jump with context loading
The system SHALL allow users to click the reply snippet and jump to the original message, fetching surrounding context from the backend when the original is not in the current loaded window.

#### Scenario: Click reply snippet jumps to in-memory original
- **WHEN** user clicks the reply snippet and the original message is present in the current DOM
- **THEN** viewport scrolls to the original message and applies temporary jump-target emphasis state

#### Scenario: Click reply snippet loads context when original is outside window
- **WHEN** user clicks the reply snippet and the original message is NOT present in the current DOM
- **THEN** the system calls `getMessagesAround(roomId, messageId)` to fetch surrounding messages
- **AND** the room's message window is replaced with the returned context
- **AND** viewport scrolls to the original message and applies temporary jump-target emphasis state

#### Scenario: Click reply snippet when original permanently unavailable
- **WHEN** user clicks the reply snippet and `getMessagesAround` returns an error or empty result
- **THEN** the UI does not crash and the reply snippet keeps its fallback state text "cannot load the original message"

### Requirement: Reply preview SHALL include sender identity and content/media fallback
The system SHALL render sender identity in the reply snippet and provide content-aware preview.

#### Scenario: Text original preview
- **WHEN** reply target original message has text content
- **THEN** reply snippet shows "Replying to [name]" label and text snippet (truncated)

#### Scenario: Media original preview
- **WHEN** reply target original message is media-first
- **THEN** reply snippet shows "Replying to [name]" label and media label/preview text