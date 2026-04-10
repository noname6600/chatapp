# rich-presence-status Specification

## Purpose
Defines rich presence status semantics and rendering behavior across global and room presence surfaces.

## Requirements

### Requirement: Presence service SHALL expose effective rich status across global and room presence
The system SHALL expose each user's effective presence status as `ONLINE`, `AWAY`, or `OFFLINE` in global presence snapshots, room presence snapshots, and direct presence queries used by the frontend.

#### Scenario: Connected active user appears online
- **WHEN** a signed-in user has an active presence connection and remains within the configured activity threshold while in automatic mode
- **THEN** global and room presence payloads report that user with status `ONLINE`

#### Scenario: Connected idle user appears away
- **WHEN** a signed-in user remains connected but exceeds the configured inactivity threshold while in automatic mode
- **THEN** global and room presence payloads report that user with status `AWAY`

#### Scenario: Disconnected user appears offline
- **WHEN** a user's presence session closes or expires beyond the presence TTL
- **THEN** global and room presence payloads report that user with status `OFFLINE`

### Requirement: Presence service SHALL support manual status override and automatic mode
The system SHALL allow the signed-in user to switch between automatic status derivation and manual status selection through a presence API that updates the effective status seen by frontend consumers.

#### Scenario: User sets manual away status
- **WHEN** the signed-in user sets their presence mode to manual with status `AWAY`
- **THEN** subsequent presence queries and websocket updates report that user as `AWAY` until the manual override is cleared or changed

#### Scenario: User sets manual offline status
- **WHEN** the signed-in user sets their presence mode to manual with status `OFFLINE`
- **THEN** the frontend receives presence data that treats that user as offline for friend and room presence surfaces

#### Scenario: User returns to automatic mode
- **WHEN** the signed-in user switches their presence mode back to automatic
- **THEN** the effective status resumes following connection and activity-derived presence rules

### Requirement: Frontend presence surfaces SHALL render rich status consistently
The system SHALL render the effective presence status consistently in the friend list, room member presence views, sidebar user block, and other global presence surfaces that consume presence-service data. The current user's status indicator remains visible in sidebar identity UI, while avatar/name clicks MUST route to canonical profile navigation rather than opening unrelated interaction modes.

#### Scenario: Friend row shows away instead of online boolean
- **WHEN** a friend's effective presence status is `AWAY`
- **THEN** the friend list renders that friend with an away-specific status label or visual treatment rather than a generic online indicator

#### Scenario: Room member list reflects room presence snapshot
- **WHEN** the frontend receives a room presence snapshot containing member statuses
- **THEN** the room member list renders each member using the provided effective status without reducing it to a binary online or offline value

#### Scenario: Sidebar shows status dot on current user avatar
- **WHEN** the sidebar is rendered with an authenticated user
- **THEN** a colored status dot is overlaid on the bottom-right corner of the current user's avatar, reflecting their current effective presence status (`ONLINE` = green, `AWAY` = amber, `OFFLINE` = grey)

#### Scenario: Sidebar avatar click follows profile navigation contract
- **WHEN** the authenticated user clicks avatar or display name in sidebar identity block
- **THEN** app navigates to canonical profile page and does not diverge into inconsistent alternate behaviors

#### Scenario: Presence updates still propagate after status change
- **WHEN** user changes presence status from supported control points
- **THEN** presence API updates effective status and global UI reflects the new status consistently
