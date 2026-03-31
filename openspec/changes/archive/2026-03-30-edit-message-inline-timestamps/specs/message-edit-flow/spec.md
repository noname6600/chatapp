# message-edit-flow Specification

## Purpose
Defines the overall message editing flow, including how edits are initiated, validated, saved, and how the edited message updates are reflected and displayed to users.

## ADDED Requirements

### Requirement: Edit initiation captures current message block structure
When a user clicks the edit button on a message, the system SHALL load the current block structure (if blocks exist) into the edit UI, allowing the user to see exactly what will be edited.

#### Scenario: Multi-block message opens with all blocks visible for editing
- **WHEN** a user clicks edit on a message with multiple blocks (text + images)
- **THEN** the BlockMessageEditor opens and displays all blocks in their current order and structure

#### Scenario: Single-text message opens in text-only edit mode
- **WHEN** a user clicks edit on a message with a single TEXT block
- **THEN** the InlineEditInput textarea opens with the current text content, ready for editing

#### Scenario: Block structure is preserved for reference
- **WHEN** editing begins on a multi-block message
- **THEN** the BlockMessageEditor shows a summary of block types (e.g., "Editing: 3 text, 2 media") for user context

### Requirement: Edit submission validates content and saves blocks
When a user submits an edit (via Save button or keyboard shortcut), the system SHALL validate that at least one block contains content and send both the reconstructed content string and the block structure to the backend.

#### Scenario: Edit validation requires non-empty content
- **WHEN** user attempts to save an edit with all blocks empty
- **THEN** the save action is rejected and an error message appears (e.g., "Content cannot be empty")

#### Scenario: Multi-block edit sends block structure to API
- **WHEN** a user edits a multi-block message and clicks Save
- **THEN** the editMessageApi is called with both content (string) and blocks (array) parameters

#### Scenario: Single-text edit sends text content
- **WHEN** a user edits a single-text message and confirms the edit
- **THEN** the editMessageApi is called with the updated text content

### Requirement: Edited message updates are applied optimistically
When the user submits an edit, the system SHALL update the message in the UI immediately (optimistic update) rather than waiting for server confirmation, using the edited content and block structure.

#### Scenario: Optimistic update shows edited message immediately
- **WHEN** user submits an edit and the API call is in flight
- **THEN** the UI updates the message display with the new content/blocks and "edited <timestamp>" indicator immediately

#### Scenario: Failed edit restores previous message state
- **WHEN** the edit API call fails
- **THEN** the previously displayed message is restored and an error message appears to the user

### Requirement: Edit mode exit restores normal message state
Breaking out of edit mode (via Cancel, Escape, or successful save) SHALL exit edit UI and return the message to displaymode with actionbar buttons re-enabled.

#### Scenario: Cancel edit restores message display
- **WHEN** user presses Escape or clicks Cancel while editing
- **THEN** the message returns to display mode and editing UI is closed without saving changes

#### Scenario: Save edit returns to display mode
- **WHEN** the user confirms an edit (Save or Enter key)
- **THEN** the edit UI closes and the message is shown in display mode with the updated content
