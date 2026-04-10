## ADDED Requirements

### Requirement: Unified profile card presentation across entry points
The frontend SHALL render a consistent profile card structure when users click avatar, icon, display name, or mention surfaces.

#### Scenario: Avatar click uses shared profile card contract
- **WHEN** a user clicks any profile avatar/icon entry point
- **THEN** the rendered profile surface SHALL use the shared profile card layout and identity fields
- **THEN** the visual structure SHALL match the canonical profile presentation used by profile settings preview

#### Scenario: Name and mention click use the same profile contract
- **WHEN** a user clicks a display name or mention token in supported surfaces
- **THEN** the same profile card contract SHALL be used
- **THEN** identity content (avatar, display name, username, about/bio, background treatment) SHALL stay consistent with other entry points

### Requirement: Profile identity updates propagate consistently
Profile identity attributes edited in settings SHALL be reflected consistently across all profile card entry points.

#### Scenario: Updated profile attributes appear in all profile cards
- **WHEN** the current user updates avatar, display name, username, or profile background in settings
- **THEN** subsequent profile cards opened from avatar/name/mention entry points SHALL show the updated attributes
- **THEN** no entry point SHALL display stale legacy layout fields after the update cycle completes
