## MODIFIED Requirements

### Requirement: User-own reactions are visually highlighted
The system SHALL visually highlight reactions that include the current user so users can identify their own reaction state at a glance. The backend SHALL provide ownership (`reactedByMe`) in message history responses so highlight state is correct on first render before any new realtime reaction event is received.

#### Scenario: Highlight own reaction on initial history load
- **WHEN** current user has reacted with emoji E on message M and message history is fetched
- **THEN** the reaction summary for emoji E includes `reactedByMe = true`, enabling immediate highlighted render

#### Scenario: Highlight remains consistent after realtime updates
- **WHEN** current user toggles emoji E on message M and realtime reaction events are processed
- **THEN** highlighted state remains consistent with backend ownership and does not invert due to missing initial ownership data
