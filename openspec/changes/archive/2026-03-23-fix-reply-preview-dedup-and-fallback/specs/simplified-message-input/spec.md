## ADDED Requirements

### Requirement: Composer reply preview SHALL render identity and original-message state
The message composer SHALL show reply preview with sender identity and original-message state specific rendering.

#### Scenario: Reply preview includes name and icon
- **WHEN** user selects Reply from a message item
- **THEN** composer reply preview shows original sender name and sender icon/avatar

#### Scenario: Reply preview uses content-specific rendering
- **WHEN** original replied message is text or media
- **THEN** composer preview renders text snippet for text messages and media label/preview for media messages

#### Scenario: Missing original uses fallback text
- **WHEN** original replied message is deleted or unavailable
- **THEN** composer preview shows "cannot load the original message"

## MODIFIED Requirements

### Requirement: User can send message with Enter key
The system SHALL send one message per intentional Enter submit and SHALL prevent duplicate sends while the same submit is already processing.

#### Scenario: Pressing Enter sends message once
- **WHEN** user types text and presses Enter (no Alt/Ctrl/Shift)
- **THEN** one message is sent and input field is cleared after successful submit

#### Scenario: Repeated Enter during in-flight submit does not duplicate
- **WHEN** user presses Enter repeatedly before previous submit completes
- **THEN** duplicate send requests are not emitted for the same draft

#### Scenario: Empty message on Enter is not sent
- **WHEN** user presses Enter with empty input field
- **THEN** nothing happens, input remains focused and ready for text