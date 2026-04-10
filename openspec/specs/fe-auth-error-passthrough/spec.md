# fe-auth-error-passthrough

## Purpose
Define the requirement for the frontend HTTP client to pass through auth endpoint error responses without intercepting them for token refresh, and to display backend error messages verbatim on authentication failure.

## Requirements

### Requirement: Auth endpoint 401 responses are not intercepted for token refresh
The frontend HTTP client SHALL NOT attempt to refresh the access token when a 401 response comes from the login or register endpoint. The original error response from the server SHALL be propagated to the caller.

#### Scenario: Wrong credentials on login
- **WHEN** the user submits login credentials that do not match any account
- **THEN** the auth-refresh interceptor SHALL skip the token refresh attempt
- **THEN** the original 401 error response SHALL be propagated to the login form handler

#### Scenario: Wrong credentials on register
- **WHEN** the user submits a register request that is rejected with 401 (e.g. duplicate email handled as 409, but a 401-type conflict)
- **THEN** the auth-refresh interceptor SHALL skip the token refresh attempt
- **THEN** the original error response SHALL be propagated to the form handler

### Requirement: Backend error message is extracted and displayed on auth failure
The frontend SHALL extract and display the `message` field from the backend JSON error body when a server returns an error response, so that messages like "Invalid credentials" or "Account disabled" are shown to the user verbatim.

#### Scenario: Login with wrong credentials shows server message
- **WHEN** the user submits login with a wrong password
- **THEN** the UI SHALL display the backend message (e.g. "Invalid credentials") in the error area
- **THEN** the UI SHALL NOT display interceptor-internal messages such as "No refresh token"

#### Scenario: Login with non-existent email shows server message
- **WHEN** the user submits login with an email not registered in the system
- **THEN** the UI SHALL display the backend message (e.g. "Invalid credentials")

#### Scenario: AxiosError message extraction fallback
- **WHEN** a server error response does not include a `message` field in the JSON body
- **THEN** the frontend SHALL fall back to the Axios error message string
