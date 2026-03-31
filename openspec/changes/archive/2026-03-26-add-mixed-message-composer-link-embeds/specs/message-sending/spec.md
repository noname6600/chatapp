## MODIFIED Requirements

### Requirement: Message send supports ordered mixed-content payloads
The system SHALL accept an ordered message-body payload so one sent message can preserve interleaved text, media, and embed content.

#### Scenario: Mixed-content payload is submitted in authored order
- **WHEN** user sends a draft containing text blocks and uploaded image blocks
- **THEN** send payload preserves the authored sequence of those blocks instead of flattening media into an unordered attachment list

#### Scenario: Existing text-only sends remain valid
- **WHEN** user sends a plain text message without media blocks
- **THEN** the existing message send behavior remains valid without requiring embed or asset blocks

### Requirement: Message service derives compatibility fields from ordered content
The system SHALL derive compatibility-oriented summaries from ordered message blocks so legacy views can continue working during migration.

#### Scenario: Mixed-content message derives preview summary
- **WHEN** a mixed-content message is persisted
- **THEN** service derives a concise preview string that summarizes ordered blocks for room list and notification usage

#### Scenario: Asset references remain available to legacy consumers
- **WHEN** ordered content includes uploaded asset blocks
- **THEN** normalized asset metadata remains available in compatibility fields needed by existing consumers during migration

### Requirement: Plain links remain safe and clickable in mixed-content messages
The system SHALL preserve URLs inside text blocks as plain clickable links without requiring provider-specific embed rendering.

#### Scenario: URL in text block stays clickable
- **WHEN** a text block contains a valid URL
- **THEN** message rendering preserves the authored text and exposes the URL as a clickable link