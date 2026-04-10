# message-mention-autocomplete Specification

## Purpose
Define requirements for mention suggestion autocomplete functionality, including bounded suggestion lists, query-driven matching by display name and username, presence metadata rendering, deterministic mention token insertion, and interactive rendered mentions in the chat message list.

## Requirements

### Requirement: Mention suggestions are bounded and query-driven
The system SHALL show at most 5 mention suggestions for an active @query and SHALL require additional typing to narrow ambiguous results.

#### Scenario: Suggestion list is capped at five items
- **WHEN** user types `@a` and more than five members match
- **THEN** only five ranked suggestions are rendered

#### Scenario: Narrowing query changes candidate set
- **WHEN** user continues typing from `@a` to `@alex`
- **THEN** the rendered suggestions are recalculated using the longer query and remain capped to five

### Requirement: Mention search matches display name and username
The system SHALL match mention candidates by both display name and username using case-insensitive comparison.

#### Scenario: Candidate found by display name
- **WHEN** user types `@john` and a member display name contains `John`
- **THEN** that member appears in suggestions

#### Scenario: Candidate found by username
- **WHEN** user types `@j_smith` and a member username equals or contains `j_smith`
- **THEN** that member appears in suggestions

### Requirement: Suggestion row shows identity and presence metadata
Each mention suggestion row SHALL render avatar, display name, and presence icon (online/away/offline) on the left, and username on the right.

#### Scenario: Suggestion row layout includes required fields
- **WHEN** mention suggestions are visible
- **THEN** each row shows avatar + display name + presence icon on left and username on right

### Requirement: Mention selection inserts deterministic content
Selecting a mention SHALL insert deterministic, non-null content and SHALL NOT produce undefined values in composer text.

#### Scenario: Mouse selection inserts valid mention token
- **WHEN** user clicks a suggestion row
- **THEN** composer content includes a valid mention token for the selected user and no `undefined` text

#### Scenario: Keyboard selection inserts valid mention token
- **WHEN** user highlights a suggestion with arrow keys and confirms selection
- **THEN** composer content includes a valid mention token for the selected user and no `undefined` text

### Requirement: Username is available for mention identity rendering
The system SHALL propagate username for mention candidates from bulk user fetch through frontend cache and suggestion mapping. The frontend cache SHALL be updated with the current, correct user profile (including `accountId`) whenever the authenticated user saves profile changes, so that mention suggestions always reflect the latest username without stale duplicates.

#### Scenario: Username is present in bulk user response
- **WHEN** client fetches `POST /api/v1/users/bulk`
- **THEN** each returned profile includes `accountId`, `username`, and `displayName` for mention suggestions

#### Scenario: Suggestion right column reflects propagated username
- **WHEN** mention suggestions are rendered
- **THEN** right column shows username from mapped candidate profile and does not degrade to empty placeholder for valid users

#### Scenario: Mention suggestions show only the current username after username change
- **WHEN** the authenticated user changes their username via profile settings and saves
- **THEN** the mention autocomplete shows only the new username for that user and does NOT show the old username as a separate suggestion

#### Scenario: Local cache entry is keyed by correct accountId after profile save
- **WHEN** the authenticated user saves their profile (username, display name, or avatar)
- **THEN** the frontend user cache entry for that user's `accountId` is updated with the new values and no phantom entry is written under an undefined key

### Requirement: Mentions display canonical display name in chat content
The system SHALL render mention labels in chat as `@DisplayName` regardless of whether the mention was typed with username or display-name alias.

#### Scenario: Username-typed mention renders as display name
- **WHEN** sender types `@alice` and sends message mentioning user Alice Nguyen
- **THEN** message list renders mention label as `@Alice Nguyen`

#### Scenario: Display-name-typed mention renders consistently
- **WHEN** sender types a display-name-based mention alias and sends
- **THEN** message list renders mention label as canonical `@DisplayName` for the resolved user

### Requirement: Mention token is interactive in message list
Rendered mention labels SHALL be clickable and open the user profile popup for the mentioned user.

#### Scenario: Click mention opens profile popup
- **WHEN** user clicks a rendered mention token in message list
- **THEN** profile popup opens for the resolved mentioned user with name and avatar context
