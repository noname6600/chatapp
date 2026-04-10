## MODIFIED Requirements

### Requirement: Frontend presence surfaces SHALL render rich status consistently
The system SHALL render the effective presence status consistently in the friend list, room member presence views, sidebar user block, and other global presence surfaces that consume presence-service data. The current user's status selector MUST be rendered adjacent to the current user's avatar in the application sidebar, not on a separate page. The Friends page MUST NOT contain a standalone status control.

#### Scenario: Friend row shows away instead of online boolean
- **WHEN** a friend's effective presence status is `AWAY`
- **THEN** the friend list renders that friend with an away-specific status label or visual treatment rather than a generic online indicator

#### Scenario: Room member list reflects room presence snapshot
- **WHEN** room presence data is available for members in a room
- **THEN** the member list renders each member's effective status using the rich `ONLINE` / `AWAY` / `OFFLINE` values

#### Scenario: Sidebar shows status dot on current user avatar
- **WHEN** the sidebar is rendered with an authenticated user
- **THEN** a colored status dot is overlaid on the bottom-right corner of the current user's avatar, reflecting their current effective presence status (`ONLINE` = green, `AWAY` = amber, `OFFLINE` = grey)

#### Scenario: Clicking sidebar avatar opens status selector
- **WHEN** the authenticated user clicks their avatar or status dot in the sidebar
- **THEN** a compact popover/dropdown opens allowing selection of Online, Away, or Offline

#### Scenario: Status change from sidebar updates presence
- **WHEN** the user selects a new status from the sidebar popover
- **THEN** the presence API is called with the new status, the dot color updates, and the popover closes

#### Scenario: Friends page does not show standalone status selector
- **WHEN** the Friends page is rendered
- **THEN** there is no separate presence status control on the page; status management is done via the sidebar only
