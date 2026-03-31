## MODIFIED Requirements

### Requirement: Upward pagination trigger uses message container scroll state
Loading older messages SHALL be triggered from message list container conditions, with fallback behavior when container overflow is insufficient for user-generated upward scroll.

#### Scenario: Near-top in message container
- **WHEN** user scrolls upward and message list scroll position reaches near-top threshold
- **THEN** the client requests older message page data

#### Scenario: No-overflow fallback trigger
- **WHEN** message list container does not overflow after render and upward scroll cannot occur
- **THEN** the client auto-requests older message page data until scroll becomes possible or no additional progress is available

#### Scenario: Room switch return keeps trigger functional
- **WHEN** user returns to a previously visited room
- **THEN** near-top and no-overflow pagination triggers remain functional for that room
