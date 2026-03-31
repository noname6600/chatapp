# persistent-sidebar-collapse Specification

## Purpose
TBD - created by archiving change remove-global-header-refine-sidebar. Update Purpose after archive.
## Requirements
### Requirement: Header component removed globally
The global Header component SHALL be completely removed from MainLayout rendering for all routes.

#### Scenario: Page load
- **WHEN** any page loads (chat, friends, notifications, profile)
- **THEN** no header bar appears at the top
- **AND** content area expands to fill the vertical space previously occupied by header

### Requirement: Sidebar includes persistent collapse button
The Sidebar SHALL display a collapse/expand button that toggles sidebar width independent of mobile state.

#### Scenario: Desktop collapse
- **WHEN** user clicks collapse button on desktop viewport
- **THEN** sidebar collapses to icon-only mode (reduced width)
- **AND** content area expands to fill reclaimed space

#### Scenario: Desktop expand
- **WHEN** user clicks expand button on collapsed sidebar
- **THEN** sidebar expands back to full width with text labels

### Requirement: Sidebar collapse state is persistent
The collapsed/expanded state of the sidebar SHALL persist across page navigation and viewport changes.

#### Scenario: State persists across routes
- **WHEN** user navigates from /chat to /friends while sidebar is collapsed
- **THEN** sidebar remains collapsed on /friends page

