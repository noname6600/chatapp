## ADDED Requirements

### Requirement: Self profile overlay routes to profile settings
When the viewer opens their own profile surface, the primary action SHALL provide a direct route to profile settings.

#### Scenario: Self click shows settings-entry action
- **WHEN** the authenticated user opens their own profile card from any supported entry point
- **THEN** the card SHALL display a prominent action button that routes to profile settings
- **THEN** the card SHALL NOT show other-user mini-chat input as the primary interaction

### Requirement: Other-user profile overlay provides mini-chat with Enter transition
When the viewer opens another user profile surface, the primary interaction SHALL be a mini chat input that transitions to full chat on Enter.

#### Scenario: Other-user card shows text-and-emoji mini chat input
- **WHEN** a user opens another user's profile card
- **THEN** the card SHALL show a mini chat input that accepts text and emoji/icon characters
- **THEN** the mini chat interaction SHALL remain scoped to that target user context

#### Scenario: Enter key starts/activates private chat and jumps to chat page
- **WHEN** the viewer submits mini chat with Enter for another user
- **THEN** the frontend SHALL create or resolve a private chat room using the existing private-chat flow
- **THEN** the active room SHALL be selected in chat state
- **THEN** the app SHALL navigate to the full chat page

### Requirement: Secondary overlay actions must not break primary interaction flow
Secondary actions inside profile overlay menus SHALL not prevent self/other primary behavior from being available.

#### Scenario: Secondary menu interactions preserve primary action readiness
- **WHEN** a user opens and closes secondary profile menu actions (friend, block, more)
- **THEN** self cards SHALL still provide settings entry
- **THEN** other-user cards SHALL still provide mini-chat input and Enter-to-chat behavior
