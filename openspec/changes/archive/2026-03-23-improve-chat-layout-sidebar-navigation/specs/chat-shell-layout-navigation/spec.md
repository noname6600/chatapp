## ADDED Requirements

### Requirement: Chat shell uses sidebar-first navigation
The chat page SHALL render without a top header and SHALL use the left sidebar as the primary navigation and identity surface.

#### Scenario: Chat page renders shell
- **WHEN** a user opens the chat page
- **THEN** the page does not render a top header region for chat shell controls
- **AND** the left sidebar renders as the navigation shell

### Requirement: Sidebar top section shows current user identity
The sidebar SHALL display current user avatar, username, and display name in the top section.

#### Scenario: User identity is available
- **WHEN** the sidebar is rendered and current user data is present
- **THEN** avatar, username, and display name are visible at the top of the sidebar

### Requirement: Logout action is anchored in sidebar footer
The logout action SHALL be rendered at the bottom-most section of the left sidebar and remain visible independently from room list scroll.

#### Scenario: Room list overflows
- **WHEN** the room list exceeds available sidebar height
- **THEN** the room list scrolls inside the middle section
- **AND** the logout action remains anchored at the sidebar bottom
