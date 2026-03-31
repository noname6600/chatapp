## ADDED Requirements

### Requirement: Toggle preserves aggregate count correctness
The system SHALL preserve reaction aggregate counts for each `(messageId, emoji)` while toggles are processed for a user.

#### Scenario: First toggle adds reaction
- **WHEN** user U toggles emoji E on message M and no row exists for `(M, U, E)`
- **THEN** a row is created and aggregate count for `(M, E)` increases by exactly 1

#### Scenario: Second toggle removes reaction
- **WHEN** user U toggles emoji E on message M and a row exists for `(M, U, E)`
- **THEN** the row is deleted and aggregate count for `(M, E)` decreases by exactly 1

### Requirement: Toggle operations are idempotent under retries
The system MUST remain consistent when duplicate requests or duplicate processing attempts occur for the same toggle transition.

#### Scenario: Duplicate add attempt
- **WHEN** add-path processing is retried for `(M, U, E)` after the row already exists
- **THEN** aggregate count for `(M, E)` is not incremented a second time

#### Scenario: Duplicate remove attempt
- **WHEN** remove-path processing is retried for `(M, U, E)` after the row has been deleted
- **THEN** aggregate count for `(M, E)` is not decremented below the true persisted total
