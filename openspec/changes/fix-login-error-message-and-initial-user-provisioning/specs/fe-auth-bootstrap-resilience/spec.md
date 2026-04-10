## MODIFIED Requirements

### Requirement: Session failure during bootstrap is handled gracefully
If a bootstrap request fails because the session is missing, invalid, or marked incomplete-account, the client MUST perform controlled session handling and avoid unhandled promise errors.

#### Scenario: 401 during bootstrap with missing refresh token
- **WHEN** a bootstrap request receives 401 and refresh token is unavailable
- **THEN** client SHALL treat the session as unauthenticated
- **AND** app SHALL avoid uncaught promise errors in store/provider effects

#### Scenario: Incomplete-account bootstrap failure triggers controlled reset
- **WHEN** bootstrap receives an explicit incomplete-account auth error code
- **THEN** the client SHALL clear session state via controlled auth reset exactly once per bootstrap cycle
- **AND** the UI SHALL surface actionable error guidance instead of entering reconnect/logout loops

#### Scenario: Non-auth error during bootstrap remains observable
- **WHEN** a bootstrap request fails with non-auth error (for example 404 from route misconfiguration or 500)
- **THEN** the error SHALL be logged with endpoint context for diagnosis
- **AND** app SHALL remain operational without websocket teardown loops
