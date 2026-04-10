# settings-profile-simplified-layout Specification

## Purpose

Simplify Settings information architecture by removing the redundant Account Recovery tab while keeping profile management as the primary destination.

## Requirements

### Requirement: Settings page removes account recovery tab
The system SHALL render Settings with profile-focused controls and SHALL NOT render a dedicated Account Recovery tab in the settings section switcher.

#### Scenario: Recovery tab is not shown in settings tabs
- **WHEN** an authenticated user opens the Settings page
- **THEN** section tabs include Profile and Security only
- **AND** no Account Recovery tab is rendered

### Requirement: Profile remains the canonical settings section
The system SHALL keep profile management in Settings as the primary account-edit destination after recovery tab removal.

#### Scenario: Settings defaults to profile management
- **WHEN** an authenticated user opens Settings from sidebar or direct route
- **THEN** the Profile section is available immediately for account profile management
