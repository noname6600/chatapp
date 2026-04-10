## Why

The friendship-service currently publishes events through Kafka but lacks realtime WebSocket integration to push immediate updates to connected clients. Users don't receive instant feedback when friend requests arrive, when requests are accepted, or when friendship status changes. This creates a disconnect between the backend event system and the UI, forcing users to refresh manually to see updated friendship statuses and unread friend request badges. Adding realtime capabilities will make the friendship experience feel responsive and interactive.

## What Changes

- **Add WebSocket integration** to friendship-service for realtime client communication
- **Implement friendship event broadcasting** to push friend request arrivals and acceptance to users (both requester and recipient)
- **Create friend request badge system** to display unread friend request count in the UI
- **Add Kafka event consumption** within friendship-service to trigger WebSocket broadcasts when friendship events occur
- **Extend WebSocket message types** to include friendship-specific notifications like `FRIEND_REQUEST_RECEIVED`, `FRIEND_REQUEST_ACCEPTED`, `FRIEND_STATUS_CHANGED`

## Capabilities

### New Capabilities
- `friendship-request-realtime-notification`: Real-time delivery of friend request notifications (sent/received/accepted/declined) via WebSocket to affected users
- `friendship-status-realtime-sync`: Real-time synchronization of friendship status changes across all connected clients showing the user
- `friendship-unread-badge-display`: UI badge displaying unread friend request count that updates in realtime

### Modified Capabilities
- `notification-realtime-sync`: Extend to include friendship request notifications alongside existing notification types

## Impact

**Backend (Java/Spring)**:
- `chatappBE/friendship-service/`: Add WebSocket config, event broadcaster, Kafka consumer
- `chatappBE/common/common-websocket/`: Already provides base classes (no changes needed)
- Dependencies: Add `spring-boot-starter-websocket` to friendship-service build.gradle

**Frontend (React/TypeScript)**:
- `chatappFE/src/components/`: Add friend request badge component
- `chatappFE/src/websocket/`: Register friendship event handlers
- `chatappFE/src/store/`: Add friendship notification state management
- `chatappFE/src/pages/`: Display friend requests with realtime updates

**Database**:
- No schema changes (uses existing friendship and friendship_request tables)

**APIs**:
- REST endpoints: No changes to existing endpoints
- WebSocket: New message types: `FRIEND_REQUEST_RECEIVED`, `FRIEND_REQUEST_ACCEPTED`, `FRIEND_STATUS_CHANGED`

**Event Flow**:
- Kafka: Friendship events produced → friendship-service consuming and broadcasting → WebSocket delivery to clients
