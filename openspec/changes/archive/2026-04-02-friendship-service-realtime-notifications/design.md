## Context

The friendship-service is a Spring Boot microservice that manages friend relationships and produces Kafka events when friendship state changes (requests sent/accepted/declined, unfriending, blocking). Currently, it publishes these events but has no mechanism to push updates in realtime to connected clients. The notification-service consumes friendship events to create notification records, but users must manually refresh to see updated friend request badges or friendship status changes in the UI.

The architecture includes:
- **Backend**: Multiple Spring Boot services communicating via Kafka and Feign clients
- **Frontend**: React app connected via WebSocket for realtime messaging and status
- **Common**: Shared library (`common-websocket`) providing base WebSocket functionality (`IUserBroadcaster`, `WsOutgoingMessage`, `AbstractJwtHandshakeInterceptor`)
- **Event Flow**: Services publish domain events → Kafka topic → consuming services listen and act

The friendship-service must integrate WebSocket broadcasting to make realtime friendship updates possible, similar to how chat-service broadcasts typing indicators and how notification-service pushes notifications.

## Goals / Non-Goals

**Goals:**
- Enable realtime friend request notifications to appear on recipient's device without page refresh
- Display friend status changes (acceptance/decline) immediately to requester
- Create a badge system showing unread friend request count that updates in realtime
- Maintain event-driven architecture—Kafka remains the source of truth for persistence
- Reuse existing `common-websocket` patterns for consistency with other services
- Support JWT-authenticated WebSocket connections
- Allow friendship events to broadcast to both affected users simultaneously

**Non-Goals:**
- Changing REST API contracts for friend endpoints (only adding WebSocket events)
- Persisting WebSocket session state in database (sessions are ephemeral)
- Creating a separate friendship notification database table (use existing notification-service table)
- Implementing friend suggestion algorithms or discovery features
- Modifying friendship schema or database structure
- Real-time presence indicators for "friend online status" (separate capability)

## Decisions

### 1. **Kafka Consumption Within Friendship-Service** → Realtime Broadcasting Source
**Decision**: Friendship-service will consume its own Kafka events (`friendship.events` and `friendship.request.events` topics) to trigger WebSocket broadcasts.

**Rationale**: Instead of relying solely on notification-service to handle realtime delivery, the friendship-service owns the broadcasting responsibility for its domain. This keeps friendship domain logic cohesive and allows immediate broadcasts without waiting for notification-service to process events.

**Alternatives Considered**:
- *Event delegation to notification-service*: Adds latency and coupling; notification-service already has many responsibilities.
- *Push from chat-service on friend acceptance*: Creates cross-service coupling; not clean separation of concerns.

**Implementation**: Create `FriendshipEventConsumer` listener on `friendship.events` and `friendship.request.events` topics using `@KafkaListener`.

---

### 2. **WebSocket Message Types for Friendship** → Distinct Event Types
**Decision**: Use specific WebSocket message types for friendship events: `FRIEND_REQUEST_RECEIVED`, `FRIEND_REQUEST_ACCEPTED`, `FRIEND_REQUEST_DECLINED`, and `FRIEND_STATUS_CHANGED`.

**Rationale**: Clear separation from other WebSocket events (chat messages, notifications). Allows frontend to route friendship updates to appropriate UI handlers. Uses `WsOutgoingMessage` wrapper from `common-websocket`.

**Alternatives Considered**:
- *Generic `NOTIFICATION` type*: Would blur domain boundaries; harder to type-check on frontend.
- *Storing friendship notifications in notification table*: Added complexity; friendship domain is simpler as realtime-only.

**Implementation**: Extend `WsOutgoingMessage` with friendship-specific payloads (requestId, senderId, recipientId, actionUserId, newStatus).

---

### 3. **Broadcasting to Both Affected Users** → Dual Recipient Pattern
**Decision**: When a friendship event occurs, broadcast to both affected users (requester and recipient), not just the primary actor.

**Rationale**: Provides complete UI consistency. Both users should see updated friend status immediately. Example: when user A accepts user B's request, both A and B should see the relationship status change.

**Alternatives Considered**:
- *Only notify the recipient of actions*: User A wouldn't see their own request status change when B accepts; inconsistent UX.
- *Broadcast to all connected users*: Wasteful; only relevant users need updates.

**Implementation**: In `IUserBroadcaster.sendToUser(userId, message)`, call for both `userLow` and `userHigh` IDs from friendship entity.

---

### 4. **Shared WebSocket Endpoint vs. Service-Specific** → Shared Endpoint
**Decision**: Reuse the shared `/ws` WebSocket endpoint established by other services; friendship events are just additional message types.

**Rationale**: Avoids connection fragmentation. Frontend maintains single WebSocket connection to receive messages from all services. Simplifies client-side connection management.

**Alternatives Considered**:
- *Dedicated `/ws-friendship` endpoint*: Creates multiple connections; more client complexity.

**Implementation**: Friendship-service will connect to shared WebSocket broker through `common-websocket` interfaces. No separate endpoint configuration needed.

---

### 5. **Unread Friend Request Badge State Management** → Frontend-Driven with Backend Seed
**Decision**: Frontend maintains unread count in Redux/store initialized from a REST endpoint on login; WebSocket events update it in realtime.

**Rationale**: Puts badge state where UI lives, avoids synchronization issues. REST endpoint provides initial state; WebSocket provides deltas for realtime updates.

**Alternatives Considered**:
- *Pure backend-driven badge*: Adds unnecessary backend complexity; badge is UI state.
- *Persistent badge state in database*: Overcomplicates; unread is ephemeral per user.

**Implementation**: 
- Add GET `/api/friends/unread-count` endpoint in friendship-service
- Frontend calls on app load to initialize badge count
- WebSocket listener updates count when `FRIEND_REQUEST_RECEIVED`, `FRIEND_REQUEST_ACCEPTED` events arrive

---

## Risks / Trade-offs

| Risk | Mitigation |
|------|-----------|
| **Duplicate Events**: Kafka consumer listening to service's own events could cause lag loops | Only friendship-service consumes; events are idempotent. Add event deduplication if needed later. |
| **WebSocket Session Loss**: User closes browser/disconnects—unseen messages lost | By design (realtime-only). For persistence, users can check `/api/friend-requests` on reconnect. |
| **Scale**: Many simultaneous friendship events under high load could saturate WebSocket connections | `common-websocket` uses async broadcasting. Monitor WebSocket session count. Scale horizontally as needed. |
| **Database Consistency**: Rapid friendship state changes before event consumed | Kafka ensures ordering per partition (keyed by friendship ID). Transactional writes prevent state corruption. |
| **Client-Side Complexity**: Frontend must handle multiple event types | Use clear Redux action types and event handlers per message type. Frontend specs will define this. |
| **Backward Compatibility**: Existing clients not expecting friendship WebSocket events | Non-breaking; clients ignore unknown message types. Graceful degradation (manual refresh still works). |

---

## Migration Plan

**Deployment Order**:
1. **Phase 1**: Deploy friendship-service changes (WebSocket config + Kafka consumer) — existing functionality unaffected
2. **Phase 2**: Deploy frontend changes (WebSocket listeners + badge component) — assumes backend is ready
3. **Rollback**: Remove consumers and WebSocket config; users fall back to manual refresh

**Data Migration**: None (no schema changes)

**Canary Testing**: Deploy to staging environment; test friend request flow end-to-end with multiple clients connected via WebSocket
