# 08. WebSocket Realtime Flow

## 1) Realtime Edge Position In Architecture
The websocket gateway is centralized in realtime-edge-service.

Responsibilities:
- ticket-based handshake auth
- session registration and lease management
- channel subscription authorization
- command dispatch to domain services
- delivery of Redis/Kafka-derived events to websocket clients

## 2) Handshake And Ticket Lifecycle

### Ticket issuance
Endpoint: RealtimeTicketController POST /api/v1/realtime/ticket
- extracts userId from validated Jwt principal
- stores ws:ticket:{ticket} = userId:accessToken, TTL 30s

### Handshake interceptor
JwtHandshakeInterceptor:
1. reads ticket query param
2. loads ws:ticket key
3. parses userId + accessToken
4. deletes ticket (single-use)
5. creates ws:session-token:{tokenRef} -> accessToken TTL 15m
6. sets userId + accessTokenRef in websocket session attributes

## 3) Connection Establishment
RealtimeWebSocketHandler.afterConnectionEstablished:
1. reads userId from attributes
2. creates RealtimeSession
3. registers session in RealtimeSessionRegistry
4. registers websocket session object in RealtimeWebSocketSessionStore
5. auto-subscribes channels:
   - notification:{userId}
   - friendship:{userId}
   - presence:global
6. checks active token ref
7. submits presence.connect side effect through RealtimeSideEffectQueue

## 4) Session Registry Modes
Configured via realtime.session-registry.mode.

### in-memory mode
- ConcurrentHashMap based local registry
- good for single instance/dev

### redis mode
- RedisRealtimeSessionRegistry stores distributed ownership and subscriptions
- lease TTL default 90s
- supports cross-instance ownership queries and cleanup

Maintenance job:
- EdgeSessionMaintenanceJob runs scheduled cleanup of stale sessions and orphan indexes

## 5) Message Ingress Routing
RealtimeWebSocketHandler.handleTextMessage:
- validates access token ref exists
- routes presence.* messages to presence handlers
- routes chat command types JOIN/LEAVE/SEND/EDIT/DELETE/REACTION/PIN/UNPIN
- fallback to generic command dispatcher for subscribe/unsubscribe/ping

## 6) Channel Authorization
ChannelSubscriptionManager validates channel type and ownership.

Rules:
- user/notification channels: self-only
- room/presence/typing channels: requires room membership check through IChatCommandRouter.canAccessRoom(accessToken,roomId)
- authorization cache key room:access:{userId}:{roomId}, TTL 30s

Invalidation:
- realtime-edge RedisEventListener invalidates room access cache on room membership event types.

## 7) Presence Bridge
EdgePresenceLifecycleBridge coordinates lifecycle with presence-service through PresenceDomainClient.

Connect path:
- call /api/v1/presence/ws/connect
- fetch global snapshot and send presence.global.snapshot frame to client

Disconnect path:
- checks if user has other presence:global sessions
- only sends disconnect when last session closes

## 8) Redis-Driven Outbound Delivery
Realtime-edge consumes Redis pub/sub and dispatches:
- ChatRealtimeDeliveryService for room events
- NotificationRealtimeDeliveryService for user notification events
- PresenceRealtimeDeliveryService for presence events

All delivery services push via WebSocketOutboundDeliveryQueue.

## 9) Outbound Queue Concurrency Model
WebSocketOutboundDeliveryQueue:
- fixed worker thread pool (configurable)
- per-session bounded queue (default capacity 500)
- enqueue returns false and increments drop metric when full
- drains serially per session using AtomicBoolean gate

This prevents concurrent writes to same WebSocketSession and isolates slow clients.

## 10) Friendship Cross-Instance Special Path
FriendshipRealtimeDeliveryService:
- split sessions by ownership (local vs remote) using EdgeCrossInstanceDispatchCoordinator
- local sends direct
- remote sends via EdgeDeliveryHandoffPublisher to Redis channel realtime.edge.handoff.{instanceId}
- target instance receives via EdgeDeliveryHandoffListener and delivers locally

Why special handling exists:
- friendship source is Kafka consume path in edge, not redis fanout path, so explicit cross-instance coordination is needed.

## 11) Horizontal Scaling Semantics
With 5 websocket edge servers:
- chat/presence/notification redis events are broadcast to all edge nodes
- each node sends only to sessions it owns
- no sticky sessions required for redis fanout correctness

For friendship Kafka path:
- consumer runs on one edge instance per partition assignment
- handoff pub/sub sends to remote owner instances for user sessions

## 12) Runtime Guarantees And Limits
Guaranteed best-effort properties:
- authenticated websocket session establishment
- per-session ordered send queue processing
- local ownership-aware fanout

Not guaranteed:
- global exactly-once delivery
- strict cross-instance ordering
- lossless delivery under redis outages or disconnected clients

## 13) Reconnect Behavior
If token ref expired or missing:
- handler sends TOKEN_EXPIRED error
- closes socket
- client must request new ticket and reconnect

If server instance dies:
- local ws sessions drop
- clients reconnect to available edge node
- redis-mode cleanup eventually removes stale session indices
