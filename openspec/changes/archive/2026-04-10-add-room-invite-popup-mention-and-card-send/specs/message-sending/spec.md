## ADDED Requirements

### Requirement: Message sending SHALL accept room invite card payloads
The system SHALL accept a room invite card message payload in the standard message send flow and persist enough room context for recipients to join from the rendered card.

#### Scenario: Invite card payload is submitted
- **WHEN** the client submits a message send request with invite-card payload type for a room
- **THEN** chat-service validates the payload as invite-card content
- **AND** the message is persisted and delivered using existing message dispatch behavior

### Requirement: Invite card payload SHALL preserve room join context
Invite-card message content SHALL include canonical room identifiers needed for join action handling, including roomId and join code or equivalent join token.

#### Scenario: Invite card rendered by recipient
- **WHEN** a recipient loads or receives an invite card message
- **THEN** the rendered card contains room context needed to trigger join flow
- **AND** join action uses the same authorization and room-join rules as other join entrypoints

### Requirement: Invalid invite card payload SHALL fail with message validation error
The system SHALL reject invite-card message sends when required room context fields are missing or malformed.

#### Scenario: Missing room context in invite card payload
- **WHEN** a message send request includes invite-card type but omits required room join context
- **THEN** the send request fails with existing bad-request validation semantics
- **AND** no invite card message is persisted
