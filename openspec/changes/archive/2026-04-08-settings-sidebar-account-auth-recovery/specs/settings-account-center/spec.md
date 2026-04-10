## ADDED Requirements

### Requirement: Sidebar navigation SHALL expose Settings as the final entry
The sidebar SHALL replace the Notifications navigation item with a Settings item and position Settings as the last item in the sidebar navigation list.

#### Scenario: Settings replaces Notifications in sidebar
- **WHEN** an authenticated user views the sidebar navigation
- **THEN** Notifications is not shown as a standalone sidebar item
- **AND** Settings is shown as a navigation item

#### Scenario: Settings appears as last navigation item
- **WHEN** sidebar navigation items are rendered
- **THEN** Settings appears after all other primary navigation entries

### Requirement: Settings page SHALL centralize account controls
The Settings page SHALL contain sections for profile summary, profile editing entrypoint, username/display-name updates, password change, email verification, and account recovery/security actions.

#### Scenario: Settings account sections render
- **WHEN** user navigates to Settings
- **THEN** the page displays account sections for identity, security, and profile actions

#### Scenario: Edit profile entrypoint is available from settings
- **WHEN** user opens Settings account section
- **THEN** an edit-profile action is visible and navigates to profile editing

### Requirement: Settings profile summary SHALL render current identity visuals
The Settings page SHALL show avatar, display name, and profile background preview using current user profile data.

#### Scenario: Settings shows avatar and display name
- **WHEN** settings profile summary loads
- **THEN** current avatar and display name are visible

#### Scenario: Settings shows profile background preview
- **WHEN** settings profile summary loads
- **THEN** current profile background is rendered using the same background source as profile page