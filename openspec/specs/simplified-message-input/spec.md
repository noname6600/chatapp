# simplified-message-input Specification

## Purpose
TBD - created by archiving change simplify-message-input. Update Purpose after archive.
## Requirements
### Requirement: Message input component has no text formatting toolbar
The system SHALL not display bold, italic, strikethrough, or other inline text formatting buttons in the message input UI.

#### Scenario: Input rendered without formatting buttons
- **WHEN** MessageInput component is rendered in the chat view
- **THEN** no formatting toolbar is visible and no formatting-related CSS classes are applied

#### Scenario: Old formatting buttons are removed
- **WHEN** component mounts
- **THEN** any previously existing bold/italic/strikethrough button DOM elements are not present

### Requirement: User can send message with Enter key
The system SHALL send a message when the user presses Enter in the message input field (without modifier keys), and SHALL prevent duplicate sends while the same submit is already processing.

#### Scenario: Pressing Enter sends message
- **WHEN** user types text and presses Enter (no Alt/Ctrl/Shift)
- **THEN** message is sent immediately and input field is cleared

#### Scenario: Repeated Enter during in-flight submit does not duplicate
- **WHEN** user presses Enter repeatedly before previous submit completes
- **THEN** duplicate send requests are not emitted for the same draft

#### Scenario: Empty message on Enter is not sent
- **WHEN** user presses Enter with empty input field
- **THEN** nothing happens, input remains focused and ready for text

### Requirement: Message input displays reply context when composing a reply
The system SHALL show a compact reply preview above the message input field when the user is composing a reply to an existing message.

#### Scenario: Reply preview appears above input
- **WHEN** user clicks reply on a message
- **THEN** a reply preview widget appears above the message input showing: (1) left-accent bar, (2) sender name, (3) preview snippet of original message up to 50 characters

#### Scenario: Reply context propagates in send flow
- **WHEN** user composes reply and presses Enter to send
- **THEN** sent message is tagged with `replyToMessageId` referencing the replied-to message ID

#### Scenario: User can dismiss reply context
- **WHEN** reply preview is visible and user clicks the close/X button on preview widget
- **THEN** reply context is cleared, `replyToMessageId` is nullified, input remains focused for new message


The system SHALL insert a newline into the message input when user presses Alt+Enter (or Ctrl+Enter on Windows).

#### Scenario: Alt+Enter creates new line
- **WHEN** user presses Alt and Enter simultaneously in message input
- **THEN** a newline character is inserted at cursor position, text wraps to next line, input height may expand

#### Scenario: Ctrl+Enter alternative for Windows users
- **WHEN** user presses Ctrl and Enter on Windows/Linux system
- **THEN** behavior is identical to Alt+Enter (newline inserted)

### Requirement: User can drag-drop files/images into message input
The system SHALL accept dropped files and images when user drags them from outside the application into the message input area.

#### Scenario: Visual feedback on drag-over
- **WHEN** user drags file over message input area
- **THEN** input area shows visual indicator (border highlight, background color change) to signal drop zone

#### Scenario: Successful file drop triggers upload
- **WHEN** user drops image or file onto message input area
- **THEN** file is automatically queued for upload using existing attachment upload mechanism

#### Scenario: Multiple files in single drop
- **WHEN** user drops multiple files at once
- **THEN** all files are queued for upload (multiple attachments)

#### Scenario: Unsupported file types are rejected
- **WHEN** user drops unsupported file type (e.g., .exe, .zip)
- **THEN** file is rejected with user warning, only supported types (images, documents) are queued

