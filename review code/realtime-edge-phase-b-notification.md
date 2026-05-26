# Realtime Edge Phase B - Notification Migration

**Status:** Implemented for edge-routing slice, not cut over to production traffic  
**Date:** May 12, 2026  
**Scope:** Notification migration only

---

## 1. What Was Implemented in Edge

### 1.1 Notification command routing

The edge service now accepts notification commands on the unified websocket path and forwards them to notification-service over HTTP.

**Edge components added or updated:**
- [RealtimeClientMessage.java](../chatappBE/realtime-edge-service/src/main/java/com/example/realtime/protocol/RealtimeClientMessage.java)
- [CommandDispatcher.java](../chatappBE/realtime-edge-service/src/main/java/com/example/realtime/routing/CommandDispatcher.java)
- [INotificationCommandRouter.java](../chatappBE/realtime-edge-service/src/main/java/com/example/realtime/routing/command/INotificationCommandRouter.java)
- [NotificationCommandRequest.java](../chatappBE/realtime-edge-service/src/main/java/com/example/realtime/routing/command/NotificationCommandRequest.java)
- [RestNotificationCommandRouter.java](../chatappBE/realtime-edge-service/src/main/java/com/example/realtime/adapter/out/notification/RestNotificationCommandRouter.java)
- [RealtimeWebSocketHandler.java](../chatappBE/realtime-edge-service/src/main/java/com/example/realtime/adapter/in/websocket/RealtimeWebSocketHandler.java)

**Supported commands in Phase B:**
- `mark-read`
- `mark-all-read`
- `clear-room`
- `mark-read-by-room`

These are the only notification commands that are clearly modeled in the existing notification-service codebase today.

### 1.2 Notification subscription ownership

The edge keeps user-scoped notification subscriptions in the session registry.

**Current behavior:**
- User is authenticated during websocket handshake
- Session is registered under the authenticated user ID
- Session is auto-subscribed to `notification:{userId}` for notification fanout
- Explicit subscribe/unsubscribe support remains available through the existing generic websocket subscription flow

### 1.3 Notification event delivery

The edge now consumes the notification-service Redis payload shape directly and fans it out to subscribed websocket sessions.

**Edge delivery flow:**
- Redis channel: `realtime.notification.user.{userId}`
- Payload: `RealtimeWsEvent` JSON with `type` and `payload`
- Listener: `RedisEventListener`
- Delivery target: `NotificationRealtimeDeliveryService`
- Websocket fanout: subscribed sessions for `notification:{userId}`

### 1.4 Identity extraction

The edge continues to use the existing JWT handshake approach.

**Current identity path:**
- `JwtHandshakeInterceptor` decodes the token from the websocket handshake query parameter
- `userId` is extracted from the JWT and stored in session attributes
- The access token is forwarded to notification-service for HTTP commands

**Temporary limitation:**
- The edge relies on the existing handshake-token approach and does not invent a separate security layer for Phase B
- If the token is missing, the command is rejected rather than guessed or synthesized

### 1.5 Edge configuration

Edge configuration now enables the Redis listener and points notification routing at notification-service.

- `realtime.redis.listener.enabled=true`
- `services.notification.url=http://localhost:8086`

---

## 2. What Was Implemented in Notification-Service

### 2.1 Minimal HTTP command endpoint

A new HTTP endpoint was added to accept the Phase B command model from the edge.

**New controller:**
- [NotificationRealtimeCommandController.java](../chatappBE/notification-service/src/main/java/com/example/notification/controller/NotificationRealtimeCommandController.java)

**Endpoint:**
- `POST /api/v1/notifications/realtime/commands`

**Behavior:**
- Reuses the existing `NotificationCommandService`
- Uses the JWT-authenticated user from the request principal
- Supports the same command set the edge forwards
- Leaves existing REST and websocket behavior unchanged

### 2.2 Request model

**New DTO:**
- [NotificationRealtimeCommandRequest.java](../chatappBE/notification-service/src/main/java/com/example/notification/dto/NotificationRealtimeCommandRequest.java)

This request keeps the command surface intentionally small:
- `command`
- `notificationId`
- `roomId`
- `requestId`

### 2.3 Existing behavior preserved

No existing notification websocket handler was removed or replaced.

The following remain intact:
- `NotificationWebSocketHandler`
- `NotificationWebSocketPublisher`
- `RedisNotificationSubscriber`
- `NotificationController`
- Existing notification push and unread-count behavior

---

## 3. Actual Inbound Command Flow

The Phase B notification command path is now explicit.

```text
WebSocket client
  → realtime-edge-service /ws or /realtime
  → RealtimeWebSocketHandler
  → CommandDispatcher
  → RestNotificationCommandRouter
  → notification-service /api/v1/notifications/realtime/commands
  → NotificationRealtimeCommandController
  → NotificationCommandService
  → repository / push service
```

### Supported actions

- `mark-read` updates a single notification for the authenticated user
- `mark-all-read` marks all notifications for the authenticated user as read
- `clear-room` clears room notifications for the authenticated user
- `mark-read-by-room` marks room notifications as read for the authenticated user

### Assumptions

- The client sends the notification command in the websocket envelope using `domain=notification` or `type=notification.command`
- The payload includes the fields required by the command
- The authenticated websocket session already carries the user identity and access token from the JWT handshake

---

## 4. Actual Outbound Event Flow

Notification delivery now uses the existing Redis transport that notification-service already publishes to.

```text
notification-service
  → NotificationPushService
  → NotificationWebSocketPublisher
  → RedisNotificationPublisher
  → Redis channel: realtime.notification.user.{userId}
  → Realtime edge RedisEventListener
  → NotificationRealtimeDeliveryService
  → subscribed websocket sessions
  → client
```

### What the edge listener does

- Reads the Redis message from the notification channel
- Deserializes the notification event payload
- Extracts the target user ID from the channel name
- Delivers the event to sessions subscribed to that user-scoped notification channel

### Why this matches the current transport

Notification-service currently publishes notification realtime events to Redis, not Kafka, for websocket fanout. Phase B therefore consumes Redis directly in the edge rather than inventing a new delivery path.

---

## 5. Rollback Path

Rollback remains simple and does not require removing the existing notification websocket handler.

### Rollback options

1. Route websocket clients back to the current service-local notification endpoint.
2. Disable Phase B websocket commands in the edge dispatcher.
3. Leave notification-service HTTP command endpoint deployed but unused.

### What stays available during rollback

- The notification-service websocket handler remains intact
- Redis notification publication remains intact
- Existing notification REST endpoints remain intact
- Existing user-scoped fanout behavior remains intact

### Operational note

This is a reversible additive migration slice, not a cutover.

---

## 6. Remaining Gaps Before Production Cutover

Phase B is functionally wired, but it is not production-cutover ready yet.

### Remaining gaps

- The edge notification command contract is intentionally small and may need refinement if additional notification actions appear later
- The current edge handshake still relies on the existing token-in-query-param approach
- Load and soak testing have not yet proven that the notification command and Redis fanout path hold under real traffic
- The edge session registry is still in-memory, so multi-instance fanout would need a distributed registry before full cutover
- Other domains are intentionally not migrated yet, so the edge is only a partial delivery boundary at this stage

### What was intentionally not changed

- Presence migration was not touched
- Chat migration was not touched
- Friendship migration was not touched
- The notification websocket handler in notification-service was not removed
- No automatic traffic cutover was introduced

---

## 7. Conclusion

Phase B is now implemented as a real, minimal migration slice for notification delivery through `realtime-edge-service`.

The edge can now:
- accept authenticated websocket connections
- route notification commands to notification-service over HTTP
- consume notification Redis events
- fan out notification updates to the correct user sessions

The system remains rollback-friendly because the current service-local notification websocket handler and the existing notification-service realtime delivery path remain in place.

---

**Prepared By:** Realtime Edge Phase B Notification Slice  
**Last Updated:** May 12, 2026
