# inline-edit-timestamp-display Specification

## Purpose
Defines how the "edited at" timestamp is displayed based on the scope of the edit (single-line vs. multi-block), ensuring clear visual hierarchy and consistent behavior across all edited messages.

## ADDED Requirements

### Requirement: Single-text-block edits display timestamp inline
When a message with a single TEXT block is edited, the "edited at" timestamp SHALL appear inline (on the same line) after the text content, not on a new line.

#### Scenario: Edited single-line text message shows inline timestamp
- **WHEN** a message containing only one TEXT block is edited

- **THEN** the "edited <timestamp>" label appears as an inline element immediately after the text content, and a newline is NOT inserted before the label

#### Scenario: Inline timestamp uses standard time formatting
- **WHEN** a single TEXT block message is edited
- **THEN** the timestamp uses the same formatting as normal message timestamps (e.g., "edited 2:30 PM")

### Requirement: Multi-block edits display timestamp on new line
When a message with multiple blocks (TEXT + ASSET or multiple TEXT blocks) is edited, the "edited at" timestamp SHALL appear on a separate line below the content.

#### Scenario: Edited multi-block message shows timestamp on new line
- **WHEN** a message containing multiple blocks (e.g., text + image + text) is edited
- **THEN** the "edited <timestamp>" label appears on a new line after all blocks, with standard block spacing

#### Scenario: Edited message with only ASSET blocks shows timestamp on new line
- **WHEN** a message contains only attachment/media blocks and is edited
- **THEN** the "edited <timestamp>" label appears on a new line after the attachments

### Requirement: Edit detection uses block count, not message type
The system SHALL determine timestamp placement based on the count of non-empty message blocks (blocks.length), not the message type field (TEXT/MIXED/ATTACHMENT).

#### Scenario: Pure TEXT message with multiple blocks uses newline timestamp
- **WHEN** a message with type=TEXT but multiple TEXT blocks (e.g., edited to add content) is displayed
- **THEN** the "edited" timestamp appears on a new line because blocks.length > 1

#### Scenario: Single block in MIXED-type message uses inline timestamp
- **WHEN** a MIXED-type message is edited down to a single remaining block
- **THEN** the "edited" timestamp appears inline because only one block remains after edit
