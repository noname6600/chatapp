# Realtime-Edge Architecture Plan

## 1. Current Realtime Ownership

### Service-Local Websocket Ingress (Temporary)
Each service exposes its own websocket endpoint through the gateway:

| Service | Route | Port | Session Management | Broadcast | Auth |
|---------|-------|------|-------------------|-----------|------|
| chat-service | `/ws/chat/**` | 8083 | ChatSessionRegistry | ChatRealtimeAdapter (Redis) | JwtHandshakeInterceptor per session |
| presence-service | `/ws/presence/**` | 8084 | PresenceSessionRegistry | PresenceRedisPublisher (Redis) | JwtHandshakeInterceptor per session |
| friendship-service | `/ws/friendship/**` | 8085 | FriendshipSessionRegistry | FriendshipWebSocketPublisher (Redis) | JwtHandshakeInterceptor per session |
| notification-service | `/ws/notifications/**` | 8086 | NotificationSessionRegistry | NotificationRedisRealtimeAdapter (Redis) | JwtHandshakeInterceptor per session |

### Event Sources
- **Kafka (Durable)**: chat.message.sent, friendship.request.*, account.created, chat.reaction.updated
- **Redis Pub/Sub (Ephemeral)**: Typing indicators, presence status changes, room activity
- **Direct Application Publication**: Domain services publish via RealtimePort adapters

### Current Auth & Session Pattern
```
Client connects -> Service-local handshake (query param JWT)
  -> JwtHandshakeInterceptor.resolveUserId(token)
  -> Create session, store in service-local registry
  -> Return WsPrincipal(userId)
  -> Map session -> userId bidirectionally
  -> Broadcast routed by userId or roomId lookup in registry
```

### Current Realtime Responsibilities (Scattered)
- **Session lifecycle**: register on connect, unregister on close
- **Auth**: decode JWT, extract userId, validate token
- **Subscriptions**: implicit (chat service = all room messages you can read)
- **Broadcast**: lookup sessions by userId/roomId, send to all
- **Delivery semantics**: handled per-service via RealtimeFlowClassificationPolicy (now being removed)
- **Dedupe**: event-level only (NotificationEventDedupeGuard, etc.)
- **Error recovery**: service handles dead sessions, websocket close on error

---

## 2. Problems With Current Transitional Model

### Architectural Fragmentation
1. **Four separate websocket implementations**: duplicate connection handling, error handling, reconnection logic.
2. **No unified auth**: each service independently validates JWT, no token refresh strategy, no centralized revocation.
3. **No unified session management**: 4 separate registries, each with its own lock/concurrency strategy.
4. **Scattered subscription logic**: implicit subscriptions based on service domain (chat service = all your room messages).
5. **No centralized monitoring/observability**: difficult to trace a message from event → edge → client.

### Delivery & Reliability Issues
1. **RealtimeFlowClassificationPolicy removed but not migrated**: services currently have flow-type intent but no enforcement at edge.
2. **Dedupe logic at application layer only**: doesn't prevent client-level duplicates on reconnect.
3. **No unified backpressure/rate limiting**: each service independently handles slow clients.
4. **No recovery semantics**: client reconnect forces re-subscribe, no replay buffer.

### Operational Issues
1. **Hard to add new realtime flows**: requires service code change + websocket handler + session registry + broadcaster.
2. **Difficult to scale**: each service owns its sessions, no way to share load across instances.
3. **No unified testing**: hard to write integration tests that exercise full realtime path.
4. **Difficult migration from websocket to other protocols** (gRPC streaming, etc.): would need to refactor each service.

### Security Issues
1. **Auth on every service-local connection**: n × validation logic, n × JWT decode overhead.
2. **No centralized token invalidation**: if a token is revoked, all 4 services need to handle it independently.
3. **No rate limiting at ingress**: clients can hammer any service's websocket.
4. **Session fixation risk**: each service independently validates session → userId mapping.

### Performance Issues
1. **Redis broadcast for every realtime message**: even ephemeral-only events go through Redis.
2. **No message buffering/batching**: individual messages sent immediately.
3. **Session registry lookup overhead**: O(n) for broadcast to all users in a room (must scan registry).

---

## 3. Target Realtime-Edge Service Responsibilities

### Overview
A dedicated **realtime-edge** service (or edge component on gateway) owns all websocket ingress, client connection lifecycle, session management, subscription routing, and event delivery to connected clients. Business services remain stateless and only publish events.

### Specific Responsibilities

#### A. Client Connection & Auth Lifecycle
- **Single websocket ingress**: accept connections at `wss://gateway/realtime` or `wss://realtime-edge/ws`
- **Token validation**: decode JWT once at handshake, extract userId + scopes
- **Session lifecycle management**:
  - Create session(userId, scopes, connectionId)
  - Track connection state (connected, reconnecting, closed)
  - Maintain global session registry (in-memory or shared cache for multi-instance)
  - Clean up on disconnect
- **Auth enforcement**: prevent a session from subscribing to channels outside its scope (e.g., user:otherUserId if not authorized)
- **Token refresh**: support in-flight token refresh so clients don't lose connection on expiry

#### B. Subscription Management
- **Explicit subscription model**: clients send subscription commands on connect (not implicit)
  - `subscribe` message: `{ type: "subscribe", channels: ["room:123", "user:me", "presence:123"] }`
  - `unsubscribe` message: `{ type: "unsubscribe", channels: [...] }`
- **Authorization checks**: for each subscription request, validate the session is authorized
  - Can subscribe to `room:X` if user has read access to room X
  - Can subscribe to `user:me` but not `user:otherUserIdTheyDontOwn`
- **Subscription registry**: map session → set of subscribed channels
- **Channel types**:
  - `room:{roomId}` - room-specific messages, reactions, member list updates
  - `user:{userId}` - user-specific notifications, friend requests, direct messages
  - `presence:{roomId}` - aggregated online status for room members
  - `notification:{userId}` - notification center events
  - `typing:{roomId}` - typing indicator broadcasts (ephemeral)

#### C. Event Consumption & Routing
- **Consume from event sources**:
  - Kafka topics: chat.message.sent, friendship.request.*, notification.*, account.created, chat.reaction.updated, etc.
  - Redis pub/sub: ephemeral flows (typing, presence status)
- **Event routing logic**:
  - Extract event metadata: eventType, userId, roomId, targetUsers, etc.
  - Map event to target channels (e.g., chat.message.sent → route to room:X subscribers)
  - Look up sessions subscribed to those channels
  - Enrich event with client-facing payload
  - Send to all matching sessions
- **Flow-based delivery enforcement**:
  - Durable-first: always delivered to clients; reconnect gets message from buffer
  - Ephemeral-only: best-effort; if not connected, message is dropped
  - Mixed-with-convergence: delivered + convergence fetch endpoint for clients to catch up

#### D. Delivery & Reliability
- **Message dedupe**: track (eventId, sessionId) → delivered timestamp, drop duplicates within TTL
- **Message buffering** (for durable-first flows):
  - Maintain circular buffer per channel (or per user for user-specific channels)
  - On client reconnect, replay buffered messages
- **Backpressure handling**: if client is slow, queue messages briefly; if queue grows too large, close connection
- **Error handling**:
  - Failed sends logged but don't block other clients
  - Malformed events skipped with logging
  - Network errors on client handled gracefully

#### E. Monitoring & Observability
- **Metrics**:
  - `realtime.connections.active` - current connected sessions
  - `realtime.events.received` - events consumed from sources per channel
  - `realtime.events.delivered` - events sent to clients per channel
  - `realtime.subscriptions.active` - total active subscriptions
  - `realtime.delivery.latency_ms` - p50, p99 latency from event source → client delivery
- **Logging**: track connection lifecycle, subscription changes, delivery failures
- **Tracing**: correlate eventId through consumption → routing → delivery

---

## 4. Business Service Responsibilities After Cutover

### Unchanged (REST Endpoints)
- All command endpoints remain REST (POST /api/v1/rooms, PUT /api/v1/messages/{id}, etc.)
- All query endpoints remain REST
- Auth enforcement via Spring Security (X-User-Id header set by gateway JwtAuthFilter)

### Changed (Event Publishing Only)
- **Services stop owning websocket endpoints**: remove service-local websocket handlers, session registries, broadcasters
- **Services publish to event streams**: when a command completes, publish event to Kafka/Redis (unchanged flow)
- **Event structure**: events already wrapped in EventEnvelope with metadata; no service-level format change needed
- **No service-level delivery guarantees**: services publish the event; edge is responsible for delivery to clients
- **No session management**: services don't know which sessions are connected; they only publish to event stream

### Service Event Publishers (After Cutover)
| Service | Events Published | Topic/Channel | Type |
|---------|------------------|---------------|------|
| chat-service | chat.message.sent, chat.message.deleted, chat.reaction.updated, chat.room.created, chat.typing (maybe ephemeral to Redis) | Kafka + Redis | Durable + Ephemeral |
| presence-service | presence.user_status_changed, presence.room_online_users | Redis (ephemeral) or Kafka (if need replay) | Ephemeral |
| notification-service | notification.new, notification.dismissed, unread_count_updated | Redis or direct via Kafka | Depends on flow |
| friendship-service | friendship.request.sent, friendship.request.accepted, friendship.status_changed | Kafka | Durable |
| user-service | user.account_created, user.profile_updated | Kafka | Durable |

### Service Application Layers (No Change)
- Application services continue to orchestrate commands, call repositories, publish events
- Domain services remain stateless
- Event producers remain as adapters between application and Kafka/Redis

---

## 5. Event Inputs To Edge

### From Kafka (Durable Events)
The edge subscribes to:
- `chat.message.sent` → route to `room:{roomId}`
- `chat.message.deleted` → route to `room:{roomId}`
- `chat.reaction.updated` → route to `room:{roomId}`
- `chat.room.created` → route to `user:{userId}` (for room owner/members if notified)
- `friendship.request.sent` → route to `user:{recipientId}`
- `friendship.request.accepted` → route to both `user:{senderId}` and `user:{recipientId}`
- `friendship.request.declined` → route to `user:{senderId}`
- `notification.*` (new, dismissed, etc.) → route to `user:{userId}`
- `account.created` → route to `user:{userId}` (welcome notification)

### From Redis Pub/Sub (Ephemeral Events)
- `chat.typing:{roomId}` → route to `typing:{roomId}`
- `presence.user_status:{userId}` → route to `presence:{roomId}` for all rooms where user is member
- `presence.room_online_users:{roomId}` → route to `presence:{roomId}`

### Direct from Edge (Client Commands)
Some events may originate from client commands received on websocket:
- `typing.start` → publish to Redis `chat.typing:{roomId}` for other clients in room
- `user.presence.update` → publish to Redis for presence watchers
- These are ephemeral and don't go through Kafka

---

## 6. Client Channel / Subscription Model

### Channel Types & Authorization Rules

#### Room Channel: `room:{roomId}`
- **Payload**: messages, reactions, member updates, typing indicators
- **Authorization**: user must have read access to room (checked at subscribe time)
- **Delivery**: durable-first for messages, ephemeral for typing
- **Client Commands**:
  - Subscribe on room open/join
  - Unsubscribe on room close/leave

#### User Channel: `user:{userId}`
- **Payload**: notifications (new notification, dismissed), friend requests, unread counts, direct messages
- **Authorization**: only userId itself can subscribe, or admin/system can subscribe to any user
- **Delivery**: durable-first for notifications, mixed-with-convergence for unread counts
- **Client Commands**:
  - Subscribe on login (to `user:me`)
  - Implicit; clients don't unsubscribe (until logout)

#### Presence Channel: `presence:{roomId}`
- **Payload**: user online/offline status within room, aggregated active users
- **Authorization**: user must have access to room
- **Delivery**: ephemeral-only (clients don't need historical presence)
- **Client Commands**:
  - Subscribe when joining room
  - Unsubscribe on leave

#### Typing Channel: `typing:{roomId}`
- **Payload**: user started typing, stopped typing
- **Authorization**: user must have access to room
- **Delivery**: ephemeral-only (typing events not persisted)
- **Client Commands**:
  - Subscribe when in room
  - Publish typing.start/typing.stop while editing

#### Notification Channel: `notification:{userId}`
- **Payload**: new notifications, unread count updates
- **Authorization**: only userId itself
- **Delivery**: durable-first (clients want to see all notifications)
- **Client Commands**:
  - Subscribe on login
  - Unsubscribe on logout

### Subscription Request Format (Proposed)
```json
{
  "type": "subscribe",
  "channels": ["room:room-uuid", "user:me", "presence:room-uuid", "notification:me"],
  "requestId": "req-123" // optional, for request tracking
}
```

### Subscription Response Format
```json
{
  "type": "subscribe_ack",
  "requestId": "req-123",
  "channels": ["room:room-uuid", "user:me", "presence:room-uuid"],
  "failed": ["notification:other-user"], // auth denied
  "message": "Subscribed to 3 channels, 1 denied"
}
```

### Event Delivery Format (Proposed)
```json
{
  "type": "event",
  "eventType": "chat.message.sent",
  "channel": "room:room-uuid",
  "eventId": "event-uuid",
  "timestamp": "2026-01-15T10:30:45Z",
  "payload": {
    "messageId": "msg-uuid",
    "content": "hello",
    "senderId": "user-uuid",
    "createdAt": "2026-01-15T10:30:45Z"
  }
}
```

---

## 7. Migration Order

### Phase 1: Build Realtime-Edge Foundation (Weeks 1-2)
- [ ] Create `realtime-edge` service or extend gateway with edge component
- [ ] Implement websocket ingress, auth/session management
- [ ] Implement subscription registry + channel authorization
- [ ] Set up event consumption from Kafka (initial: just one topic for validation)
- [ ] Implement routing engine (event → channels → sessions)
- [ ] Add observability: metrics, logging, tracing
- [ ] Integration tests: auth, subscribe/unsubscribe, event delivery

### Phase 2: Shadow Mode Deployment (Weeks 3-4)
- [ ] Deploy realtime-edge alongside services (not in front of them yet)
- [ ] Services still own websocket endpoints
- [ ] Services publish events to BOTH old adapters AND edge (event duplication)
- [ ] Clients can optionally connect to edge (via a separate gateway route or alpha domain)
- [ ] Validate edge receives events, routes correctly, delivers to test clients
- [ ] Monitor for discrepancies between old and new paths

### Phase 3: Route Cutover (Week 5)
- [ ] Update gateway: route `/ws/*` to realtime-edge instead of services
- [ ] Services no longer receive websocket traffic
- [ ] Services continue to publish events (both old and new consumers still live)
- [ ] Monitor client connections, error rates, delivery latency
- [ ] If issues, rollback to phase 2

### Phase 4: Service Cleanup (Week 6)
- [ ] Remove service-local websocket handlers (chat, presence, friendship, notification)
- [ ] Remove service-local session registries
- [ ] Remove RealtimePort implementations and RealtimeFlowClassificationPolicy adapters
- [ ] Remove deprecated websocket classes from common-websocket
- [ ] Event producers remain (services still publish to Kafka/Redis)

### Phase 5: Post-Cutover Stabilization (Ongoing)
- [ ] Optimize edge performance (session registry, event routing)
- [ ] Add replay/buffering for durable flows
- [ ] Implement client-side convergence fetch endpoints (service-level query APIs for missed events)
- [ ] Migrate token refresh logic to edge if needed

---

## 8. Risks

### Technical Risks

#### R1: Single Point of Failure
- **Risk**: if realtime-edge goes down, all realtime features fail
- **Mitigation**: deploy edge as multi-instance with load balancing; session affinity not required (edge is stateless except for in-memory subscriptions; re-connect is acceptable)
- **Impact**: high; must design for horizontal scaling from day 1

#### R2: Event Loss During Migration
- **Risk**: events published during shadow mode might not reach clients on new edge
- **Mitigation**: shadow mode validates both paths; dual-publish ensures no loss; rollback available
- **Impact**: medium; can be caught in shadow phase

#### R3: Auth Token Validation Performance
- **Risk**: all websocket auth goes through edge; JWT validation at scale could become bottleneck
- **Mitigation**: cache token validation results with short TTL; use asymmetric keys for quick validation
- **Impact**: medium; can be optimized post-deployment

#### R4: Kafka Consumer Lag
- **Risk**: if edge falls behind consuming Kafka topics, events are delayed to clients
- **Mitigation**: monitor consumer lag; scale edge horizontally; use consumer group rebalancing
- **Impact**: medium; manageable with monitoring + alerting

#### R5: In-Memory Subscription Registry Loss on Restart
- **Risk**: if edge service restarts, all subscriptions lost; clients forced to reconnect
- **Mitigation**: edge is stateless; client reconnection is acceptable; subscriptions can optionally be persisted to Redis for faster reconnect (future optimization)
- **Impact**: low; expected behavior during service restart

### Operational Risks

#### R6: Increased Infrastructure Complexity
- **Risk**: new service to operate, monitor, scale
- **Mitigation**: use standard Spring Boot patterns; integrate with existing ops tooling; automate deployment
- **Impact**: low; standard Spring Boot service

#### R7: Migration Regression
- **Risk**: bugs in new edge reveal themselves only at scale
- **Mitigation**: extensive testing in shadow phase; gradual rollout; canary deployment; feature flags for easy rollback
- **Impact**: medium; manageable with staged approach

### Organizational Risks

#### R8: Team Ownership
- **Risk**: edge becomes bottleneck for feature delivery (all realtime features require edge changes)
- **Mitigation**: design edge to be minimal; keep business logic in services; edge is only a router/delivery layer
- **Impact**: high; must establish clear ownership and SLAs

#### R9: Knowledge Silos
- **Risk**: only one team understands realtime-edge internals
- **Mitigation**: thorough documentation, code reviews, cross-training
- **Impact**: medium; mitigated with good practices

---

## 9. Final Recommendation

### Verdict: YES — Realtime-Edge Architecture Should Be Pursued

**Rationale**:
1. **Current model is unsustainable**: 4 independent websocket implementations violate DRY principle, create maintenance burden, and prevent scaling
2. **Clean separation of concerns**: edge owns realtime delivery; services own business logic
3. **Path to future protocols**: once edge is built, migrating to gRPC streaming, Server-Sent Events, or other protocols is isolated to edge
4. **Better observability**: centralized logging, metrics, tracing for all realtime traffic
5. **Easier to test**: edge can be integration-tested independently from business services
6. **Migration is low-risk**: shadow mode validation + staged rollout provides safety net
7. **Unblocks scalability**: multi-instance edge, event-driven architecture is cloud-native friendly

### Recommended Next Steps
1. **Design phase**: detailed event contracts, channel authorization rules, client reconnection behavior
2. **Prototype phase** (Week 1-2): build MVP edge service with auth + single topic consumption
3. **Shadow phase** (Week 3-4): validate edge in parallel with current system
4. **Cutover phase** (Week 5): route traffic to edge
5. **Cleanup phase** (Week 6): remove service-local websocket code
6. **Hardening phase**: add replay, monitoring, optimize for scale

### Key Success Criteria
- [ ] Zero message loss during migration
- [ ] Edge latency < 100ms p99 from Kafka → client delivery
- [ ] Support 10k+ concurrent connections per edge instance
- [ ] Deployable in multi-region without session affinity
- [ ] Clear audit trail of who subscribed to what, when
- [ ] 99.9% uptime SLA for edge service

### Out-of-Scope Refinements (Post-Cutover)
- Client-side convergence fetch for missed durable events
- Replay buffer for new subscribers catching up on historical events
- Rate limiting per-user or per-subscription
- Token refresh without reconnect
- Connection migration between edge instances (gRPC-based)
- Support for third-party webhook delivery (if needed for integrations)
