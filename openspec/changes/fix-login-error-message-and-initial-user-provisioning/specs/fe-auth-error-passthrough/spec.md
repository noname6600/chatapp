## MODIFIED Requirements

### Requirement: Backend error message is extracted and displayed on auth failure
The frontend SHALL extract and display the `message` field from the backend JSON error body when a server returns an auth error response, so that messages like "Invalid credentials" or "Account setup incomplete" are shown to the user verbatim.

#### Scenario: Login with wrong credentials shows server message
- **WHEN** the user submits login with a wrong password
- **THEN** the UI SHALL display the backend message (e.g. "Invalid credentials") in the error area
- **THEN** the UI SHALL NOT display interceptor-internal messages such as "No refresh token"

#### Scenario: Login with non-existent email shows server message
- **WHEN** the user submits login with an email not registered in the system
- **THEN** the UI SHALL display the backend message (e.g. "Invalid credentials")

#### Scenario: Incomplete account setup shows actionable server message
- **WHEN** the server rejects auth/bootstrap with an incomplete-account error response containing a `message`
- **THEN** the UI SHALL display that backend message verbatim in auth-related error surfaces

#### Scenario: AxiosError message extraction fallback
- **WHEN** a server error response does not include a `message` field in the JSON body
- **THEN** the frontend SHALL fall back to the Axios error message string
