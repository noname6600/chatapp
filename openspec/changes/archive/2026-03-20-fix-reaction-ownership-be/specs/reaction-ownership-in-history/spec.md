## ADDED Requirements

### Requirement: Message history includes per-user reaction ownership
The system SHALL include per-emoji ownership in message history responses so the authenticated user can determine whether each returned reaction is their own.

#### Scenario: Latest messages include ownership
- **WHEN** an authenticated user requests latest messages for a room
- **THEN** each reaction item in the response includes `emoji`, `count`, and `reactedByMe` for that authenticated user

#### Scenario: Older messages include ownership
- **WHEN** an authenticated user requests messages before sequence N
- **THEN** each reaction item in the response includes `emoji`, `count`, and `reactedByMe` for that authenticated user

### Requirement: Ownership value is explicit and deterministic
The system MUST return an explicit boolean ownership value for each reaction item in history responses.

#### Scenario: User has reacted to an emoji
- **WHEN** user U has an existing reaction row for message M and emoji E
- **THEN** the reaction summary for emoji E in user U's history response has `reactedByMe = true`

#### Scenario: User has not reacted to an emoji
- **WHEN** user U has no reaction row for message M and emoji E
- **THEN** the reaction summary for emoji E in user U's history response has `reactedByMe = false`
