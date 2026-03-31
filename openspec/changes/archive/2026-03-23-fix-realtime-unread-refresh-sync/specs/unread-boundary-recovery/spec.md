## ADDED Requirements

### Requirement: Unread boundary divider is backend-driven
The message view SHALL display an unread boundary using backend unread state when unread messages exist in the loaded window.

#### Scenario: Divider appears at first unread message in loaded window
- **WHEN** unread count is greater than zero and first unread exists in current message page
- **THEN** UI renders a visible boundary divider between read and unread messages

#### Scenario: No fake divider when boundary is outside loaded window
- **WHEN** unread starts before currently loaded messages
- **THEN** UI does not render an incorrect divider position and instead uses top unread indicator behavior

### Requirement: Entry positioning adapts to unread span size
On room open, the message viewport SHALL position based on unread span relative to viewport height.

#### Scenario: Multi-page unread starts near top with banner
- **WHEN** unread span exceeds one viewport height
- **THEN** message list positions near unread boundary start and shows unread banner with jump action

#### Scenario: Single-page unread centers first unread
- **WHEN** unread span fits inside one viewport
- **THEN** first unread message is placed around middle of viewport and banner may be hidden

### Requirement: Interaction-based read acknowledgement
The system SHALL acknowledge read after meaningful user interaction with unread region.

#### Scenario: Scroll crossing unread boundary marks room read
- **WHEN** user scrolls and unread boundary enters viewed region
- **THEN** mark-read action is sent once for that room view session

#### Scenario: Jump-to-latest marks room read
- **WHEN** user activates jump-to-latest from unread indicator
- **THEN** room is marked read and unread indicators are cleared after state reconciliation
