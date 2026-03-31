# message-block-rendering Specification

## Purpose
TBD - created by archiving change edit-message-inline-timestamps. Update Purpose after archive.
## Requirements
### Requirement: Message blocks with no text content are not rendered
The system SHALL NOT render text blocks that contain no meaningful content (empty string or whitespace-only) in the message display, even if those blocks exist in the underlying data structure.

#### Scenario: Empty TEXT block is skipped during render
- **WHEN** a message's blocks array contains a TEXT block with empty or whitespace-only text
- **THEN** that block is not rendered in the message display area

#### Scenario: Blocks with content are rendered normally
- **WHEN** a message's blocks array contains TEXT blocks with actual content
- **THEN** all non-empty TEXT blocks are rendered in their original order

#### Scenario: Spacing is not affected by empty blocks
- **WHEN** a message contains empty blocks interspersed with content blocks
- **THEN** rendering skips the empty blocks and other content blocks display with normal spacing, as if the empty blocks did not exist

### Requirement: ASSET blocks render in original order with proper spacing
Media/attachment blocks (ASSET type) SHALL render in their original position within the message block sequence and display with consistent spacing relative to adjacent blocks.

#### Scenario: Attachment renders between text blocks in correct order
- **WHEN** a message has blocks [TEXT, ASSET, TEXT] and is displayed
- **THEN** the two text sections are separated by the attachment, maintaining the original block sequence

#### Scenario: Multiple attachments render with consistent spacing
- **WHEN** a message contains multiple ASSET blocks
- **THEN** each attachment is rendered with consistent spacing (margins/gaps) between them and adjacent blocks

