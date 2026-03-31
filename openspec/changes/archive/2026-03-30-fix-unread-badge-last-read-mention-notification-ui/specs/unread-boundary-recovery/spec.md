## MODIFIED Requirements

### Requirement: Unread boundary divider is backend-driven
The message view SHALL display an unread boundary using backend unread state and lastReadSeq when unread messages exist in the loaded window.

#### Scenario: Divider appears at first unread message in loaded window
- **WHEN** unread count is greater than zero and first unread exists in current message page
- **THEN** UI renders a visible boundary divider between read and unread messages

#### Scenario: No fake divider when boundary is outside loaded window
- **WHEN** unread starts before currently loaded messages
- **THEN** UI does not render an incorrect divider position and instead uses top unread indicator behavior

#### Scenario: Boundary recovery after pagination preserves last-read consistency
- **WHEN** older or newer message windows are loaded around the current viewport
- **THEN** unread boundary position is recomputed from backend lastReadSeq and remains stable without duplicating or skipping unread markers
