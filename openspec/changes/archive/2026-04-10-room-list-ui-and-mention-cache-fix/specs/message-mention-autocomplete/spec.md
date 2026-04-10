## MODIFIED Requirements

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
