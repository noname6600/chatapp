# fe-auth-bootstrap-resilience

## Purpose
Define the requirement for frontend bootstrap flows to be session-aware and handle auth and non-auth failures gracefully during application initialization.

## Requirements

### Requirement: Auth-dependent bootstrap requests are session-aware
Frontend bootstrap flows that depend on authenticated APIs (for example rooms preload, friendship unread preload, notifications preload) MUST execute only when a valid access token/session context exists.

#### Scenario: Bootstrap is skipped when no active session
- **WHEN** app initializes without `access_token`
- **THEN** protected bootstrap requests SHALL be skipped
- **AND** no protected API request is sent during unauthenticated startup

#### Scenario: Bootstrap runs after successful login
- **WHEN** login succeeds and auth context stores valid session tokens
- **THEN** room/friendship/notification bootstrap requests SHALL execute
- **AND** responses SHALL initialize corresponding stores

### Requirement: Session failure during bootstrap is handled gracefully
If a bootstrap request fails because the session is missing or invalid, the client MUST perform controlled session handling and avoid unhandled promise errors.

#### Scenario: 401 during bootstrap with missing refresh token
- **WHEN** a bootstrap request receives 401 and refresh token is unavailable
- **THEN** client SHALL treat the session as unauthenticated
- **AND** app SHALL avoid uncaught promise errors in store/provider effects

#### Scenario: Non-auth error during bootstrap remains observable
- **WHEN** a bootstrap request fails with non-auth error (for example 404 from route misconfiguration or 500)
- **THEN** the error SHALL be logged with endpoint context for diagnosis
- **AND** app SHALL remain operational without websocket teardown loops

### Requirement: Friendship websocket lifecycle logging distinguishes normal cleanup from error
Friendship websocket lifecycle logs MUST clearly distinguish expected manual disconnect cleanup from connectivity/auth failure paths.

#### Scenario: Manual cleanup log is informational
- **WHEN** provider cleanup calls websocket disconnect during auth-state transition
- **THEN** log SHALL indicate expected manual cleanup behavior
- **AND** it SHALL NOT be classified as connection failure

#### Scenario: Auth-failure disconnect is observable
- **WHEN** websocket closes due to invalid/expired auth context
- **THEN** the system SHALL emit an auth/session-relevant diagnostic entry
- **AND** reconnect logic SHALL respect unauthenticated state suppression
