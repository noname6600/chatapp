# Editable Draft Content

## Purpose

Users SHALL be able to modify and remove individual content items (text and media) within a message draft before sending, without discarding the entire draft or restarting composition.

## Requirements

### Requirement: Draft content modeled as ordered blocks

The draft composition system SHALL represent draft content as an ordered array of content blocks. Each block SHALL have:
- A unique identifier (block ID)
- A type (`'text'` or `'media'`)
- Type-specific content (`text: string` for text blocks, `file: File` for media blocks)

Draft blocks SHALL be rendered in the order they appear in the array.

#### Scenario: Draft with mixed text and media blocks
- **WHEN** a user adds text, then an image, then more text to a draft
- **THEN** the draft contains three blocks in order: [text block, media block, text block]

#### Scenario: Each block has unique ID
- **WHEN** a draft block is created or added
- **THEN** the block is assigned a unique, immutable identifier

### Requirement: User can edit text content in draft blocks

Users SHALL be able to click on a text block in a draft to enter edit mode, modify the text, and save changes. The text SHALL remain in the draft until explicitly sent or the draft is discarded.

Editing a text block SHALL:
- Display the current text in a textarea (multi-line support)
- Allow character-level changes and deletion
- Save changes on blur (click outside) or Enter key
- Cancel changes on Escape key (revert to pre-edit content)

#### Scenario: Click to edit text
- **WHEN** user clicks on a text block in the draft
- **THEN** the block enters edit mode showing a textarea with current content

#### Scenario: Save text edit on blur
- **WHEN** user is editing a text block and clicks outside the textarea
- **THEN** the edited text is saved to the draft block

#### Scenario: Cancel edit with Escape
- **WHEN** user is editing a text block and presses Escape
- **THEN** the edit is canceled and the text block returns to pre-edit content

#### Scenario: Multi-line text support
- **WHEN** user types or pastes multi-line text into a draft text block
- **THEN** the text block preserves newlines and renders all lines

### Requirement: User can remove individual media items from draft

Users SHALL be able to remove individual media items (images, files) from a draft without affecting text blocks or other media. Removing a media block SHALL:
- Delete the media item immediately
- Remove the corresponding block from the draft
- NOT affect surrounding text or other media blocks

#### Scenario: Remove media with delete button
- **WHEN** user clicks the delete (X) button on a media block
- **THEN** the media block is removed from the draft immediately

#### Scenario: Other blocks unaffected by media removal
- **WHEN** user removes a media block from a draft containing text before and after it
- **THEN** the two text blocks remain unchanged and adjacent in the draft

### Requirement: Draft edit state management

The system SHALL maintain edit state for drafts such that:
- Only one text block can be in edit mode at a time
- Edit state (which block is being edited, its temporary content) is isolated to the draft in the current room
- Closing a draft (or switching rooms) first saves any in-progress edits or discards them based on user intent

#### Scenario: Only one block in edit mode
- **WHEN** user clicks to edit text block A, then clicks to edit text block B without saving block A
- **THEN** block A's changes are automatically saved and block B enters edit mode

#### Scenario: Edit state persists across operations
- **WHEN** user is editing a text block and adds a media item to the draft
- **THEN** the text block remains in edit mode and the media is added without interrupting the edit

### Requirement: Draft send reassembles text and media

When sending a draft message, the system SHALL:
- Concatenate all text blocks in order with newlines between them
- Collect all media blocks and attach as message attachments
- Send the final message via the standard message API (no API changes required)

#### Scenario: Send mixed-content draft
- **WHEN** user sends a draft with [text block "Hello", media block (image), text block "World"]
- **THEN** the message is sent with text: "Hello\nWorld" and attachments: [image]

#### Scenario: Send text-only draft
- **WHEN** user sends a draft with only text blocks and no media
- **THEN** the message is sent with the concatenated text and empty attachments list

### Requirement: Draft initialization and migration

The draft system SHALL initialize and migrate draft data as follows:
- New drafts created in the updated system SHALL use the blocks array structure
- Existing drafts in old format (`{ text: string, attachments: File[] }`) SHALL be automatically migrated to blocks format on first load
- Migration SHALL NOT require user action or lose data

#### Scenario: New draft uses blocks structure
- **WHEN** a new draft is created after the feature is deployed
- **THEN** the draft is initialized with `blocks: []` and no legacy `text` or `attachments` fields

#### Scenario: Legacy draft migrates automatically
- **WHEN** an app loads with an existing draft in the old `{ text, attachments }` format
- **THEN** the draft is transparently converted to blocks format without user interaction
