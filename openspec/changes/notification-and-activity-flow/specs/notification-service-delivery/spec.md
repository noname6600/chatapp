## ADDED Requirements

### Requirement: Notification-service SHALL persist and deliver notifications for MESSAGE, MENTION, and FRIEND_REQUEST events
The notification-service SHALL consume domain events from Kafka, persist a `Notification` record per recipient user, and push a real-time delivery to each connected user over WebSocket.

#### Scenario: Message notification created for all room members except sender
- **WHEN** notification-service consumes a `MessageCreatedEvent` from `chat.message.created`
- **THEN** one `Notification` record with type `MESSAGE` and `referenceId = messageId` is persisted for each room member excluding the sender

#### Scenario: Notification delivered over WebSocket to connected recipient
- **WHEN** a `Notification` record is persisted for a user
- **THEN** notification-service pushes a `NOTIFICATION_NEW` WebSocket event to `/user/{userId}/queue/notifications` containing the notification payload

#### Scenario: Notification survives reconnect via REST endpoint
- **WHEN** a user reconnects and calls `GET /api/v1/notifications`
- **THEN** the response includes up to 50 most-recent unread and read notifications ordered by `createdAt` descending

#### Scenario: Marking a single notification read
- **WHEN** client sends `POST /api/v1/notifications/{id}/read`
- **THEN** the notification's `isRead` field is set to true and the updated record is returned

#### Scenario: Marking all notifications read
- **WHEN** client sends `POST /api/v1/notifications/read-all`
- **THEN** all unread notifications for the authenticated user are set to `isRead = true`

#### Scenario: Unread notification count is included in notification list response
- **WHEN** client calls `GET /api/v1/notifications`
- **THEN** the response body includes an `unreadCount` field with the count of `isRead = false` records

### Requirement: Notification records SHALL be capped per user to prevent unbounded growth
The notification-service SHALL enforce a maximum of 200 notification records per user by deleting the oldest records when the limit is exceeded on insert.

#### Scenario: Oldest notifications trimmed on cap breach
- **WHEN** inserting a new notification exceeds the 200-record limit for a user
- **THEN** the oldest notification records beyond the cap are deleted before or after insert so total stays at 200
