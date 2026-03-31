# message-draft-review Specification

## Purpose
TBD - created by archiving change improve-draft-review-and-message-edit. Update Purpose after archive.
## Requirements
### Requirement: User can open draft message review before sending
The system SHALL provide a mechanism for users to open a draft message review interface that displays the currently composed message text without immediately sending it.

#### Scenario: Review button triggers draft review modal
- **WHEN** user clicks a "Review" button in the message composer
- **THEN** a modal/panel opens displaying the composed message content

#### Scenario: Keyboard shortcut can open draft review
- **WHEN** user presses a designated keyboard shortcut (e.g., Ctrl+Enter or Cmd+Enter) while composing
- **THEN** the draft review interface opens (if configured) or message sends directly (if shortcut is send)

#### Scenario: Draft review displays full message context
- **WHEN** draft review modal is open
- **THEN** the message content is fully visible with adequate reading space and no text truncation

### Requirement: Draft message text can be edited in review state
The system SHALL allow users to modify the message text within the draft review interface before confirming send.

#### Scenario: Edit control in review modal allows text modification
- **WHEN** user clicks an "Edit" button or text area within the draft review modal
- **THEN** the text becomes editable and user can modify content

#### Scenario: Edited draft text is preserved when confirming send
- **WHEN** user edits message text in draft review and clicks "Send"
- **THEN** the modified text content is sent to the server (not the original pre-review text)

#### Scenario: Discard option returns to composer
- **WHEN** user clicks "Cancel" or "Discard" in draft review modal
- **THEN** the modal closes and the original composed text remains in the composer (if desired) or is cleared

### Requirement: Draft review state SHALL NOT require additional send configuration
The system SHALL allow draft review without requiring users to re-select reply targets, attachments, or other message metadata.

#### Scenario: Reply context persists through draft review
- **WHEN** user has selected a reply target, opens draft review, and confirms send
- **THEN** the send request includes the original reply target metadata

#### Scenario: Attachments are preserved in draft review flow
- **WHEN** user has attached files, opens draft review with those attachments visible, and sends
- **THEN** all attached files are included in the message send request

#### Scenario: Mentions are preserved in draft review
- **WHEN** user has inserted message mentions, opens draft review, and sends
- **THEN** the mention tokens are sent as-is in the message content

