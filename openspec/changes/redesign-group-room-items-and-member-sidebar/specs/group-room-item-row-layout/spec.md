## ADDED Requirements

### Requirement: Group room items render as compact list rows with name and last message preview
In the Groups section of the room list sidebar, each group room item SHALL render as a full-width list row containing: an avatar on the left (square with rounded corners), the group name as primary text, and the last message preview (sender name + content snippet) as secondary text when available.

#### Scenario: Group room row shows avatar and name
- **WHEN** the Groups section is expanded and a group room is rendered
- **THEN** the row SHALL display the group avatar on the left and the group name as the primary text label

#### Scenario: Group room row shows last message preview when available
- **WHEN** a group room has a `lastMessage` value
- **THEN** the row SHALL display the sender name and a truncated content snippet below the group name

#### Scenario: Group room row handles missing last message gracefully
- **WHEN** a group room has no `lastMessage`
- **THEN** the row SHALL render only the group name with no secondary text, without layout errors

#### Scenario: Group room row reflects active and hover states
- **WHEN** a group room is the active room
- **THEN** the row SHALL have a visually distinct active highlight (e.g., left-border accent, background tint) that clearly differs from hover state
