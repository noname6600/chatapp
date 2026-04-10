## ADDED Requirements

### Requirement: Room member list visual hierarchy must be clear and readable
The system SHALL render each room member entry with a stable visual hierarchy: avatar, primary identity text (display name), secondary identity text (username or metadata), and a right-aligned presence or role affordance when available.

#### Scenario: Member row shows primary and secondary identity
- **WHEN** the room member list renders a member with both display name and username
- **THEN** the row SHALL show display name as the primary label and username as secondary text with distinct typography

#### Scenario: Long member names are handled without layout break
- **WHEN** a member has a long display name or username
- **THEN** the row SHALL preserve alignment and truncate overflow text without overlapping status or role indicators

### Requirement: Room list behavior remains unchanged by member list redesign
The system SHALL preserve existing room list behavior and visuals while applying this change only to room member list surfaces.

#### Scenario: Room list renders unchanged after member list update
- **WHEN** the user opens the sidebar room list after the member list redesign is deployed
- **THEN** room grouping, sorting, unread indicators, and room item styling SHALL remain consistent with prior behavior

### Requirement: Room member list must support loading, empty, and responsive states
The system SHALL provide deterministic UI states for loading and empty member lists, and SHALL keep entries usable across desktop and mobile breakpoints.

#### Scenario: Loading state is shown before member data arrives
- **WHEN** the room member panel is opened and member data is still fetching
- **THEN** the UI SHALL show a loading state placeholder rather than blank content

#### Scenario: Empty state is shown when no members are available
- **WHEN** the room has no visible members for the current user context
- **THEN** the panel SHALL display an explicit empty-state message

#### Scenario: Member list remains usable on narrow viewports
- **WHEN** the viewport width is narrow (mobile or small tablet)
- **THEN** member rows SHALL remain tappable and readable with preserved avatar and primary name visibility
