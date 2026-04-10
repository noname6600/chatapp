## ADDED Requirements

### Requirement: Sending a friend request SHALL generate a FRIEND_REQUEST notification for the recipient
When a user sends a friend request, the friendship-service SHALL emit a Kafka event and the notification-service SHALL deliver a FRIEND_REQUEST notification to the intended recipient in real-time.

#### Scenario: Friendship-service emits event on friend request send
- **WHEN** user A sends a friend request to user B via `POST /api/v1/friendships/request`
- **THEN** friendship-service publishes a `FriendRequestEvent { senderId, recipientId, type: SENT }` to the `friendship.request.events` Kafka topic

#### Scenario: Notification-service creates FRIEND_REQUEST notification
- **WHEN** notification-service consumes a `FriendRequestEvent` with `type = SENT`
- **THEN** one `Notification` record with type `FRIEND_REQUEST` and `referenceId = senderId` is persisted for the `recipientId`

#### Scenario: Friend request notification delivered in real-time
- **WHEN** the FRIEND_REQUEST notification is persisted
- **THEN** a `NOTIFICATION_NEW` WebSocket event is pushed to the recipient's notification queue

#### Scenario: Accepting a friend request generates a reciprocal notification
- **WHEN** user B accepts user A's friend request via `POST /api/v1/friendships/{requestId}/accept`
- **THEN** friendship-service publishes a `FriendRequestEvent { senderId: B, recipientId: A, type: ACCEPTED }` and notification-service creates a FRIEND_REQUEST_ACCEPTED notification for user A

#### Scenario: Duplicate friend request does not create duplicate notification
- **WHEN** a `FriendRequestEvent` is consumed but a FRIEND_REQUEST notification for the same sender-recipient pair already exists and is unread
- **THEN** no new notification record is created (idempotent on unread existing record)
