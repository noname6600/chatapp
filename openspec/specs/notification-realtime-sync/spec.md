# notification-realtime-sync Specification

## Purpose
Define deterministic realtime notification UI behavior, including immediate bell count and notification panel updates from websocket events, and recovery mechanisms after websocket reconnect.
## Requirements
### Requirement: Notification UI state SHALL update immediately from realtime websocket events
The system SHALL update notification bell count and notification panel immediately when realtime events are received, while preserving mention-targeted semantics and stable non-overlapping layout behavior.

This includes friendship-related notifications in addition to message mention and activity notifications.

#### Scenario: Bell count increments on new notification event
- **WHEN** a realtime notification event is received from the websocket
- **THEN** notification bell count increments immediately in the UI

#### Scenario: Notification panel prepends new notification from realtime event
- **WHEN** a notification event is received via realtime websocket and notification panel is open
- **THEN** new notification is prepended to the notification panel without requiring manual refresh

#### Scenario: Realtime notification updates do not require page refresh
- **WHEN** notification updates arrive via realtime websocket
- **THEN** UI reflects changes immediately without user needing to refresh the page

#### Scenario: Friendship notifications display in notification panel
- **WHEN** a friendship notification event (`FRIEND_REQUEST_RECEIVED` or `FRIEND_STATUS_CHANGED`) is received
- **THEN** the notification panel prepends a friendship notification entry with appropriate icon and action text

#### Scenario: Mention notifications appear only for targeted mentions
- **WHEN** a mention notification event is processed in the client
- **THEN** it corresponds to a targeted mentioned user and non-targeted participants do not receive mention-notification UI entries

#### Scenario: Notification panel does not overlap room list layout
- **WHEN** notification UI is opened while room list and chat shell are visible
- **THEN** panel and badges render in a stable overlay layer that does not clip behind or overlap room-list interactive regions

### Requirement: Notification realtime flow SHALL recover after websocket reconnect
The system SHALL reactivate notification handlers after websocket reconnect and trigger convergence fetch to resolve any missed events, while ensuring reconnect and convergence-fetch behavior remain bounded under persistent instability and suppressing repeated futile reconnect attempts when the websocket endpoint contract is invalid for the current session.

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

#### Scenario: Reconnect attempts remain bounded during persistent instability
- **WHEN** websocket repeatedly disconnects in a non-recoverable loop for the same session signature
- **THEN** client reconnect attempts are bounded by backoff and suppression rules rather than continuing at a constant fixed interval forever

#### Scenario: Reconnect churn does not cause unbounded notifications API calls
- **WHEN** websocket reconnect/open transitions occur repeatedly in short intervals
- **THEN** convergence fetch requests to `/api/v1/notifications` remain bounded by fetch dedupe and rate-limit controls

