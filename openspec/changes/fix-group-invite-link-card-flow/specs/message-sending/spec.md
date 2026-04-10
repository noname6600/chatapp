## MODIFIED Requirements

### Requirement: Message send supports ordered mixed-content payloads
The system SHALL accept an ordered message-body payload so one sent message can preserve interleaved text, media, and invite-card content blocks, including ROOM_INVITE-only payloads when invite metadata is valid.

#### Scenario: Mixed-content payload is submitted in authored order
- **WHEN** user sends a draft containing text blocks and uploaded image blocks
- **THEN** send payload preserves the authored sequence of those blocks instead of flattening media into an unordered attachment list

#### Scenario: Existing text-only sends remain valid
- **WHEN** user sends a plain text message without media blocks
- **THEN** the existing message send behavior remains valid without requiring embed or asset blocks

#### Scenario: ROOM_INVITE-only payload is accepted as valid content
- **WHEN** outgoing payload contains a ROOM_INVITE block with required invite metadata and no text or attachment blocks
- **THEN** validation accepts the message as non-empty content
- **AND** message send proceeds without `Message must contain content or attachment` failure

## ADDED Requirements

### Requirement: Invite-card send validation SHALL enforce ROOM_INVITE shape rules
The system SHALL validate ROOM_INVITE blocks as first-class message content and MUST require invite identity fields necessary for join semantics.

#### Scenario: Missing room identity in ROOM_INVITE is rejected
- **WHEN** outgoing ROOM_INVITE block omits required room identity field(s)
- **THEN** send validation fails with explicit validation error

#### Scenario: Valid ROOM_INVITE metadata passes without fallback content
- **WHEN** outgoing ROOM_INVITE block includes required room identity and optional snapshot metadata
- **THEN** send validation passes even when text and attachments are absent