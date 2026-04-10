## MODIFIED Requirements

### Requirement: Chat shell uses sidebar-first navigation
The chat page SHALL render without a top header and SHALL use the left sidebar as the primary navigation and identity surface. Primary sidebar navigation SHALL expose Settings as the final item and SHALL not expose Notifications as a standalone primary nav item.

#### Scenario: Chat page renders shell
- **WHEN** a user opens the chat page
- **THEN** the page does not render a top header region for chat shell controls
- **AND** the left sidebar renders as the navigation shell

#### Scenario: Sidebar primary navigation includes Settings last
- **WHEN** sidebar navigation is rendered
- **THEN** Settings is present as a primary navigation item
- **AND** Settings appears after all other primary navigation items
- **AND** Notifications is not shown as a separate primary nav item