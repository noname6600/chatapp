## ADDED Requirements

### Requirement: ROOM_INVITE blocks render as actionable invite cards
The message renderer SHALL render `ROOM_INVITE` blocks as dedicated invite cards with room identity details and a primary join action.

#### Scenario: Invite card displays room summary content
- **WHEN** timeline contains a message block of type `ROOM_INVITE`
- **THEN** renderer shows room name, room icon/avatar fallback, and invite affordance in a distinct card layout

#### Scenario: Invite card join action dispatches join flow
- **WHEN** user clicks invite card join action from the message timeline
- **THEN** frontend triggers join request for the target room and updates card state based on response

### Requirement: Invite card rendering degrades safely on incomplete data
The renderer SHALL fail safely when invite payload data is incomplete or stale.

#### Scenario: Missing optional snapshot fields still render card
- **WHEN** invite payload lacks optional fields like room icon or member count
- **THEN** card renders with deterministic fallbacks and remains usable

#### Scenario: Invalid target metadata renders unavailable card
- **WHEN** invite payload references an invalid or inaccessible room
- **THEN** renderer shows disabled unavailable state instead of throwing or breaking message list rendering
