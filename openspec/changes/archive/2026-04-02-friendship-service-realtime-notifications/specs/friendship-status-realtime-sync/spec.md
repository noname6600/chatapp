# friendship-status-realtime-sync Specification

## Purpose
Define realtime synchronization of friendship status across all clients showing a user, ensuring friendship relationships are consistently updated in the UI when status changes occur (acceptance, unfriend, block operations).

## Requirements

### Requirement: Friendship status changes SHALL be synchronized in realtime across all client instances
When a friendship relationship status changes, all connected clients of affected users SHALL immediately receive and reflect the updated status without requiring manual refresh.

#### Scenario: Friendship list updates when friend request is accepted
- **WHEN** user A accepts a pending friend request from user B
- **THEN** both user A's and user B's friendship lists immediately show each other as "friends" via realtime WebSocket notification

#### Scenario: Friendship list updates when user unfriends
- **WHEN** user A unfriends user B
- **THEN** both user A and user B receive a `FRIEND_STATUS_CHANGED` WebSocket message and their friendship lists are updated to remove each other as friends

#### Scenario: Friend is added to blocked list realtime
- **WHEN** user A blocks user B
- **THEN** user A's client receives a `FRIEND_STATUS_CHANGED` notification and updates the friendship status to blocked


#### Scenario: Blocked user status syncs across devices
- **WHEN** user A blocks user B on device 1
- **THEN** all of user B's connected devices receive notification that they have been blocked by user A

#### Scenario: Unblock operation syncs realtime
- **WHEN** user A unblocks user B
- **THEN** both user A and user B receive a `FRIEND_STATUS_CHANGED` notification indicating the friendship is no longer blocked

### Requirement: Friendship status messages SHALL contain actionable state information
Each friendship status change notification SHALL include the new status and sufficient context to update UI representations without additional API calls.

#### Scenario: Status message includes new friendship state
- **WHEN** a friendship status change notification is sent via WebSocket
- **THEN** the message includes newStatus (ACCEPTED, UNFRIENDED, BLOCKED, UNBLOCKED) and userIds of both parties

#### Scenario: Status message indicates action initiator
- **WHEN** a friendship status change occurs
- **THEN** the WebSocket message includes actionUserId to identify which user performed the action

### Requirement: Friendship status updates SHALL be consistent across simultaneous operations
If multiple clients of the same user attempt simultaneous status changes, the system SHALL ensure eventual consistency and prevent conflicting state.

#### Scenario: Last-write-wins applied to simultaneous status changes
- **WHEN** user A has two connected clients that both attempt to change friendship status with user B simultaneously
- **THEN** one change wins consistently, and both clients converge to the same final state via WebSocket updates

### Requirement: Friendship status synchronization SHALL recover after connection loss
When a client reconnects after losing WebSocket connection, it SHALL fetch the current friendship status to resolve missed updates.

#### Scenario: Disconnected client syncs status on reconnection
- **WHEN** user A's WebSocket disconnects for 30 seconds, during which user B unfriends them
- **THEN** upon reconnection, user A's client fetches current friendship status from REST API and displays the updated relationship

#### Scenario: In-flight status change is applied after reconnect
- **WHEN** a user initiates a status change (e.g., unblock) during WebSocket disconnect
- **THEN** the REST API operation succeeds, and upon reconnect, the WebSocket message is delivered reflecting the new status
