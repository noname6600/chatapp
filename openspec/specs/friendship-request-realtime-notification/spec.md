# friendship-request-realtime-notification Specification

## Purpose
Define realtime delivery of friend request notifications via WebSocket, ensuring recipients are notified immediately when friend requests arrive, and requesters receive confirmation of request dispatch.

## Requirements

### Requirement: Friend request notifications SHALL be delivered realtime via WebSocket
The system SHALL deliver friend request events (sent, received, accepted, declined) in realtime through WebSocket connections, maintaining order and consistency for affected users.

#### Scenario: Recipient receives notification when friend request arrives
- **WHEN** user A sends a friend request to user B
- **THEN** user B's client receives a `FRIEND_REQUEST_RECEIVED` WebSocket message containing sender information (user A's ID, name, avatar) within 100ms

#### Scenario: Requester receives confirmation when request is sent
- **WHEN** user A successfully submits a friend request to user B via REST API
- **THEN** user A's client receives a `FRIEND_REQUEST_SENT` WebSocket message confirming the request ID and recipient information

#### Scenario: Both users notified when request is accepted
- **WHEN** user B accepts a pending friend request from user A
- **THEN** both user A and user B receive a `FRIEND_REQUEST_ACCEPTED` WebSocket message with the friendship relationship details

#### Scenario: Both users notified when request is declined
- **WHEN** user B declines a pending friend request from user A
- **THEN** both user A and user B receive a `FRIEND_REQUEST_DECLINED` WebSocket message indicating the request ID and action user

#### Scenario: Recipient notified when request is cancelled
- **WHEN** user A cancels a pending friend request they sent to user B
- **THEN** user B receives a `FRIEND_REQUEST_CANCELLED` WebSocket message indicating the cancellation by user A

### Requirement: Friend request WebSocket message payload SHALL contain required identification and context
Each friend request notification message SHALL include sufficient information to identify the affected users, the action taken, and the request itself.

#### Scenario: Message payload contains user identification
- **WHEN** a friend request notification is sent via WebSocket
- **THEN** the message payload includes senderId, recipientId (or actionUserId), and optional user details (name, avatar URL)

#### Scenario: Message payload indicates request state and action
- **WHEN** a friend request WebSocket message is sent
- **THEN** the payload includes the current friendship status, the specific action type (sent/received/accepted/declined), and request timestamp

### Requirement: Friend request notifications SHALL only reach affected users
Notifications for a specific friend request SHALL only be delivered to users directly involved (requester and recipient), not broadcast to all connected clients.

#### Scenario: Only requester and recipient receive request notification
- **WHEN** a friend request event occurs between user A and user B
- **THEN** only user A and user B receive the corresponding WebSocket notification

#### Scenario: Third parties do not receive unrelated friend requests
- **WHEN** user A sends a friend request to user B
- **THEN** user C (not involved in the request) does not receive this notification

### Requirement: Friend request notifications SHALL persist delivery semantics across reconnection
The system SHALL ensure friend request notifications can be recovered if a client disconnects and reconnects.

#### Scenario: Friend request received during disconnect is visible on reconnect
- **WHEN** user B receives a friend request while disconnected, then reconnects
- **THEN** user B's client fetches pending friend requests from the REST API and displays them

#### Scenario: WebSocket reconnect restores future request notifications
- **WHEN** a client reconnects after WebSocket loss
- **THEN** the WebSocket listener is re-registered and future friend request events are delivered