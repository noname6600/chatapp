## MODIFIED Requirements

### Requirement: API Client Error Handling Works Through Gateway
API clients SHALL continue to provide consistent error handling and token refresh logic regardless of whether requests route through the gateway or directly to services. Private chat initiation responses SHALL be mapped into deterministic client outcomes for both successful and failed start-chat attempts.

#### Scenario: Token refresh triggered on 401 response
- **WHEN** an API request receives a 401 Unauthorized response
- **THEN** system attempts to refresh the token using the refresh API
- **AND** the original request is retried with the new token

#### Scenario: Private chat initiation success returns routable room identifier
- **WHEN** frontend calls the private chat initiation endpoint through the gateway and the request succeeds
- **THEN** API client resolves with a payload containing a valid room identifier for navigation

#### Scenario: Private chat initiation validation failure returns actionable client error
- **WHEN** frontend calls the private chat initiation endpoint through the gateway and backend rejects the request due to target eligibility or validation
- **THEN** API client exposes a stable error category/code to the UI layer
- **AND** UI can present user-facing feedback without silent failure
