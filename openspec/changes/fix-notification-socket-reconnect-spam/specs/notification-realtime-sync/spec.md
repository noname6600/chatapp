## MODIFIED Requirements

### Requirement: Notification realtime flow SHALL recover after websocket reconnect
The system SHALL reactivate notification handlers after websocket reconnect and trigger convergence fetch to resolve any missed events, while ensuring reconnect behavior remains bounded and does not enter infinite retry spam loops.

#### Scenario: Notification handler active after websocket reconnect
- **WHEN** websocket connection is reestablished after a disconnect
- **THEN** notification event handler is reactivated to process future realtime events

#### Scenario: Reconnect triggers convergence fetch for missed notifications
- **WHEN** websocket reconnect completes
- **THEN** system triggers a convergence fetch to retrieve notification state from backend and reconcile any events missed during the disconnect period

#### Scenario: Missed friendship notifications are reconciled on reconnect
- **WHEN** websocket reconnect completes after friend request events were missed during disconnect
- **THEN** the client reconciles friendship notification and unread friend-request badge state via backend fetch

#### Scenario: Reconnect attempts remain bounded during persistent instability
- **WHEN** websocket repeatedly disconnects in a non-recoverable loop for the same session signature
- **THEN** client reconnect attempts are bounded by backoff and suppression rules rather than continuing at a constant fixed interval forever
