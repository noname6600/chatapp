## MODIFIED Requirements

### Requirement: Messages from same sender within 2-minute window SHALL be visually grouped
The system SHALL continue grouping messages by sender/time while supporting inline reply snippet rendering and linked reply-highlight states.

#### Scenario: Same sender + within 2 minutes should group
- **WHEN** consecutive messages have identical senderId and createdAt difference <= 2 minutes
- **THEN** render them as one visual group with continuous bubble stack

#### Scenario: Different sender breaks group
- **WHEN** consecutive messages have different senderId
- **THEN** start a new visual group

#### Scenario: Time gap > 2 minutes breaks group
- **WHEN** same sender but createdAt difference > 2 minutes
- **THEN** start a new visual group

#### Scenario: Inline reply snippet remains inside grouped row
- **WHEN** a grouped message is a reply message
- **THEN** reply snippet renders inline inside that message row without breaking group container structure

#### Scenario: Linked highlight does not collapse grouped spacing
- **WHEN** grouped message row participates in reply-linked highlight state
- **THEN** highlight style applies while preserving group spacing and timestamp/avatar behavior