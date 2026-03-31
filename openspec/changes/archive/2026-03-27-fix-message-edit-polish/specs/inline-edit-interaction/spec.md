## ADDED Requirements

### Requirement: Edited message content SHALL render at the same line height as non-edited messages
The system SHALL NOT apply any additional padding, margin, or background wrapper to the content area of an edited message that would alter the vertical height or text baseline relative to non-edited messages in the same list.

#### Scenario: Edited text message has same row height as non-edited text message
- **WHEN** a text message has been edited and its `editedAt` field is set
- **THEN** the message row renders at the same height as a non-edited message with the same content length, with no extra background block or padding wrapper applied to the content

#### Scenario: Edited badge is inline with the message text
- **WHEN** a message has `editedAt` set
- **THEN** the "edited \<timestamp\>" indicator appears as an inline element after the last word of the message content, not as a separate block-level element that shifts the layout

#### Scenario: Structured-block edited message also renders without wrapper padding
- **WHEN** an edited message has structured blocks (e.g., attachment + text)
- **THEN** the blocks container has no background padding wrapper applied due to the `editedAt` field; the "edited" badge is rendered inline within or after the blocks

### Requirement: Scroll position SHALL remain stable after confirming an inline edit
The system SHALL NOT trigger a bulk message window refresh or any operation that resets the chat container's scroll position as a side-effect of a successful inline edit submission.

#### Scenario: Scroll position unchanged after edit confirm
- **WHEN** a user submits an inline message edit via Enter or the Save button
- **THEN** the chat scroll container remains at the same scroll position after the edit is committed, with the edited message updated in place

#### Scenario: No loadMessagesAround call on successful edit
- **WHEN** the edit API responds successfully
- **THEN** the updated message state is applied locally via upsert without triggering a loadMessagesAround re-hydration

### Requirement: Action bar buttons SHALL be disabled while a message is in inline-edit mode
The system SHALL disable the Reply, Emoji/Reaction, and Delete action buttons on a message while that message is actively being edited inline, preventing conflicting operations.

#### Scenario: Reply button is disabled during editing
- **WHEN** a message is in inline-edit mode (edit textarea is open)
- **THEN** the Reply button in the action bar is disabled and non-interactive

#### Scenario: Emoji/Reaction picker is disabled during editing
- **WHEN** a message is in inline-edit mode
- **THEN** the Emoji/Reaction picker trigger is disabled and cannot be opened

#### Scenario: Delete button is disabled during editing
- **WHEN** a message is in inline-edit mode
- **THEN** the Delete button in the action bar is disabled and non-interactive

#### Scenario: All actions re-enable after edit is cancelled or saved
- **WHEN** the user confirms or cancels the inline edit
- **THEN** all action bar buttons return to their normal enabled state
