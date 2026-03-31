# notification-realtime-sync Specification

## Purpose
Define deterministic realtime notification UI behavior, including immediate bell count and notification panel updates from websocket events, and recovery mechanisms after websocket reconnect.
## Requirements
### Requirement: Notification UI state SHALL update immediately from realtime websocket events
The system SHALL update notification bell count and notification panel immediately when realtime events are received, while preserving mention-targeted semantics and stable non-overlapping layout behavior.

#### Scenario: Bell count increments on new notification event
- **WHEN** a realtime notification event is received from the websocket
- **THEN** notification bell count increments immediately in the UI

#### Scenario: Notification panel prepends new notification from realtime event
- **WHEN** a notification event is received via realtime websocket and notification panel is open
- **THEN** new notification is prepended to the notification panel without requiring manual refresh

#### Scenario: Realtime notification updates do not require page refresh
- **WHEN** notification updates arrive via realtime websocket
- **THEN** UI reflects changes immediately without user needing to refresh the page

#### Scenario: Mention notifications appear only for targeted mentions
- **WHEN** a mention notification event is processed in the client
- **THEN** it corresponds to a targeted mentioned user and non-targeted participants do not receive mention-notification UI entries

#### Scenario: Notification panel does not overlap room list layout
- **WHEN** notification UI is opened while room list and chat shell are visible
- **THEN** panel and badges render in a stable overlay layer that does not clip behind or overlap room-list interactive regions

### Requirement: Notification realtime flow SHALL recover after websocket reconnect
The system SHALL reactivate notification handlers after websocket reconnect and trigger convergence fetch to resolve any missed events.

#### Scenario: Notification handler active after websocket reconnect
- **WHEN** websocket connection is reestablished after a disconnect
- **THEN** notification event handler is reactivated to process future realtime events

#### Scenario: Reconnect triggers convergence fetch for missed notifications
- **WHEN** websocket reconnect completes
- **THEN** system triggers a convergence fetch to retrieve notification state from backend and reconcile any events missed during the disconnect period

