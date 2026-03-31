# user-reaction-highlight Specification

## Purpose
Provide visual distinction for reactions added by the current user, making it immediately clear which reactions belong to the logged-in user versus other participants.

## Requirements

### Requirement: Current user reactions are visually highlighted
The system SHALL apply a distinct visual style (highlight, background color, or styling class) to reactions where reaction.userId equals the currentUser.id, making them visually distinguishable from others' reactions.

#### Scenario: User's own reaction is highlighted
- **WHEN** current user (U1) has reacted with emoji 🔥 on message M1
- **THEN** the reaction chip for 🔥 displays with a user-reaction highlight style (e.g., colored background, accent border, or CSS class `user-reaction`)

#### Scenario: Other user's reaction is not highlighted
- **WHEN** a different user (U2) has reacted with emoji 🔥 on message M1
- **THEN** the reaction chip for 🔥 from U2 displays without highlight styling

### Requirement: Highlight applies immediately after optimistic add
The system SHALL apply highlight styling to optimistic reactions (before server confirmation) for reactions added by the current user.

#### Scenario: Optimistic reaction shows highlight immediately
- **WHEN** current user clicks emoji E on message M1
- **THEN** the optimistic reaction displays with highlight styling immediately, before realtime confirmation

#### Scenario: Highlight persists after real reaction arrives
- **WHEN** optimistic reaction with highlight merges with real reaction event
- **THEN** the merged reaction maintains highlight styling without interruption

### Requirement: Highlight is removed when user removes reaction
The system SHALL immediately remove highlight styling when the current user removes a reaction, and keep it removed after realtime reconciliation.

#### Scenario: Highlight removed after user unreacts
- **WHEN** current user toggles off emoji E on message M1
- **THEN** the reaction chip no longer displays with highlight styling immediately (optimistic removal)

#### Scenario: Highlight stays removed after real event
- **WHEN** the real remove event for that reaction arrives via realtime
- **THEN** the reaction stays removed and highlight is not restored

### Requirement: Highlight is applied via CSS class, not inline styles
The system SHALL apply highlight using a CSS class selector (e.g., `.user-reaction` or `[data-user-reaction]`) that can be themed and customized without modifying component logic.

#### Scenario: CSS class applied to reaction element
- **WHEN** a reaction belongs to the current user
- **THEN** the reaction container element has the CSS class `user-reaction` applied

#### Scenario: Styling is configurable via CSS
- **WHEN** the visual theme changes or branding updates
- **THEN** highlight color and style can be modified in CSS without code changes
