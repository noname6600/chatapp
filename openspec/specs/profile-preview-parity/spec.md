# profile-preview-parity Specification

## Purpose

Guarantee consistent profile identity presentation between Settings preview and profile views opened from chat avatar/name entrypoints.

## Requirements

### Requirement: Profile preview uses shared identity mapping across settings and chat entrypoints
The system SHALL use a shared profile presentation mapping so that profile preview identity fields (avatar, display name, username/about-me, background fallback) are consistent between Settings preview and profile views opened from chat avatar/name clicks.

#### Scenario: Avatar click in chat matches settings preview identity values
- **WHEN** the user opens profile from a chat avatar click
- **THEN** the rendered avatar, display name, and background behavior matches the Settings preview mapping for the same profile

#### Scenario: Name click in chat matches settings preview identity values
- **WHEN** the user opens profile from a chat display-name click
- **THEN** the rendered display name, username/about-me representation, and fallback behavior matches the Settings preview mapping for the same profile

### Requirement: Preview parity tolerates missing metadata consistently
The system SHALL apply identical fallback rules for missing avatar, missing about-me, and invalid background metadata in both Settings preview and chat-triggered profile views.

#### Scenario: Invalid background is normalized identically
- **WHEN** profile background metadata is missing or invalid
- **THEN** both Settings preview and chat-triggered profile view render the same fallback background
