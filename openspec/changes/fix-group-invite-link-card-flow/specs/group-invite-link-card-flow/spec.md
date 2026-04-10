## ADDED Requirements

### Requirement: Unified group invite entry points SHALL send invite cards
The system SHALL provide a single group-invite behavior across supported entry points: room-header invite actions and user-profile popup invite actions MUST both send a ROOM_INVITE card message for a selected group room.

#### Scenario: Room header invite members sends invite card
- **WHEN** user opens a group room and triggers the room-header invite action
- **THEN** client sends a ROOM_INVITE block message for that current group room
- **AND** the room header does not expose a separate duplicate “Send Invite Card” action

#### Scenario: Profile popup invite action sends selected group card
- **WHEN** user opens another user's profile popup and selects “Invite to group”
- **THEN** a selectable list of current user's eligible group rooms is shown
- **AND** selecting a group sends a ROOM_INVITE card message for that group

### Requirement: Invite cards SHALL include link/code-oriented join context
Invite cards SHALL present join intent as a clickable card with link/code-oriented room context so recipients can join with minimal friction.

#### Scenario: Invite card displays join context
- **WHEN** a ROOM_INVITE card is rendered in chat
- **THEN** card shows room identity and join affordance with link/code-oriented context

#### Scenario: Recipient joins from invite card
- **WHEN** recipient clicks join on a valid invite card
- **THEN** join flow executes once and routes recipient to the joined room state

### Requirement: Invite API paths SHALL be controller-mapped and not fall through to static resources
Invite-related API requests SHALL resolve to controller endpoints under API routing and MUST NOT return static-resource not-found fallback behavior for valid invite operations.

#### Scenario: Invite request resolves to API controller
- **WHEN** client posts an invite operation to the configured invite API path
- **THEN** request is handled by a controller endpoint and returns API response semantics

#### Scenario: Missing invite route does not masquerade as static-resource miss
- **WHEN** invite operation path is incorrect or unsupported
- **THEN** backend returns explicit API error response rather than static-resource handler fallback semantics