## ADDED Requirements

### Requirement: Notification UI state SHALL update immediately from realtime websocket events
The system SHALL apply incoming notification websocket events to client notification state without requiring a browser refresh.

#### Scenario: Bell count increments on incoming notification event
- **WHEN** a NOTIFICATION_NEW event is received for the authenticated user
- **THEN** the bell unread count is incremented in client state immediately within the same interaction cycle

#### Scenario: Notification panel list prepends incoming notification
- **WHEN** a NOTIFICATION_NEW event is received while the app is open
- **THEN** the new notification is inserted at the top of the in-memory notification list without requiring a REST refetch

#### Scenario: Manual refresh is not required to see latest notification
- **WHEN** a notification is created for a connected user
- **THEN** the notification appears in UI before any page refresh action is performed

### Requirement: Notification realtime flow SHALL recover after websocket reconnect
The system SHALL restore notification event handling after websocket reconnection and reconcile with backend truth.

#### Scenario: Notification handler is active after reconnect
- **WHEN** websocket disconnects and reconnects during an active session
- **THEN** subsequent NOTIFICATION_NEW events are processed and reflected in bell/list state without page refresh

#### Scenario: Reconnect triggers convergence fetch
- **WHEN** websocket reconnect succeeds
- **THEN** client reconciles notification list and unread count with backend snapshot to eliminate drift from missed events
