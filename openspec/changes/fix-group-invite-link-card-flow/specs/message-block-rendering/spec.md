## MODIFIED Requirements

### Requirement: Message blocks with no text content are not rendered
The system SHALL NOT render text blocks that contain no meaningful content (empty string or whitespace-only) in the message display, even if those blocks exist in the underlying data structure, while still rendering non-text meaningful blocks such as ROOM_INVITE and ASSET.

#### Scenario: Empty TEXT block is skipped during render
- **WHEN** a message's blocks array contains a TEXT block with empty or whitespace-only text
- **THEN** that block is not rendered in the message display area

#### Scenario: Blocks with content are rendered normally
- **WHEN** a message's blocks array contains TEXT blocks with actual content
- **THEN** all non-empty TEXT blocks are rendered in their original order

#### Scenario: Spacing is not affected by empty blocks
- **WHEN** a message contains empty blocks interspersed with content blocks
- **THEN** rendering skips the empty blocks and other content blocks display with normal spacing, as if the empty blocks did not exist

## ADDED Requirements

### Requirement: ROOM_INVITE blocks SHALL render as actionable invite cards
ROOM_INVITE blocks SHALL render as joinable invite cards with clear call-to-action semantics and link/code-oriented room context.

#### Scenario: ROOM_INVITE block renders invite card UI
- **WHEN** a message includes a valid ROOM_INVITE block
- **THEN** renderer outputs an invite card with room context and join action

#### Scenario: Invite card supports deterministic join states
- **WHEN** invite card state is evaluated against recipient membership and join attempt outcomes
- **THEN** card renders deterministic states for joinable, joined, and unavailable outcomes

#### Scenario: Invalid ROOM_INVITE block is not rendered as joinable card
- **WHEN** ROOM_INVITE block lacks required room identity
- **THEN** renderer does not expose a clickable join action for that block