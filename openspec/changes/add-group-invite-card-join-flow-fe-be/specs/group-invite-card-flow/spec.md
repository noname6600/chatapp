## ADDED Requirements

### Requirement: Users can send group invite cards in chat
The system SHALL allow eligible users to send a structured group invite card message that references a target group room.

#### Scenario: Sender creates invite card from room context
- **WHEN** a user selects "Send Group Invite" for a group room they can invite others to
- **THEN** the client sends a message payload containing an invite-card block with target `roomId` and room snapshot metadata

#### Scenario: Invite card message is persisted and broadcast
- **WHEN** the invite-card message is accepted by the backend
- **THEN** it is persisted as a normal chat message and delivered through existing realtime message distribution

### Requirement: Recipients can join from invite card
The system SHALL provide a join action on invite cards that joins the referenced room and navigates the user into that room on success.

#### Scenario: Recipient joins room by clicking card action
- **WHEN** a non-member recipient clicks "Join Group" on a valid invite card
- **THEN** the backend adds the user to the referenced room and the frontend routes to the joined room chat

#### Scenario: Repeated join clicks are idempotent
- **WHEN** a user clicks join multiple times for the same invite card while already a member
- **THEN** the backend returns a successful idempotent response without duplicate membership

### Requirement: Invite card state is explicit and safe
The system SHALL show deterministic invite card states based on live join eligibility and backend outcomes.

#### Scenario: Already joined user sees joined state
- **WHEN** invite card is rendered for a user who is already a room member
- **THEN** card shows non-primary "Joined" state and does not attempt duplicate join on click

#### Scenario: Unavailable room shows disabled state
- **WHEN** the target room is no longer joinable (deleted, hidden, or access denied)
- **THEN** card shows unavailable state and join action is disabled or returns a clear denial message
