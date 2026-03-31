## Why

The app currently has no end-to-end notification system: users who are not on a room page receive no signal that messages arrived, friend requests go unacknowledged until navigated to, @mentions are invisible, and room lists show no activity ordering. This change wires together the notification-service, presence, and chat surfaces into a complete, real-time notification experience.

## What Changes

- **Message notifications**: Every persisted message fans out a notification to all room members who are NOT the sender; notification is delivered over WebSocket and stored persistently so it survives refresh.
- **Room list activity sort**: Rooms are re-ordered in the sidebar list by latest-message timestamp, updating in real-time when new messages arrive.
- **Friend-request notifications**: Accepting or sending a friend request generates a persisted notification delivered to the target user in real-time.
- **@mention flow**: Detecting `@username` tokens in message body during send; mentioned users receive a dedicated mention-notification type in the notification inbox.
- **Group chat mute setting**: Per-user, per-room mute toggle stored server-side; muted rooms suppress notification toasts and badge increments while still persisting notification records.
- **Private chat notification icon**: Private (1-1) chat rooms display an unread/notification badge icon in the room list entry, consistent with group rooms.

## Capabilities

### New Capabilities
- `notification-service-delivery`: Backend notification service — persist, query, mark-read, and broadcast notification events (MESSAGE, MENTION, FRIEND_REQUEST types) via Kafka + WebSocket to connected clients.
- `room-list-activity-sort`: Room sidebar list is sorted descending by the timestamp of the latest message; sort updates live on incoming WebSocket message events.
- `mention-notifications`: Token detection of `@username` during message send; creation of MENTION notification records for each target user; client rendering of mention notifications in the inbox with deep-link to message.
- `friend-request-notifications`: Friendship-service emits a Kafka event when a friend request is sent or accepted; notification-service consumes it and delivers a FRIEND_REQUEST notification to the target user.
- `room-notification-settings`: Per-user per-room mute toggle (group rooms only); persisted server-side; muted rooms suppress toast and badge increments on the client while still storing notification records.
- `private-chat-notification-badge`: Private chat rooms display a notification/unread badge icon in the room list entry using the same unread-count infrastructure as group rooms.

### Modified Capabilities
- `room-unread-realtime-sync`: Add muted-room behavior — a room that is muted by the current user does not increment the notification badge even when a new unread WebSocket event arrives; the unread count record still updates.
- `message-sending`: Mention token scanning (`@username`) is performed in the send payload pipeline before dispatching to chat-service, adding `mentionedUserIds` to the message create request.

## Impact

- **notification-service** (BE): New Kafka consumers for `chat.message.created` and `friendship.request.events`; new REST endpoints `GET /api/v1/notifications`, `POST /api/v1/notifications/{id}/read`, `POST /api/v1/notifications/read-all`; new `Notification` JPA entity with type enum.
- **chat-service** (BE): `MessageCreateRequest` gains `mentionedUserIds[]`; `MessageQueryService` publishes enriched event including mentions.
- **friendship-service** (BE): Emits `FriendRequestEvent` to Kafka on send/accept.
- **FE notification store**: New Zustand-like context `NotificationProvider`; WebSocket event handler for `NOTIFICATION_*` event types; badge count managed globally.
- **FE room list**: Sort comparator on `latestMessageAt`; real-time re-sort on `ws_message_sent_event`.
- **FE settings panel**: Mute toggle component inside group room settings drawer.
- **common-events** (shared): New event types `NotificationEvent`, `MentionEvent`, `FriendRequestEvent`.
