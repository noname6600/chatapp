## ADDED Requirements

### Requirement: Avatar and name interactions SHALL route to one profile destination
Across supported app surfaces, clicking user avatar or display name SHALL navigate to the same canonical profile page for that user.

#### Scenario: Sidebar avatar click opens canonical profile
- **WHEN** user clicks their avatar in sidebar identity block
- **THEN** app navigates to canonical profile page

#### Scenario: Sidebar display-name click opens canonical profile
- **WHEN** user clicks their display name in sidebar identity block
- **THEN** app navigates to the same canonical profile page as avatar click

#### Scenario: Cross-surface consistency
- **WHEN** user clicks avatar/name in any supported identity surface
- **THEN** profile routing behavior is consistent and does not open unrelated controls

### Requirement: Profile entrypoints SHALL expose edit-profile action
The canonical profile page SHALL expose a direct edit-profile action.

#### Scenario: Edit profile action from profile page
- **WHEN** user opens their own profile page
- **THEN** an edit-profile button is visible and navigates to profile edit flow