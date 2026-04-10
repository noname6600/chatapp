## MODIFIED Requirements

### Requirement: Unread boundary divider is backend-driven
The message view SHALL display an unread boundary using backend unread state when unread messages exist in the loaded window, and SHALL render the divider as a clear red separator between read and unread regions.

#### Scenario: Divider appears at first unread message in loaded window
- **WHEN** unread count is greater than zero and first unread exists in current message page
- **THEN** UI renders a visible red boundary divider between read and unread messages

#### Scenario: No fake divider when boundary is outside loaded window
- **WHEN** unread starts before currently loaded messages
- **THEN** UI does not render an incorrect divider position and instead uses top unread indicator behavior

### Requirement: Entry positioning adapts to unread span size
On room open, the message viewport SHALL position from the backend-derived unread boundary and adapt based on unread span relative to viewport height.

#### Scenario: Multi-page unread starts near top with banner
- **WHEN** unread span exceeds one viewport height
- **THEN** message list positions near unread boundary start and shows unread banner with jump action

#### Scenario: Single-page unread centers first unread
- **WHEN** unread span fits inside one viewport
- **THEN** first unread message is placed around middle of viewport and banner may be hidden
