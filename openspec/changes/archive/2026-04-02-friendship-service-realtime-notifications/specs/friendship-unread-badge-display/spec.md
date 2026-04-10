# friendship-unread-badge-display Specification

## Purpose
Define UI representation and realtime updates of unread friend request badges, allowing users to quickly see pending friend requests at a glance and have the count update immediately when new requests arrive or are processed.

## Requirements

### Requirement: Unread friend request badge SHALL display unread count
The system SHALL display a numeric badge showing the count of unread (pending) friend requests, positioned prominently in the navigation UI where it is immediately visible.

#### Scenario: Badge displays pending friend request count on page load
- **WHEN** user loads the application
- **THEN** a badge component displays the number of pending friend requests received by the user (fetched from GET `/api/friends/unread-count`)

#### Scenario: Badge counts only pending (not accepted or declined) requests
- **WHEN** a user has 3 pending requests, 1 accepted, and 1 declined request
- **THEN** the badge displays "3", counting only pending friend requests

#### Scenario: Badge is hidden when unread count is zero
- **WHEN** a user has no pending friend requests
- **THEN** the badge is not displayed (or displays an empty state)

### Requirement: Badge count SHALL update in realtime when friend requests arrive or are processed
Badge numbers SHALL increment or decrement immediately via WebSocket events without requiring page refresh.

#### Scenario: Badge increments when friend request is received
- **WHEN** a user receives a new friend request via realtime WebSocket (`FRIEND_REQUEST_RECEIVED`)
- **THEN** the unread badge count immediately increments by 1

#### Scenario: Badge decrements when request is accepted
- **WHEN** a user accepts a pending friend request via WebSocket (`FRIEND_REQUEST_ACCEPTED`)
- **THEN** the badge count immediately decrements by 1

#### Scenario: Badge decrements when request is declined
- **WHEN** a user declines a pending friend request via WebSocket
- **THEN** the badge count immediately decrements by 1

#### Scenario: Badge decrements when request is cancelled by sender
- **WHEN** a user receives a cancellation event for a pending request they were shown
- **THEN** the badge count immediately decrements by 1

### Requirement: Badge state SHALL be consistent across multiple browser tabs/devices
If a user has multiple browser tabs or devices connected, badge counts SHALL remain synchronized.

#### Scenario: Badge syncs across tabs when request received on different tab
- **WHEN** user A receives a friend request in browser tab 1, and has browser tab 2 open
- **THEN** within 100ms, both tab 1 and tab 2 show the incremented unread count

#### Scenario: Badge syncs across devices
- **WHEN** user A receives a friend request on mobile while desktop is connected
- **THEN** both mobile and desktop clients receive the `FRIEND_REQUEST_RECEIVED` WebSocket message and update their badges consistently

### Requirement: Badge updates SHALL be resilient to WebSocket disconnections
Badge state SHALL recover and re-synchronize correctly after temporary network loss or WebSocket disconnect.

#### Scenario: Badge re-syncs after WebSocket reconnect
- **WHEN** a user's WebSocket disconnects and reconnects
- **THEN** the client fetches the current unread friend request count from the REST API and updates the badge

#### Scenario: Requests received during disconnection are reflected after reconnect
- **WHEN** user A receives 2 friend requests while WebSocket is disconnected
- **THEN** upon reconnection, the badge is updated to reflect the new count and the client can fetch pending requests

### Requirement: Badge click/interaction SHALL navigate to friend requests view
Clicking the badge or associated button SHALL take the user to a view displaying pending friend requests.

#### Scenario: Badge click opens friend requests list
- **WHEN** user clicks on the unread friend request badge
- **THEN** the application navigates to a friend requests view displaying all pending requests with accept/decline actions
