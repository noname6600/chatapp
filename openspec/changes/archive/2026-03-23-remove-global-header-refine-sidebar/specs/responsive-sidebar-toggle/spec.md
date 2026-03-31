## ADDED Requirements

### Requirement: Sidebar supports responsive open and close controls
The chat UI SHALL provide explicit controls to open and close the left sidebar on narrow/mobile viewports.

#### Scenario: Open sidebar on mobile
- **WHEN** viewport width is in narrow/mobile range and user activates the open navigation control
- **THEN** the sidebar becomes visible

#### Scenario: Close sidebar on mobile
- **WHEN** sidebar is visible on narrow/mobile viewport and user activates the close control
- **THEN** the sidebar is hidden

### Requirement: Sidebar default state is viewport-aware
The sidebar SHALL default to open on desktop-sized viewports and SHALL support hidden initial state on narrow/mobile viewports.

#### Scenario: Desktop default
- **WHEN** chat page loads on desktop-sized viewport
- **THEN** the sidebar is visible without requiring user action

#### Scenario: Mobile default
- **WHEN** chat page loads on narrow/mobile viewport
- **THEN** the sidebar is hidden until opened via control
