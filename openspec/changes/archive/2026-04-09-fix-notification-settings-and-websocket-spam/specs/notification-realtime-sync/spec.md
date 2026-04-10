## MODIFIED Requirements

### Requirement: Notification realtime flow SHALL recover after websocket reconnect
The system SHALL reactivate notification handlers after websocket reconnect and trigger convergence fetch to resolve any missed events, while suppressing repeated futile reconnect attempts when the websocket endpoint contract is invalid for the current session.

#### Scenario: Notification handler active after websocket reconnect
- **WHEN** websocket connection is reestablished after a disconnect
- **THEN** notification event handler is reactivated to process future realtime events

#### Scenario: Reconnect triggers convergence fetch for missed notifications
- **WHEN** websocket reconnect completes
- **THEN** system triggers a convergence fetch to retrieve notification state from backend and reconcile any events missed during the disconnect period

#### Scenario: Missed friendship notifications are reconciled on reconnect
- **WHEN** websocket reconnect completes after friend request events were missed during disconnect
- **THEN** the client reconciles friendship notification and unread friend-request badge state via backend fetch

#### Scenario: Invalid notification websocket endpoint does not cause infinite reconnect churn
- **WHEN** the notification websocket fails because the configured route or handshake path is invalid
- **THEN** the client emits an observable diagnostic entry for the failure reason
- **AND** repeated reconnect attempts are suppressed for that session until configuration or auth state changes