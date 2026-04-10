# notification-realtime-sync Specification (DELTA)

## MODIFIED Requirements

### Requirement: Notification UI state SHALL update immediately from realtime websocket events
The system SHALL update notification bell count and notification panel immediately when realtime events are received, while preserving mention-targeted semantics and stable non-overlapping layout behavior.

**MODIFIED to include**: The notification system SHALL handle friendship-related notifications (friend requests, status changes) in addition to existing message mention and activity notifications.

#### Scenario: Bell count increments on new notification event
- **WHEN** a realtime notification event is received from the websocket (including friendship notifications)
- **THEN** notification bell count increments immediately in the UI

#### Scenario: Notification panel prepends new notification from realtime event
- **WHEN** a notification event is received via realtime websocket (including FRIEND_REQUEST_RECEIVED, FRIEND_STATUS_CHANGED) and notification panel is open
- **THEN** new notification is prepended to the notification panel without requiring manual refresh

#### Scenario: Realtime notification updates do not require page refresh
- **WHEN** notification updates arrive via realtime websocket (including friendship notifications)
- **THEN** UI reflects changes immediately without user needing to refresh the page

#### Scenario: Mention notifications appear only for targeted mentions
- **WHEN** a mention notification event is processed in the client
- **THEN** it corresponds to a targeted mentioned user and non-targeted participants do not receive mention-notification UI entries

#### Scenario: Notification panel does not overlap room list layout
- **WHEN** notification UI is opened while room list and chat shell are visible
- **THEN** panel and badges render in a stable overlay layer that does not clip behind or overlap room-list interactive regions

#### Scenario: Friendship notifications display with appropriate icon and message
- **WHEN** a friendship notification (friend request or status change) is received and displayed in the notification panel
- **THEN** it displays with a friendship-specific icon, sender name, and action description (e.g., "User X sent you a friend request")

### Requirement: Notification realtime flow SHALL recover after websocket reconnect
The system SHALL reactivate notification handlers after websocket reconnect and trigger convergence fetch to resolve any missed events, including friendship notifications.

#### Scenario: Notification handler active after websocket reconnect
- **WHEN** websocket connection is reestablished after a disconnect
- **THEN** notification event handler is reactivated to process future realtime events (including friendship events)

#### Scenario: Reconnect triggers convergence fetch for missed notifications
- **WHEN** websocket reconnect completes
- **THEN** system triggers a convergence fetch to retrieve notification state from backend and reconcile any events missed during the disconnect period

#### Scenario: Missed friendship notifications are fetched on reconnect
- **WHEN** a client reconnects after WebSocket loss and the user missed friend requests during disconnection
- **THEN** the client fetches pending friend requests and updates both the unread badge and notification panel
