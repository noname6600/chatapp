## ADDED Requirements

### Requirement: Frontend submits join code exactly as entered
The frontend SHALL submit the join code payload using the exact user-entered case and SHALL NOT force uppercase/lowercase normalization before request dispatch.

#### Scenario: Mixed-case input is preserved in request
- **WHEN** a user enters `AbC123` in the join code input
- **THEN** the frontend sends `AbC123` in the join request payload

#### Scenario: No implicit upper/lower transformation
- **WHEN** a user enters `abc123` in lowercase
- **THEN** the frontend does not transform it to `ABC123` before sending

### Requirement: Frontend room code display is scoped by roomId
The frontend SHALL store and render fetched room codes in room-scoped state so code from one room cannot appear in another room view.

#### Scenario: Room A code does not leak into room B
- **WHEN** room A code has been fetched and user navigates to room B
- **THEN** room B UI does not display room A code unless room B code is fetched for room B

#### Scenario: Per-room lookup returns room-scoped UI value
- **WHEN** room A and room B are both loaded in frontend state
- **THEN** each room view resolves code from its own roomId entry

### Requirement: Stale room-code responses are ignored by frontend
The frontend SHALL ignore late/out-of-order room-code fetch responses that do not belong to the current request ownership context.

#### Scenario: Rapid switch does not overwrite active room code
- **WHEN** user requests room A code then quickly switches to room B before room A response returns
- **THEN** late room A response does not overwrite room B displayed code

#### Scenario: Only matching request context mutates state
- **WHEN** a room-code response arrives
- **THEN** frontend applies it only if response context matches active/requested room context
