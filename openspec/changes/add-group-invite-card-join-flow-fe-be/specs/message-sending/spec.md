## ADDED Requirements

### Requirement: Message send supports group invite card payloads
The system SHALL accept and transmit a structured `ROOM_INVITE` message block in the message send flow while preserving existing text and mixed-content behavior.

#### Scenario: Invite card send includes required room metadata
- **WHEN** sender dispatches an invite-card message
- **THEN** outgoing payload includes invite block type and target room identity metadata required for rendering and join action

#### Scenario: Invite card send remains compatible with existing send path
- **WHEN** invite-card message is processed by the existing send pipeline
- **THEN** optimistic delivery, server confirmation, and realtime distribution semantics remain consistent with other message types

#### Scenario: Invalid invite room is rejected at send time
- **WHEN** sender attempts to send an invite card for a room they cannot reference
- **THEN** backend rejects the send request with validation/authorization error and no invite message is created
