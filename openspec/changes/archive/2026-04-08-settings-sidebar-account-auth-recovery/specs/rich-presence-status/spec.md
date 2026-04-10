## MODIFIED Requirements

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