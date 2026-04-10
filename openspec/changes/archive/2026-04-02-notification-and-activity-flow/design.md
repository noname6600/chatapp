## Context

The app has a `notification-service` module already scaffolded in the backend monorepo but currently has no domain logic, no Kafka consumers, and no REST endpoints. The chat-service publishes `MessageCreatedEvent` to Kafka topic `chat.message.created`. The friendship-service has no Kafka publishing yet. The frontend has no notification context, no notification bell, and no @mention token parsing.

Current unread-count infrastructure (`room-unread-realtime-sync`) is frontend-only in-memory state from WebSocket events — it is not persisted. The new notification system adds a persistent notification record layer on top of the existing ephemeral unread-count layer.

## Goals / Non-Goals

**Goals:**
- Deliver persisted, real-time notifications for MESSAGE, MENTION, and FRIEND_REQUEST event types
- Room list sorted by latest-message activity in real-time
- Per-room mute toggle (group rooms) suppressing badge/toast but not persistence
- Private chat rooms show the same unread badge as group rooms
- @mention token detection in the send pipeline with dedicated mention notification type

**Non-Goals:**
- Push notifications (browser `Notification` API / FCM) — out of scope for this change
- Email or SMS notification delivery
- Notification preferences beyond mute/unmute (e.g., granular per-event-type toggles)
- Read receipts or delivery receipts per message
- Pagination of the notification inbox beyond latest 50

## Decisions

### Decision 1: Notification fan-out via existing Kafka topic, not new HTTP calls
`notification-service` subscribes to `chat.message.created` (already published by chat-service) and `friendship.request.events` (new). Alternatives: (a) direct service-to-service HTTP calls — rejected, creates coupling and latency under load; (b) new notification topic — rejected, duplicates the event; (c) in-process coupling — rejected, breaks service boundaries.

### Decision 2: Single `notifications` table with polymorphic `type` enum
One JPA entity `Notification { id, userId, type (MESSAGE|MENTION|FRIEND_REQUEST), referenceId, roomId, isRead, createdAt }`. Alternatives: separate tables per type — rejected, adds join complexity; JSON blob payload — rejected, harder to query/index.

### Decision 3: Mention detection server-side in chat-service, not exclusively client-side
`MessageCreateRequest` carries `mentionedUserIds[]` populated by the client after parsing `@username` tokens. Chat-service validates they are actual room members and passes them in the enriched `MessageCreatedEvent`. Notification-service then creates MENTION records for each. Alternative: notification-service re-parses message text — rejected, requires user-lookup in notification-service, and message format could change; purely client-side — rejected, no persistence guarantee.

### Decision 4: Mute settings stored server-side in `room_mute_settings` table
`RoomMuteSettings { userId, roomId, mutedAt }` in notification-service (or a new `user-settings-service` field). Client reads mute state on room load via `GET /api/v1/rooms/{roomId}/settings` and caches in store. Alternatives: localStorage — rejected, not synced across devices; in notification-service preferences table — chosen for locality.

### Decision 5: Room list sort is purely client-side, driven by existing `latestMessageAt` field
`latestMessageAt` is already returned by the room list API. Sorting is a frontend comparator applied to the room store array, re-triggered on every `ws_message_sent_event`. Alternatives: server-side sorted pagination — deferred (requires backend cursor change, out of scope for now).

### Decision 6: Notification delivery to FE via WebSocket, same STOMP broker
Notification-service publishes a `NotificationEvent` to `notification.events` Kafka topic; a WebSocket gateway (reuse existing gateway or notification-service's own) subscribes and pushes to `/user/{userId}/queue/notifications`. Alternatives: Server-Sent Events — rejected, requires additional server infrastructure; polling — rejected, latency and chattiness.

### Decision 7: Notification bell badge count = unread notification records, independent of room unread counts
Room-level unread counts and notification-inbox unread counts are separate concerns. The bell badge = count of `Notification` records where `isRead=false`. Room unread badge = existing ephemeral + persisted unread mechanism. They are additive, not shared.

## Risks / Trade-offs

- **[Risk] Notification fan-out latency** → Kafka consumer lag could delay notification delivery under burst load. Mitigation: consumer group with multiple partitions; tune `max.poll.records`.
- **[Risk] Mention list spoofing** → Client submits arbitrary `mentionedUserIds`; notification-service could create notifications for non-members. Mitigation: chat-service validates memberIds before embedding in event.
- **[Risk] Mute state staleness** → If mute toggle write fails silently, client and server diverge. Mitigation: optimistic update with server rollback; always re-read on room activation.
- **[Risk] Room sort thrash** → High-traffic rooms constantly re-sort the entire room list, causing re-render flicker. Mitigation: debounce re-sort by 300 ms; use stable sort with key comparison; only move room to top if it wasn't already there.
- **[Risk] Notification table growth** → No cleanup strategy. Mitigation: soft-cap to 200 records per user via insert-then-trim; add a cleanup job (deferred).
- **[Trade-off] Mention detection is client-driven** → Server has authoritative list of valid usernames but the client does the `@name` token scanning. If a client sends wrong IDs the validation pass catches it; if the client omits scanning, mentions are silently lost. Accepted for now; a future server-side re-parse could be added.

## Migration Plan

1. Deploy `notification-service` with new DB schema (`notifications`, `room_mute_settings`).
2. Deploy updated `chat-service` with `mentionedUserIds` in `MessageCreateRequest` and enriched `MessageCreatedEvent` (field is optional/nullable — backwards compatible).
3. Deploy updated `friendship-service` with Kafka publish (purely additive).
4. Deploy updated API Gateway routes for `/api/v1/notifications/**` and `/api/v1/rooms/{id}/settings`.
5. Deploy FE with notification store, bell component, mute toggle, activity sort, and mention parser.

Rollback: FE can be reverted independently. BE service changes are additive except the notification-service Kafka consumers which can be disabled by removing the consumer group without affecting other services.

## Open Questions

- Should MENTION notifications suppress the redundant MESSAGE notification for the same message for the mentioned user, or both should appear? (Current plan: only MENTION created for mentioned users, no extra MESSAGE notification for them.)
- Should the notification inbox be a dedicated page or a dropdown panel? (Current plan: dropdown panel accessible from bell icon.)
- Max notification history retention period — 30 days? TBD with product.
