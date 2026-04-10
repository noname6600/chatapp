## ADDED Requirements

### Requirement: Room member sidebar toggle is an icon button accessible from the chat view
The room member sidebar SHALL provide a dedicated icon button (not a plain text button) to collapse and expand the panel. The icon SHALL visually indicate the current panel state (open vs. closed) and SHALL be positioned in a stable, non-overlapping location relative to the chat content area.

#### Scenario: Icon button collapses the member panel
- **WHEN** the room member panel is visible and the user clicks the toggle icon
- **THEN** the member panel SHALL collapse and the icon SHALL update to the "open" variant

#### Scenario: Icon button expands the member panel
- **WHEN** the room member panel is collapsed and the user clicks the toggle icon
- **THEN** the member panel SHALL expand and the icon SHALL update to the "close/hide" variant

#### Scenario: Toggle icon does not overlap message content
- **WHEN** the member panel is in any state (open or closed)
- **THEN** the toggle icon SHALL be positioned in the chat header or sidebar edge without obscuring the message list or input area

### Requirement: Room member sidebar rows display presence via dot indicator only
Member rows in the room member sidebar SHALL display presence status using a colored dot indicator only. The raw status text label (e.g., "ONLINE", "AWAY", "OFFLINE") SHALL NOT be rendered as visible inline text.

#### Scenario: Member row shows dot without text label
- **WHEN** the member panel renders a member with an ONLINE, AWAY, or OFFLINE status
- **THEN** the row SHALL display only the colored presence dot for status, with no inline text label next to the dot
