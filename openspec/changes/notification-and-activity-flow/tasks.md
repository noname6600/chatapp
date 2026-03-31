## 1. Backend: common-events — New event types

- [x] 1.1 Add `NotificationEvent` class to `common-events` with fields: `notificationId`, `userId`, `type` (enum: MESSAGE, MENTION, FRIEND_REQUEST, FRIEND_REQUEST_ACCEPTED), `referenceId`, `roomId`, `senderName`, `preview`, `createdAt`
- [x] 1.2 Add `FriendRequestEvent` class to `common-events` with fields: `senderId`, `recipientId`, `requestId`, `type` (enum: SENT, ACCEPTED)
- [x] 1.3 Update `MessageCreatedEvent` in `common-events` to include `mentionedUserIds: List<UUID>`

## 2. Backend: chat-service — Mention support in send pipeline

- [x] 2.1 Add `mentionedUserIds: List<UUID>` field to `MessageCreateRequest` DTO (nullable/optional for backwards compatibility)
- [x] 2.2 In `MessageCommandService.createMessage`, validate that each userId in `mentionedUserIds` is a member of the room; drop invalid IDs silently
- [x] 2.3 Populate `mentionedUserIds` on the published `MessageCreatedEvent`
- [x] 2.4 Write unit test: `createMessage_withMentions_dropsNonMemberIds`

## 3. Backend: friendship-service — Kafka event publishing

- [x] 3.1 Add Kafka producer configuration to `friendship-service` (topic: `friendship.request.events`)
- [x] 3.2 Publish `FriendRequestEvent { type: SENT }` in `FriendshipCommandService.sendRequest` after persisting the request
- [x] 3.3 Publish `FriendRequestEvent { type: ACCEPTED }` in `FriendshipCommandService.acceptRequest` after persisting the acceptance
- [x] 3.4 Write unit tests: `sendRequest_publishesFriendRequestEvent`, `acceptRequest_publishesFriendRequestAcceptedEvent`

## 4. Backend: notification-service — Domain model and persistence

- [x] 4.1 Create `Notification` JPA entity: `id (UUID)`, `userId (UUID)`, `type (enum)`, `referenceId (UUID)`, `roomId (UUID nullable)`, `senderName (String)`, `preview (String)`, `isRead (boolean default false)`, `createdAt`
- [x] 4.2 Create `RoomMuteSetting` JPA entity: `userId (UUID)`, `roomId (UUID)`, `mutedAt`; composite PK `(userId, roomId)`
- [x] 4.3 Create `NotificationRepository` and `RoomMuteSettingRepository` JPA repositories
- [x] 4.4 Create `NotificationQueryService.getNotificationsForUser(userId)` returning latest 50 records with `unreadCount`
- [x] 4.5 Create `NotificationCommandService`: `createNotification`, `markRead(notificationId, userId)`, `markAllRead(userId)`, `trimToLimit(userId, max=200)`
- [x] 4.6 Write unit tests for `NotificationCommandService`: mark-read ownership check, trim-to-limit keeps newest 200

## 5. Backend: notification-service — Kafka consumers

- [x] 5.1 Create `MessageCreatedEventConsumer` subscribing to `chat.message.created`; for each non-sender room member create MESSAGE notification (or MENTION if userId in `mentionedUserIds`, skipping duplicate MESSAGE for same user)
- [x] 5.2 Create `FriendRequestEventConsumer` subscribing to `friendship.request.events`; create FRIEND_REQUEST or FRIEND_REQUEST_ACCEPTED notification; enforce idempotency check for SENT type
- [x] 5.3 Write integration tests: `MessageCreatedEventConsumer_createsMessageNotificationsExcludingSender`, `MessageCreatedEventConsumer_createsMentionNotificationInsteadOfMessageForMentionedUser`
- [x] 5.4 Write integration test: `FriendRequestEventConsumer_skipsIfUnreadAlreadyExists`

## 6. Backend: notification-service — REST endpoints

- [x] 6.1 Create `NotificationController` with `GET /api/v1/notifications` (returns list + unreadCount, JWT-authenticated)
- [x] 6.2 Add `POST /api/v1/notifications/{id}/read` endpoint (marks single notification read; 403 if not owner)
- [x] 6.3 Add `POST /api/v1/notifications/read-all` endpoint
- [x] 6.4 Create `RoomMuteController` with `POST /api/v1/rooms/{roomId}/mute` (creates mute record) and `DELETE /api/v1/rooms/{roomId}/mute` (removes mute record)
- [x] 6.5 Add `GET /api/v1/rooms/{roomId}/settings` endpoint returning `{ isMuted: boolean }` for the authenticated user
- [x] 6.6 Write controller integration tests for all five endpoints (happy path + 403/404 error cases)

## 7. Backend: notification-service — WebSocket delivery

- [x] 7.1 Add STOMP WebSocket configuration to notification-service (or reuse shared gateway — confirm from gateway routing config)
- [x] 7.2 Create `NotificationWebSocketPublisher` that sends `NOTIFICATION_NEW` event to `/user/{userId}/queue/notifications` after each notification is persisted
- [x] 7.3 Write test: `NotificationWebSocketPublisher_sendsToCorrectUserDestination`

## 8. Frontend: notification store

- [x] 8.1 Create `NotificationProvider` context in `chatappFE/src/store/notification.store.tsx` with state: `notifications: Notification[]`, `unreadCount: number`, `mutesByRoom: Record<string, boolean>`
- [x] 8.2 Add `fetchNotifications()` action calling `GET /api/v1/notifications`; populate `notifications` and `unreadCount`
- [x] 8.3 Add `markRead(id)` and `markAllRead()` actions wiring to corresponding REST endpoints
- [x] 8.4 Register WebSocket handler for `NOTIFICATION_NEW` event: prepend to `notifications`, increment `unreadCount` (skip badge increment if room is muted and type is MESSAGE/MENTION)
- [x] 8.5 Add `fetchRoomMute(roomId)` and `toggleRoomMute(roomId, muted)` actions calling mute endpoints; update `mutesByRoom`
- [x] 8.6 Write unit tests: `notification store increments unreadCount on NOTIFICATION_NEW`, `muted room does not increment bell badge`

## 9. Frontend: notification bell UI

- [x] 9.1 Create `NotificationBell` component in `chatappFE/src/components/` with a bell icon and badge showing `unreadCount` (cap display at "99+")
- [x] 9.2 Create `NotificationPanel` dropdown/drawer listing notifications with type icon, preview text, timestamp, and read/unread styling
- [x] 9.3 Wire "mark all read" button in `NotificationPanel`
- [x] 9.4 Clicking a MESSAGE or MENTION notification deep-links to the relevant room (navigate + setActiveRoom)
- [x] 9.5 Mount `NotificationBell` in the app header/navbar; call `fetchNotifications()` on mount

## 10. Frontend: @mention autocomplete in message input

- [x] 10.1 Detect `@` trigger in `MessageInput` (or equivalent composer); when `@` + at least one char is typed, filter current room members by display name prefix
- [x] 10.2 Render `MentionAutocomplete` dropdown (max 5 results) positioned above the input field
- [x] 10.3 On selection, replace the `@partial` token with `@username` and track the resolved userId in a local `pendingMentions: {userId, username}[]` map
- [x] 10.4 On message send, extract `mentionedUserIds` from `pendingMentions` and include in the request; clear `pendingMentions` after send
- [x] 10.5 Write unit tests: `@mention autocomplete filters room members`, `send payload includes mentionedUserIds`

## 11. Frontend: room list activity sort

- [x] 11.1 In the room store, add `latestMessageAt: string` field to the `Room` type (sourced from API response)
- [x] 11.2 Add sort comparator in room list selector: sort by `latestMessageAt` descending, fallback to `createdAt`
- [x] 11.3 On `ws_message_sent_event`, update `latestMessageAt` for the affected room and debounce re-sort by 300 ms
- [x] 11.4 Write unit test: `room list re-sorts when message arrives for background room`

## 12. Frontend: mute toggle in group room settings

- [x] 12.1 Add mute toggle switch to the group room settings panel/drawer, using `mutesByRoom[roomId]` from notification store
- [x] 12.2 On toggle change, call `toggleRoomMute(roomId, newMuted)`; show optimistic update immediately, revert on error
- [x] 12.3 On room activation, call `fetchRoomMute(roomId)` to ensure local state is fresh
- [x] 12.4 Hide mute toggle for private (1-1) rooms

## 13. Frontend: private chat notification badge

- [x] 13.1 Verify `unreadCount` field is populated for private chat rooms in the room list API response
- [x] 13.2 Render unread badge on private chat room list entries using the same badge component as group rooms
- [x] 13.3 Write unit test: `private chat room item shows badge when unreadCount > 0`

## 14. Validation

- [x] 14.1 Run FE test suite (`npm test`) — all tests pass
- [x] 14.2 Run BE notification-service tests (`gradlew :notification-service:test`) — all pass
- [ ] 14.3 Manual end-to-end: user A sends message → user B (not on room page) receives notification bell increment
- [ ] 14.4 Manual end-to-end: user A sends `@userB` message → userB receives MENTION notification with deep-link
- [ ] 14.5 Manual end-to-end: user A sends friend request to user B → user B receives notification; acceptance triggers reciprocal notification
- [ ] 14.6 Manual end-to-end: user mutes room → new messages do not increment badge or show toast; unmute restores behavior
- [ ] 14.7 Manual end-to-end: room list re-orders when a background room receives a new message
